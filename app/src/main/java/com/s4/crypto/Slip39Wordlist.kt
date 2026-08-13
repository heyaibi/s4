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

/**
 * The 1024-word SLIP-39 English wordlist, extracted verbatim from
 * `bc-slip39/wordlist-english.h` (see PIN.txt for the pinned upstream).
 *
 * Used for share-word validation and input suggestions. The list is sorted
 * alphabetically (a property of the SLIP-39 wordlist); each word maps to its
 * 10-bit index.
 */
object Slip39Wordlist {

    const val SIZE = 1024

    private val wordlist: List<String> by lazy {
        val stream = checkNotNull(
            Slip39Wordlist::class.java.classLoader?.getResourceAsStream("slip39-wordlist.txt"),
        ) { "SLIP-39 wordlist resource not found" }
        stream.bufferedReader().readLines()
    }

    private val wordToIndex: Map<String, Int> by lazy {
        check(wordlist.size == SIZE && wordlist.toSet().size == SIZE) {
            "SLIP-39 wordlist must contain exactly $SIZE unique words"
        }
        check(wordlist == wordlist.sorted()) { "SLIP-39 wordlist must be sorted" }
        wordlist.mapIndexed { i, word -> word to i }.toMap()
    }

    /** The full 1024-word list, sorted alphabetically. */
    val words: List<String> get() = wordlist

    /** Whether [word] (case-insensitive) is a valid SLIP-39 English word. */
    fun isValidWord(word: String): Boolean = word.trim().lowercase() in wordToIndex

    /** The 10-bit index of a valid [word], or `null` if it is not in the list. */
    fun indexOf(word: String): Int? = wordToIndex[word.trim().lowercase()]
}
