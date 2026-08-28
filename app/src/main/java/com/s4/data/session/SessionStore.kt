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

import android.content.SharedPreferences
import androidx.core.content.edit
import com.s4.data.repository.ProtectedPrefsStore
import com.s4.model.StampingSession

/**
 * Persists [StampingSession]s (the metal-stamping shares) Keystore-encrypted
 * in SharedPreferences, one record per session under a short, human-written
 * code the user copies onto paper.
 *
 * Each session blob is protected by [ProtectedPrefsStore] (AES-GCM under the
 * Android Keystore, HMAC-bound to the pref key) — the same protection the PIN
 * verifier gets. A registry key tracks live codes so saved sessions can be
 * listed and wiped from Settings.
 *
 * Sessions expire after [EXPIRE_AFTER_MILLIS] (hardcoded to one week): an
 * expired session is never returned and is swept from disk on the next
 * [load]/[codes]. [save]/[load] fail closed — a session is only ever returned
 * fully decrypted, structurally valid, and unexpired, and a failed write
 * persists nothing.
 */
internal class SessionStore(
    private val prefs: SharedPreferences,
    private val store: ProtectedPrefsStore,
    private val expireAfterMillis: Long = EXPIRE_AFTER_MILLIS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        /** One week — the longest a stamping session may live on disk. */
        const val EXPIRE_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000L

        private const val KEY_PREFIX = "stamp_session_"
        private const val KEY_REGISTRY = "stamp_session_codes"
        private const val REGISTRY_SEPARATOR = ","
        private const val MAX_COLLISION_TRIES = 64
    }

    /** Saves [session] under a fresh code and returns it, or null on failure. */
    fun save(session: StampingSession): String? {
        val code = availableCode() ?: return null
        val record = SessionCodec.encode(session)
        if (!store.protectedPutString(sessionKey(code), record)) return null
        if (!registryPutAll(registryCodes() + code)) {
            // A failed registry write must not leave an orphaned session blob
            // on disk: roll back so "a failed save persists nothing".
            prefs.edit { remove(sessionKey(code)) }
            return null
        }
        return code
    }

    /**
     * Loads the session for [code], or null when the code is unknown, the
     * record is malformed/unreadable (fail closed), or the session has
     * expired (in which case it is also swept from disk).
     */
    fun load(code: String): StampingSession? {
        if (!SessionCodeGenerator.isValidCode(code)) return null
        val record = store.readProtectedValueOrNull(sessionKey(code)) ?: return null
        val session = SessionCodec.decode(record) ?: return null
        if (isExpired(session)) {
            delete(code)
            return null
        }
        return session
    }

    /** True when a record (readable or not) exists under [code]. */
    fun exists(code: String): Boolean {
        if (!SessionCodeGenerator.isValidCode(code)) return false
        return prefs.contains(sessionKey(code))
    }

    /** Deletes the session under [code]. Returns false when no record existed. */
    fun delete(code: String): Boolean {
        if (!SessionCodeGenerator.isValidCode(code)) return false
        if (!prefs.contains(sessionKey(code))) return false
        val remaining = registryCodes() - code
        prefs.edit { remove(sessionKey(code)) }
        return registryPutAll(remaining)
    }

    /**
     * All live, unexpired session codes. Expired sessions are swept from disk
     * in the process; unreadable/corrupt records are not listed.
     */
    fun codes(): List<String> {
        val live = mutableListOf<String>()
        for (code in registryCodes()) {
            if (load(code) != null) live.add(code)
        }
        return live
    }

    /**
     * Physically deletes every session that is verifiably past its expiry.
     * A session is only removed once it decrypts and is confirmed expired, so
     * this is safe to run from background alarms and after a device-clock change.
     */
    fun sweepExpired() {
        for (code in registryCodes()) {
            load(code) // load() deletes the record when the session is expired
        }
    }

    private fun isExpired(session: StampingSession): Boolean =
        session.createdAt <= 0L || session.createdAt + expireAfterMillis < nowProvider()

    private fun availableCode(): String? {
        repeat(MAX_COLLISION_TRIES) {
            val candidate = SessionCodeGenerator.generate()
            if (!exists(candidate)) return candidate
        }
        return null
    }

    private fun registryCodes(): List<String> {
        val raw = prefs.getString(KEY_REGISTRY, null) ?: return emptyList()
        if (raw.startsWith(ProtectedPrefsStore.ENC_PREFIX)) {
            // Protected (normal) registry. Fail closed on corruption: an
            // unreadable protected registry is never silently replaced.
            val decrypted = store.readProtectedValueOrNull(KEY_REGISTRY) ?: return emptyList()
            return parseCodes(decrypted)
        }
        // Legacy plaintext registry — from an install predating protection, or
        // the bug where delete() wrote the index without encryption. The index
        // is not secret (codes are written on paper anyway), so parse it and
        // re-protect it so it stays tamper-evident going forward.
        val codes = parseCodes(raw)
        if (codes.isNotEmpty() || raw.isBlank()) registryPutAll(codes)
        return codes
    }

    private fun parseCodes(raw: String): List<String> {
        val codes = raw.split(REGISTRY_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        if (codes.any { !SessionCodeGenerator.isValidCode(it) }) return emptyList()
        return codes.distinct()
    }

    private fun registryPutAll(codes: List<String>): Boolean =
        store.protectedPutString(KEY_REGISTRY, codes.joinToString(REGISTRY_SEPARATOR))

    private fun sessionKey(code: String): String = KEY_PREFIX + code
}
