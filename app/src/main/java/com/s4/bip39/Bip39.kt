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

import com.s4.crypto.Slip39
import java.security.MessageDigest
import java.text.Normalizer
import java.util.BitSet

/**
 * Minimal BIP-39 implementation (English wordlist only).
 *
 * - [entropyToMnemonic] turns 16/20/24/28/32 entropy bytes into 12/15/18/21/24 words.
 * - [mnemonicToEntropy] validates the words and their checksum and returns the entropy.
 *
 * Backed by the wordlist resource `wordlist.txt` (2048 words), loaded once and
 * cached. Throws [IllegalArgumentException] on any invalid input.
 */
object Bip39 {

    private const val WORD_BITS = 11
    private const val WORDLIST_SIZE = 2048

    /** Valid mnemonic lengths in words. */
    val VALID_WORD_COUNTS = setOf(12, 15, 18, 21, 24)

    /** Valid entropy lengths in bytes. */
    val VALID_ENTROPY_SIZES = setOf(16, 20, 24, 28, 32)

    private val wordlist: List<String> by lazy {
        val stream = Bip39::class.java.classLoader
            ?.getResourceAsStream("wordlist.txt")
            ?: error("BIP-39 wordlist resource not found")
        stream.bufferedReader().readLines()
    }

    private val wordToIndex: Map<String, Int> by lazy {
        check(wordlist.size == WORDLIST_SIZE && wordlist.toSet().size == WORDLIST_SIZE) {
            "wordlist must contain exactly $WORDLIST_SIZE unique words"
        }
        wordlist.mapIndexed { i, word -> word to i }.toMap()
    }

    /** The full 2048-word list, sorted alphabetically. */
    val words: List<String> get() = wordlist

    /**
     * Decodes [words] back into entropy. Requires the word count to be one of
     * [VALID_WORD_COUNTS], every word to be in the wordlist, and the embedded
     * checksum to match. The comparison is case-insensitive.
     *
     * @throws IllegalArgumentException if any check fails.
     */
    fun mnemonicToEntropy(words: List<String>): ByteArray {
        require(words.size in VALID_WORD_COUNTS) {
            "mnemonic must have 12, 15, 18, 21, or 24 words (got ${words.size})"
        }

        val totalBits = words.size * WORD_BITS
        val checksumBits = totalBits / 33
        val entropyBits = totalBits - checksumBits

        val bits = BitSet(totalBits)
        for (w in words.indices) {
            val index = wordToIndex[words[w].trim().lowercase()]
                ?: throw IllegalArgumentException("word ${w + 1} ('${words[w]}') is not in the BIP-39 wordlist")
            for (b in 0 until WORD_BITS) {
                if (index and (1 shl (WORD_BITS - 1 - b)) != 0) {
                    bits.set(w * WORD_BITS + b)
                }
            }
        }

        val entropy = ByteArray(entropyBits / 8)
        for (i in 0 until entropyBits) {
            if (bits[i]) entropy.setBit(i)
        }

        val digest = sha256(entropy)
        for (i in 0 until checksumBits) {
            if (bits[entropyBits + i] != digest.bitAt(i)) {
                throw IllegalArgumentException("mnemonic checksum does not match")
            }
        }
        return entropy
    }

    /** Whether [word] (case-insensitive) is a valid BIP-39 English word. */
    fun isValidWord(word: String): Boolean = word.trim().lowercase() in wordToIndex

    /**
     * Encodes [entropy] (16, 20, 24, 28, or 32 bytes) as a BIP-39 mnemonic,
     * recomputing the checksum. The checksum length is `entropyBits / 32`.
     *
     * @throws IllegalArgumentException if the entropy length is invalid.
     */
    fun entropyToMnemonic(entropy: ByteArray): List<String> {
        require(entropy.size in VALID_ENTROPY_SIZES) {
            "entropy must be 16, 20, 24, 28, or 32 bytes (got ${entropy.size})"
        }

        val entropyBits = entropy.size * 8
        val checksumBits = entropyBits / 32
        val totalBits = entropyBits + checksumBits

        val bits = BitSet(totalBits)
        for (i in 0 until entropyBits) {
            if (entropy.bitAt(i)) bits.set(i)
        }
        val digest = sha256(entropy)
        for (i in 0 until checksumBits) {
            if (digest.bitAt(i)) bits.set(entropyBits + i)
        }

        val words = ArrayList<String>(totalBits / WORD_BITS)
        for (start in 0 until totalBits step WORD_BITS) {
            var index = 0
            for (b in 0 until WORD_BITS) {
                index = (index shl 1) or (if (bits[start + b]) 1 else 0)
            }
            words += wordlist[index]
        }
        return words
    }

    /**
     * Derives the 64-byte BIP-32 seed per BIP-39:
     * `PBKDF2-HMAC-SHA512(NFKD(mnemonic), "mnemonic" + NFKD(passphrase), 2048)`.
     *
     * Both the mnemonic sentence and the passphrase are NFKD-normalized before
     * UTF-8 encoding, as the BIP-39 spec mandates. English mnemonic words are
     * ASCII (NFKD is a no-op), but a non-ASCII passphrase must be normalized or
     * the derived seed diverges from every spec-compliant wallet.
     *
     * The [passphrase] is the BIP-39 "25th word" — optional, and *never* stored
     * or sharded by this app (it is a separate secret the user preserves). An
     * empty passphrase means the wallet used none.
     *
     * @throws IllegalArgumentException if [words] is not a valid mnemonic.
     */
    fun deriveSeed(words: List<String>, passphrase: String = ""): ByteArray {
        mnemonicToEntropy(words) // validates word count, membership, and checksum
        val mnemonic = Normalizer.normalize(
            words.joinToString(" ") { it.trim().lowercase() },
            Normalizer.Form.NFKD,
        )
        val salt = "mnemonic" + Normalizer.normalize(passphrase, Normalizer.Form.NFKD)
        return Slip39.pbkdf2Sha512(
            mnemonic.toByteArray(Charsets.UTF_8),
            salt.toByteArray(Charsets.UTF_8),
            2048,
        )
    }

    /**
     * The verification fingerprint shown at split and restore time:
     * `SHA-256(seed)` truncated to its first 16 hex characters (8 bytes).
     *
     * [seed] is the 64-byte output of [deriveSeed]. A wrong passphrase (or a
     * wrong word) produces a different fingerprint, catching mistakes before
     * any funds are touched. Note: this is an app-internal consistency check —
     * no other wallet displays `SHA-256(seed)`, so the value is meant to be
     * compared between this app's split and restore, not against an external
     * wallet.
     */
    fun fingerprint(seed: ByteArray): String {
        require(seed.size == 64) { "seed must be 64 bytes (got ${seed.size})" }
        val digest = sha256(seed)
        return buildString(16) {
            for (i in 0 until 8) append("%02x".format(digest[i]))
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    /** Reads bit [pos] (MSB first) of [this]. */
    private fun ByteArray.bitAt(pos: Int): Boolean =
        (this[pos / 8].toInt() and (1 shl (7 - (pos % 8)))) != 0

    /** Sets bit [pos] (MSB first) of [this]. */
    private fun ByteArray.setBit(pos: Int) {
        this[pos / 8] = (this[pos / 8].toInt() or (1 shl (7 - (pos % 8)))).toByte()
    }
}
