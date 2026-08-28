/*
 * Copyright (C) 2026 The S4 project contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.s4.data.session

import com.s4.data.crypto.JvmPrefsCrypto
import com.s4.data.crypto.PrefsCrypto
import com.s4.data.repository.ProtectedPrefsStore
import com.s4.model.StampingSession
import com.s4.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class SessionStoreTest {

    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var store: ProtectedPrefsStore
    private var now = 1_000L
    private lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        // The tamper flag is process-wide; each test must start with a clean
        // latch so a latched flag from one test never leaks into another.
        ProtectedPrefsStore.consumeProcessTamperFlag()
        prefs = InMemorySharedPreferences()
        store = ProtectedPrefsStore(prefs, JvmPrefsCrypto())
        now = 1_000L
        sessionStore = SessionStore(prefs, store, expireAfterMillis = 10_000L, nowProvider = { now })
    }

    private val session = StampingSession(
        threshold = 3,
        shareCount = 6,
        shares = listOf(
            "academic acid acne adjust again agency album alert alice all along already always " +
                "amber amount analysis analyst anatomy animal annual antenna anyway appeal apple " +
                "approve april apron area",
            "adjust again agency album alert alice all along already always amber amount analysis " +
                "analyst anatomy animal annual antenna anyway appeal apple approve april apron area " +
                "army aroma",
        ),
        entropyHex = "1a2b3c4d5e6f708192a3b4c5d6e7f890112233445566778899aabbccddeeff0011",
        seedWordCount = 24,
        fingerprint = "0123456789abcdef",
        passphraseUsed = false,
        passphraseLocation = "",
        createdAt = now,
    )

    // --- save / load round-trip ---

    @Test
    fun `save returns a valid code and load round-trips`() {
        val code = sessionStore.save(session)

        assertNotNull(code)
        assertTrue(SessionCodeGenerator.isValidCode(code!!))
        assertEquals(session, sessionStore.load(code))
    }

    @Test
    fun `a saved session is readable by a fresh store instance (process restart)`() {
        val code = sessionStore.save(session)!!

        val reloaded = SessionStore(prefs, ProtectedPrefsStore(prefs, JvmPrefsCrypto()), nowProvider = { now })

        assertEquals(session, reloaded.load(code))
    }

    @Test
    fun `multiple sessions coexist independently`() {
        val codeA = sessionStore.save(session)!!
        val codeB = sessionStore.save(session.copy(entropyHex = "fefe"))!!

        assertNotEquals(codeA, codeB)
        assertEquals(setOf(codeA, codeB), sessionStore.codes().toSet())
        assertEquals(session, sessionStore.load(codeA))
        assertEquals(session.copy(entropyHex = "fefe"), sessionStore.load(codeB))
    }

    @Test
    fun `exists is true after save and false for unknown codes`() {
        val code = sessionStore.save(session)!!

        assertTrue(sessionStore.exists(code))
        assertFalse(sessionStore.exists(SessionCodeGenerator.generate()))
        assertFalse(sessionStore.exists("not a code"))
    }

    @Test
    fun `load returns null for unknown and invalid codes`() {
        assertNull(sessionStore.load("ZZZZZZ"))
        assertNull(sessionStore.load(""))
        assertNull(sessionStore.load("abc"))
    }

    // --- delete ---

    @Test
    fun `delete removes the session and its registry entry`() {
        val code = sessionStore.save(session)!!

        assertTrue(sessionStore.delete(code))
        assertFalse(sessionStore.exists(code))
        assertNull(sessionStore.load(code))
        assertTrue(sessionStore.codes().isEmpty())
    }

    @Test
    fun `delete returns false when no record existed`() {
        assertFalse(sessionStore.delete("ZZZZZZ"))
    }

    @Test
    fun `deleting one of two sessions keeps the other and the registry protected`() {
        // Regression guard for the bug where delete() rewrote the registry as
        // plaintext, hiding every session in the list.
        val codeA = sessionStore.save(session)!!
        val codeB = sessionStore.save(session.copy(entropyHex = "fefe"))!!

        sessionStore.delete(codeA)

        assertEquals(listOf(codeB), sessionStore.codes())
        assertEquals(session.copy(entropyHex = "fefe"), sessionStore.load(codeB))
        assertNull(sessionStore.load(codeA))
        val registry = prefs.getString("stamp_session_codes", null)
        assertNotNull("registry must still be written", registry)
        assertTrue("registry must be re-protected after delete, was '$registry'", registry!!.startsWith("enc:"))
    }

    // --- failure modes ---

    @Test
    fun `save returns null and persists nothing when crypto is unavailable`() {
        val noCryptoStore = SessionStore(prefs, ProtectedPrefsStore(prefs, cryptoFactory = { null }), nowProvider = { now })

        val code = noCryptoStore.save(session)

        assertNull(code)
        assertTrue(prefs.all.keys.none { it.startsWith("stamp_session_") })
        assertTrue("no registry may be written", !prefs.contains("stamp_session_codes"))
        assertTrue(noCryptoStore.codes().isEmpty())
    }

    @Test
    fun `save rolls back the session blob when the registry write fails`() {
        // Regression guard: a failed registry write must not leave an orphaned
        // (unlisted, unwipeable) encrypted blob behind.
        val failsOnSecondEncrypt = FailsOnNthEncrypt(2)
        val store2 = SessionStore(prefs, ProtectedPrefsStore(prefs, failsOnSecondEncrypt), nowProvider = { now })

        val code = store2.save(session)

        assertNull("save must report failure", code)
        assertTrue("no session blob may remain on a failed save", prefs.all.keys.none { it.startsWith("stamp_session_") })
    }

    @Test
    fun `load fails closed on a corrupted session blob and latches the tamper flag`() {
        val code = sessionStore.save(session)!!
        val stored = prefs.getString("stamp_session_$code", null)!!
        val parts = stored.removePrefix("enc:").split(":")
        val cipher = Base64.getDecoder().decode(parts[1]).apply { this[0] = (this[0].toInt() xor 0x01).toByte() }
        prefs.edit()
            .putString("stamp_session_$code", "enc:" + parts[0] + ":" + Base64.getEncoder().encodeToString(cipher) + ":" + parts[2])
            .commit()

        assertNull("a corrupted blob must fail closed", sessionStore.load(code))
        assertTrue("a corrupted blob must set the tamper flag", store.consumeTamperFlag())
    }

    // --- expiry ---

    @Test
    fun `session is available up to the expiry boundary and gone just after`() {
        // createdAt = 1000, expireAfter = 10000 → dead when now > 11000.
        val code = sessionStore.save(session.copy(createdAt = now))!!

        now = 1_000L + 9_999L
        assertEquals("must be available before the deadline", session, sessionStore.load(code))

        now = 1_000L + 10_000L
        assertEquals("must still be usable at the exact deadline", session, sessionStore.load(code))

        now = 1_000L + 10_001L
        assertNull("must be expired once the deadline has passed", sessionStore.load(code))
        assertFalse("expired session must be swept from disk", prefs.contains("stamp_session_$code"))
    }

    @Test
    fun `codes excludes expired sessions and sweeps them`() {
        sessionStore.save(session.copy(createdAt = now))!! // expires when now > 11000
        val survivor = sessionStore.save(session.copy(entropyHex = "fefe", createdAt = now + 5_000))!!

        now = 1_000L + 12_000L

        assertEquals(listOf(survivor), sessionStore.codes())
    }

    @Test
    fun `sweepExpired removes only the expired sessions`() {
        val expiring = sessionStore.save(session.copy(createdAt = now))!! // expires when now > 11000
        val survivor = sessionStore.save(session.copy(entropyHex = "fefe", createdAt = now + 5_000))!!

        now = 1_000L + 12_000L
        sessionStore.sweepExpired()

        assertFalse(prefs.contains("stamp_session_$expiring"))
        assertTrue(prefs.contains("stamp_session_$survivor"))
        assertEquals(listOf(survivor), sessionStore.codes())
    }

    @Test
    fun `a non-positive createdAt is treated as expired`() {
        val code = sessionStore.save(session.copy(createdAt = 0L))!!

        assertNull(sessionStore.load(code))
    }

    // --- legacy plaintext registry recovery ---

    @Test
    fun `a legacy plaintext registry is parsed and re-protected`() {
        // Standing in for the delete() bug that once wrote the registry raw:
        // the index must still be readable and restored to protected form.
        val code = sessionStore.save(session)!!
        prefs.edit().putString("stamp_session_codes", code).commit()

        val reloaded = SessionStore(prefs, ProtectedPrefsStore(prefs, JvmPrefsCrypto()), nowProvider = { now })

        assertEquals(listOf(code), reloaded.codes())
        val registry = prefs.getString("stamp_session_codes", null)
        assertTrue("recovery must re-protect the registry, was '$registry'", registry!!.startsWith("enc:"))
    }

    @Test
    fun `a plaintext registry with garbage is not trusted`() {
        prefs.edit().putString("stamp_session_codes", "not-a-code").commit()

        assertTrue(sessionStore.codes().isEmpty())
    }

    /** A [PrefsCrypto] that throws on the nth encrypt call (1-based). */
    private class FailsOnNthEncrypt(private val failAt: Int) : PrefsCrypto {
        private val delegate = JvmPrefsCrypto()
        private var encrypts = 0
        override fun encrypt(data: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
            encrypts++
            if (encrypts == failAt) throw IllegalStateException("encrypt failed")
            return delegate.encrypt(data, aad)
        }
        override fun decrypt(ciphertext: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray =
            delegate.decrypt(ciphertext, iv, aad)
        override fun hmac(data: ByteArray): ByteArray = delegate.hmac(data)
    }
}