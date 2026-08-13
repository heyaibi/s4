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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Slip39WordlistTest {

    @Test
    fun wordlist_has1024UniqueSortedWords() {
        assertEquals(1024, Slip39Wordlist.words.size)
        assertEquals(1024, Slip39Wordlist.words.toSet().size)
        assertEquals(Slip39Wordlist.words.sorted(), Slip39Wordlist.words)
    }

    @Test
    fun firstWords_matchSpec() {
        // First/last entries of the SLIP-39 English wordlist (from wordlist-english.h).
        assertEquals("academic", Slip39Wordlist.words.first())
        assertEquals("zero", Slip39Wordlist.words.last())
        assertEquals("acid", Slip39Wordlist.words[1])
    }

    @Test
    fun isValidWord_caseInsensitive() {
        assertTrue(Slip39Wordlist.isValidWord("academic"))
        assertTrue(Slip39Wordlist.isValidWord("ACADEMIC"))
        assertTrue(Slip39Wordlist.isValidWord("zero"))
        assertFalse(Slip39Wordlist.isValidWord("notaword"))
        assertFalse(Slip39Wordlist.isValidWord(""))
    }

    @Test
    fun indexOf_mapsWordsToTheirPosition() {
        assertEquals(0, Slip39Wordlist.indexOf("academic"))
        assertEquals(1, Slip39Wordlist.indexOf("acid"))
        assertEquals(1023, Slip39Wordlist.indexOf("zero"))
        assertEquals(null, Slip39Wordlist.indexOf("notaword"))
    }
}
