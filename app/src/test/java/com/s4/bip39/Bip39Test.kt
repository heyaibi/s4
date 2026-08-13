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

package com.s4.bip39

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BIP-39 tests against the official test vectors from the bitcoin/bips repo
 * (English subset of `bip-0039/vectors.json`, stored in
 * `src/test/resources/bip39-english-vectors.tsv` as `entropyHex<TAB>mnemonic`
 * lines). Covers round-trips, checksum rejection, and input validation.
 */
class Bip39Test {

    private val vectors: List<Pair<ByteArray, List<String>>> by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("bip39-english-vectors.tsv")) {
            "vectors resource missing"
        }
        stream.bufferedReader().readLines().map { line ->
            val (hex, mnemonic) = line.split('\t')
            hex.hexToBytes() to mnemonic.split(' ')
        }
    }

    private val wordlist: List<String> by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("wordlist.txt")) {
            "wordlist resource missing"
        }
        stream.bufferedReader().readLines()
    }

    @Test
    fun entropyToMnemonic_matchesOfficialVectors() {
        for ((entropy, mnemonic) in vectors) {
            assertEquals(mnemonic, Bip39.entropyToMnemonic(entropy))
        }
    }

    @Test
    fun mnemonicToEntropy_matchesOfficialVectors() {
        for ((entropy, mnemonic) in vectors) {
            assertBytesEqual(entropy, Bip39.mnemonicToEntropy(mnemonic))
        }
    }

    @Test
    fun roundTrip_entropyToMnemonicToEntropy() {
        for ((entropy, _) in vectors) {
            assertBytesEqual(entropy, Bip39.mnemonicToEntropy(Bip39.entropyToMnemonic(entropy)))
        }
    }

    @Test
    fun mnemonicToEntropy_acceptsUppercase() {
        val (entropy, mnemonic) = vectors.first()
        assertBytesEqual(entropy, Bip39.mnemonicToEntropy(mnemonic.map { it.uppercase() }))
    }

    @Test
    fun mnemonicToEntropy_rejectsCorruptedChecksum() {
        val (_, mnemonic) = vectors.first()
        val lastIndex = wordlist.indexOf(mnemonic.last())
        val corrupted = mnemonic.dropLast(1) + wordlist[lastIndex xor 1]
        assertThrows(IllegalArgumentException::class.java) {
            Bip39.mnemonicToEntropy(corrupted)
        }
    }

    @Test
    fun mnemonicToEntropy_rejectsWordNotInWordlist() {
        assertThrows(IllegalArgumentException::class.java) {
            Bip39.mnemonicToEntropy(List(12) { "notaword" })
        }
    }

    @Test
    fun mnemonicToEntropy_rejectsInvalidWordCount() {
        assertThrows(IllegalArgumentException::class.java) {
            Bip39.mnemonicToEntropy(List(11) { "abandon" })
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bip39.mnemonicToEntropy(List(13) { "abandon" })
        }
    }

    @Test
    fun entropyToMnemonic_rejectsInvalidEntropySizes() {
        for (size in listOf(0, 15, 17, 33, 64)) {
            assertThrows(IllegalArgumentException::class.java) {
                Bip39.entropyToMnemonic(ByteArray(size))
            }
        }
    }

    @Test
    fun wordlist_has2048UniqueWords() {
        assertEquals(2048, wordlist.size)
        assertEquals(2048, wordlist.toSet().size)
    }

    @Test
    fun isValidWord_trueForWordlistWords_falseOtherwise() {
        assertTrue(Bip39.isValidWord("abandon"))
        assertTrue(Bip39.isValidWord("ABANDON"))
        assertTrue(Bip39.isValidWord("zoo"))
        assertFalse(Bip39.isValidWord("notaword"))
        assertFalse(Bip39.isValidWord(""))
    }

    @Test
    fun deriveSeed_matchesOfficialBip39Vector() {
        // Official BIP-39 test vector: mnemonic "abandon…about" + passphrase "TREZOR".
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(' ')
        val seed = Bip39.deriveSeed(words, "TREZOR")
        assertEquals(
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            seed.toHex(),
        )
    }

    @Test
    fun deriveSeed_noPassphrase() {
        // Same mnemonic, no passphrase — different seed, and the salt is just "mnemonic".
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(' ')
        val seed = Bip39.deriveSeed(words, "")
        assertEquals(64, seed.size)
        assertEquals(
            "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4",
            seed.toHex(),
        )
    }

    @Test
    fun deriveSeed_nfkdNormalizesPassphrase() {
        // BIP-39 mandates NFKD normalization of the mnemonic + passphrase before
        // UTF-8 encoding. "café" written as NFC ("\u00e9") and NFD ("e\u0301")
        // must derive the SAME seed, and differ from the plain "cafe" passphrase.
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(' ')
        val nfc = Bip39.deriveSeed(words, "caf\u00e9")
        val nfd = Bip39.deriveSeed(words, "cafe\u0301")
        assertEquals("NFC and NFD passphrase spellings must share one seed", nfc.toList(), nfd.toList())
        assertFalse("the plain ASCII passphrase must derive a different seed", Bip39.deriveSeed(words, "cafe").toList() == nfc.toList())
    }

    @Test
    fun fingerprint_stableForSameSeed() {
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(' ')
        val seed = Bip39.deriveSeed(words, "TREZOR")
        assertEquals(Bip39.fingerprint(seed), Bip39.fingerprint(Bip39.deriveSeed(words, "TREZOR")))
    }

    @Test
    fun fingerprint_differsWithWrongPassphrase() {
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(' ')
        val withPass = Bip39.fingerprint(Bip39.deriveSeed(words, "TREZOR"))
        val wrongPass = Bip39.fingerprint(Bip39.deriveSeed(words, "TREZORX"))
        val noPass = Bip39.fingerprint(Bip39.deriveSeed(words, ""))
        assertFalse(withPass == wrongPass)
        assertFalse(withPass == noPass)
        assertEquals(16, withPass.length)
    }

    @Test
    fun fingerprint_catchesWrongWord() {
        // A wrong word is caught by checksum validation before any fingerprint:
        // the (valid) mnemonic with one word swapped fails to decode.
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(' ')
        val wrong = words.toMutableList().also { it[0] = "ability" }
        assertThrows(IllegalArgumentException::class.java) {
            Bip39.deriveSeed(wrong, "")
        }

        // Two different *valid* mnemonics must give different fingerprints.
        val (entropyA, entropyB) = vectors.take(2).map { it.first }
        val wordsA = Bip39.entropyToMnemonic(entropyA)
        val wordsB = Bip39.entropyToMnemonic(entropyB)
        assertFalse(
            Bip39.fingerprint(Bip39.deriveSeed(wordsA, "")) ==
                Bip39.fingerprint(Bip39.deriveSeed(wordsB, "")),
        )
    }

    @Test
    fun deriveSeed_rejectsInvalidMnemonic() {
        assertThrows(IllegalArgumentException::class.java) {
            Bip39.deriveSeed(List(12) { "notaword" })
        }
    }

    @Test
    fun fingerprint_rejectsWrongSeedSize() {
        assertThrows(IllegalArgumentException::class.java) {
            Bip39.fingerprint(ByteArray(32))
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun assertBytesEqual(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
