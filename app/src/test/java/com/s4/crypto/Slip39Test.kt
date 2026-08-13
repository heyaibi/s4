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

import com.s4.bip39.Bip39
import com.s4.model.SplitParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-side tests of the [Slip39] facade against the real native bc-slip39 code
 * (a host dylib built by the `buildHostSlip39` Gradle task, loaded via the
 * `slip39.native.library` system property — see `app/build.gradle.kts`).
 *
 * Covers: official trezor vectors at the JNI boundary, all BIP-39 word-count
 * round-trips, error paths, and the no-hex-in-shares contract.
 */
class Slip39Test {

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
        assertTrue("expected 11 valid non-extendable vectors, got ${valid.size}", valid.size == 11)

        for (v in valid) {
            // The official vectors are generated with the SLIP-39 passphrase b"TREZOR".
            val secret = Slip39.combine(v.mnemonics, "TREZOR")
            assertEquals("${v.description}", v.secretHex, secret.toHex())
        }
    }

    @Test
    fun officialTrezorVectors_invalidCasesError() {
        val invalid = vectors.filter { it.secretHex.isEmpty() && !it.isExtendable }
        assertTrue("expected 30 invalid non-extendable vectors, got ${invalid.size}", invalid.size == 30)

        for (v in invalid) {
            assertThrows("${v.description}", Slip39Exception::class.java) {
                Slip39.combine(v.mnemonics, "TREZOR")
            }
        }
    }

    @Test
    fun roundTrip_anyThresholdSubsetRecoversExactSecret() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), secret)

        assertEquals(6, shares.size)
        shares.forEach { assertEquals(33, it.split(' ').size) }

        for (subset in listOf(listOf(0, 1, 2), listOf(1, 3, 5), listOf(0, 4, 5))) {
            assertArrayEquals(secret, Slip39.combine(subset.map { shares[it] }))
        }
    }

    @Test
    fun shares_containOnlySlip39Words_noHex() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), secret)

        for (share in shares) {
            for (word in share.split(' ')) {
                assertTrue("'$word' must be a SLIP-39 word", Slip39Wordlist.isValidWord(word))
                assertTrue("share word '$word' must not look like hex", !word.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
            }
        }
    }

    @Test
    fun shares_doNotContainHexCharacters() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), secret)
        // SLIP-39 words are real English words; none is a bare hex string of length 2+.
        val hexish = Regex("^[0-9a-f]{2,}\$")
        for (share in shares) {
            for (word in share.split(' ')) {
                assertTrue("'$word' must not be hex", !hexish.matches(word))
            }
        }
    }

    @Test
    fun roundTrip_thresholdOne() {
        val secret = ByteArray(16) { (it + 1).toByte() }
        val shares = Slip39.split(SplitParams(1, 4), secret)
        assertEquals(4, shares.size)
        assertArrayEquals(secret, Slip39.combine(listOf(shares[2])))
    }

    @Test
    fun roundTrip_16of16() {
        val secret = ByteArray(32) { (0xff - it).toByte() }
        val shares = Slip39.split(SplitParams(16, 16), secret)
        assertEquals(16, shares.size)
        assertArrayEquals(secret, Slip39.combine(shares))
    }

    @Test
    fun roundTrip_smallSecret() {
        val secret = ByteArray(16) { (it * 3).toByte() }
        val shares = Slip39.split(SplitParams(2, 5), secret)
        assertEquals(20, shares[0].split(' ').size) // 16-byte secret -> 20-word shares
        assertArrayEquals(secret, Slip39.combine(listOf(shares[0], shares[4])))
    }

    @Test
    fun tooFewShares_fails() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), secret)

        val ex = assertThrows(Slip39Exception::class.java) {
            Slip39.combine(shares.take(2))
        }
        assertTrue("expected a negative native code, got ${ex.code}", ex.code < 0)
    }

    @Test
    fun corruptedWord_failsWithChecksumError() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), secret)

        val words = shares[1].split(' ')
        val corrupted = words.dropLast(1) + Slip39Wordlist.words[(Slip39Wordlist.indexOf(words.last())!! + 1) % 1024]
        val badShare = corrupted.joinToString(" ")

        val ex = assertThrows(Slip39Exception::class.java) {
            Slip39.combine(listOf(shares[0], badShare, shares[2]))
        }
        assertEquals(-2, ex.code) // ERROR_INVALID_MNEMONIC_CHECKSUM
    }

    @Test
    fun mismatchedIdentifiers_fails() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val setA = Slip39.split(SplitParams(3, 6), secret)
        val setB = Slip39.split(SplitParams(3, 6), secret)

        val ex = assertThrows(Slip39Exception::class.java) {
            Slip39.combine(listOf(setA[0], setA[1], setB[2]))
        }
        assertTrue("expected a negative native code, got ${ex.code}", ex.code < 0)
    }

    @Test
    fun wrongWord_fails() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val shares = Slip39.split(SplitParams(3, 6), secret)

        val words = shares[0].split(' ').toMutableList()
        words[0] = Slip39Wordlist.words[(Slip39Wordlist.indexOf(words[0])!! + 1) % 1024]

        assertThrows(Slip39Exception::class.java) {
            Slip39.combine(listOf(words.joinToString(" "), shares[1], shares[2]))
        }
    }

    @Test
    fun split_rejectsOddSecretLength() {
        assertThrows(IllegalArgumentException::class.java) {
            Slip39.split(SplitParams(3, 5), ByteArray(17))
        }
    }

    @Test
    fun split_rejectsSecretTooShort() {
        assertThrows(IllegalArgumentException::class.java) {
            Slip39.split(SplitParams(3, 5), ByteArray(15))
        }
    }

    @Test
    fun split_rejectsSecretTooLong() {
        assertThrows(IllegalArgumentException::class.java) {
            Slip39.split(SplitParams(3, 5), ByteArray(33))
        }
    }

    @Test
    fun combine_rejectsEmptyList() {
        assertThrows(IllegalArgumentException::class.java) {
            Slip39.combine(emptyList())
        }
    }

    @Test
    fun combine_rejectsBlankShare() {
        assertThrows(IllegalArgumentException::class.java) {
            Slip39.combine(listOf("   "))
        }
    }

    @Test
    fun combine_rejectsUnknownWord() {
        assertThrows(IllegalArgumentException::class.java) {
            Slip39.combine(listOf("notaword ${Slip39Wordlist.words.joinToString(" ")}"))
        }
    }

    @Test
    fun roundTrip_bip39Words_throughSlip39_allWordCounts() {
        val wordCounts = listOf(12, 15, 18, 21, 24)
        val entropies = listOf(16, 20, 24, 28, 32)
        // SLIP-39 share word count = 7 metadata words + ceil(secretBits / 10).
        val expectedShareWords = listOf(20, 23, 27, 30, 33)
        for ((wordCount, entropySize, shareWords) in wordCounts.zip(entropies).zip(expectedShareWords)
                .map { (a, b) -> Triple(a.first, a.second, b) }) {
            // Deterministic entropy so the recovered words must match exactly.
            val entropy = ByteArray(entropySize) { (it * 7 + 3).toByte() }
            val words = Bip39.entropyToMnemonic(entropy)
            assertEquals(wordCount, words.size)

            val shares = Slip39.split(SplitParams(3, 6), entropy)
            assertEquals(shareWords, shares[0].split(' ').size)

            for (subset in listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(0, 3, 5))) {
                val recovered = Slip39.combine(subset.map { shares[it] })
                val recoveredWords = Bip39.entropyToMnemonic(recovered)
                assertEquals("$wordCount words", words, recoveredWords)
            }
        }
    }

    @Test
    fun roundTrip_bip39Words_anyThreshold() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val words = Bip39.entropyToMnemonic(entropy)
        val shares = Slip39.split(SplitParams(2, 5), entropy)
        val recovered = Bip39.entropyToMnemonic(Slip39.combine(listOf(shares[1], shares[4])))
        assertEquals(words, recovered)
    }

    @Test
    fun fingerprint_stableAcrossSplitAndRestore() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val words = Bip39.entropyToMnemonic(entropy)
        val passphrase = "hunter2 but not sharded"

        val splitFp = Bip39.fingerprint(Bip39.deriveSeed(words, passphrase))

        // Simulate a restore: shares recover the words; user re-enters the passphrase.
        val shares = Slip39.split(SplitParams(3, 6), entropy)
        val recoveredWords = Bip39.entropyToMnemonic(Slip39.combine(shares.take(3)))
        val restoreFp = Bip39.fingerprint(Bip39.deriveSeed(recoveredWords, passphrase))

        assertEquals(splitFp, restoreFp)
    }

    @Test
    fun fingerprint_noPassphraseWalletRecoversWithoutPassphrase() {
        val entropy = ByteArray(32) { (it * 7 + 3).toByte() }
        val words = Bip39.entropyToMnemonic(entropy)
        val shares = Slip39.split(SplitParams(3, 6), entropy)
        val recoveredWords = Bip39.entropyToMnemonic(Slip39.combine(shares.take(3)))

        // No passphrase was set: words alone are the whole wallet, fingerprint matches.
        assertEquals(
            Bip39.fingerprint(Bip39.deriveSeed(words, "")),
            Bip39.fingerprint(Bip39.deriveSeed(recoveredWords, "")),
        )
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
