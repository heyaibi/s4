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

package com.s4.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.s4.bip39.Bip39
import com.s4.model.SplitParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device (instrumented) tests of the [Slip39] JNI boundary against the real
 * `libslip39_jni.so` built by CMake. Complements the JVM unit tests (which load
 * a host dylib) by verifying the exact Android native build: official trezor
 * vectors, BIP-39↔SLIP-39 round-trips, the pbkdf2/fingerprint path, and error
 * handling. Runs on an emulator/device via `connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class Slip39InstrumentedTest {

    private val vectors: List<TrezorVector> by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("slip39-vectors.tsv")) {
            "slip39-vectors.tsv resource missing"
        }
        stream.bufferedReader().readLines().map { line ->
            val parts = line.split('\t')
            TrezorVector(
                description = parts[0],
                mnemonics = parts[1].split(" | ").filter { it.isNotEmpty() },
                secretHex = parts[2],
            )
        }
    }

    @Test
    fun officialTrezorVectors_combineToExactSecret() {
        val valid = vectors.filter { it.secretHex.isNotEmpty() && !it.isExtendable }
        assertEquals(11, valid.size)
        for (v in valid) {
            val secret = Slip39.combine(v.mnemonics, "TREZOR")
            assertEquals("${v.description}", v.secretHex, secret.toHex())
        }
    }

    @Test
    fun officialTrezorVectors_invalidCasesError() {
        val invalid = vectors.filter { it.secretHex.isEmpty() && !it.isExtendable }
        assertEquals(30, invalid.size)
        for (v in invalid) {
            assertThrows("${v.description}", Slip39Exception::class.java) {
                Slip39.combine(v.mnemonics, "TREZOR")
            }
        }
    }

    @Test
    fun roundTrip_bip39Words_anyThresholdSubset() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val words = Bip39.entropyToMnemonic(entropy)
        val shares = Slip39.split(SplitParams(3, 6), entropy)

        assertEquals(6, shares.size)
        shares.forEach { assertEquals(33, it.split(' ').size) }

        for (subset in listOf(listOf(0, 1, 2), listOf(2, 4, 5), listOf(0, 3, 5))) {
            val recovered = Slip39.combine(subset.map { shares[it] })
            assertArrayEquals(entropy, recovered)
            assertEquals(words, Bip39.entropyToMnemonic(recovered))
        }
    }

    @Test
    fun roundTrip_1ofN() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(1, 5), entropy)
        assertEquals(5, shares.size)
        for (i in 0 until 5) {
            assertArrayEquals(entropy, Slip39.combine(listOf(shares[i])))
        }
    }

    @Test
    fun roundTrip_16of16() {
        val entropy = ByteArray(32) { (0xff - it).toByte() }
        val shares = Slip39.split(SplitParams(16, 16), entropy)
        assertEquals(16, shares.size)
        assertArrayEquals(entropy, Slip39.combine(shares))
    }

    @Test
    fun pbkdf2_matchesBip39SeedVector() {
        // Official BIP-39 vector, exercised through the vendored native pbkdf2.
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(' ')
        val seed = Bip39.deriveSeed(words, "TREZOR")
        assertEquals(
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            seed.toHex(),
        )
    }

    @Test
    fun fingerprint_stableAcrossSplitAndRestore() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val words = Bip39.entropyToMnemonic(entropy)
        val passphrase = "a separately preserved secret"
        val splitFp = Bip39.fingerprint(Bip39.deriveSeed(words, passphrase))

        val shares = Slip39.split(SplitParams(3, 6), entropy)
        val recoveredWords = Bip39.entropyToMnemonic(Slip39.combine(shares.take(3)))
        val restoreFp = Bip39.fingerprint(Bip39.deriveSeed(recoveredWords, passphrase))

        assertEquals(splitFp, restoreFp)
    }

    @Test
    fun tooFewShares_fails() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), entropy)
        val ex = assertThrows(Slip39Exception::class.java) {
            Slip39.combine(shares.take(2))
        }
        assertTrue("expected a negative native code, got ${ex.code}", ex.code < 0)
    }

    @Test
    fun corruptedWord_failsWithChecksumError() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), entropy)

        val words = shares[1].split(' ')
        val corrupted = words.dropLast(1) + Slip39Wordlist.words[(Slip39Wordlist.indexOf(words.last())!! + 1) % 1024]

        val ex = assertThrows(Slip39Exception::class.java) {
            Slip39.combine(listOf(shares[0], corrupted.joinToString(" "), shares[2]))
        }
        assertEquals(-2, ex.code) // ERROR_INVALID_MNEMONIC_CHECKSUM
    }

    @Test
    fun mismatchedIdentifiers_fails() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val setA = Slip39.split(SplitParams(3, 6), entropy)
        val setB = Slip39.split(SplitParams(3, 6), entropy)

        assertThrows(Slip39Exception::class.java) {
            Slip39.combine(listOf(setA[0], setA[1], setB[2]))
        }
    }

    @Test
    fun shares_containOnlySlip39Words_noHex() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), entropy)
        val hexish = Regex("^[0-9a-f]{2,}\$")
        for (share in shares) {
            for (word in share.split(' ')) {
                assertTrue("'$word' must be a SLIP-39 word", Slip39Wordlist.isValidWord(word))
                assertTrue("'$word' must not be hex", !hexish.matches(word))
            }
        }
    }

    private data class TrezorVector(
        val description: String,
        val mnemonics: List<String>,
        val secretHex: String,
    ) {
        val isExtendable: Boolean get() = description.contains("xtendable", ignoreCase = true)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
