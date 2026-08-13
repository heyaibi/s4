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

package com.s4.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WordCompletionTest {

    // ---- currentTypedWord ----

    @Test
    fun currentTypedWord_singlePartialWord_noSeparator() {
        // User typed just "a" — no preceding space or newline.
        assertEquals("a", currentTypedWord("a"))
    }

    @Test
    fun currentTypedWord_afterSpace() {
        assertEquals("ac", currentTypedWord("academic ac"))
    }

    @Test
    fun currentTypedWord_afterNewline() {
        // Bug case: previous line ended with a newline, user starts typing on the next line.
        assertEquals("a", currentTypedWord("academic acid acne\na"))
    }

    @Test
    fun currentTypedWord_afterMultipleWords_multiline() {
        assertEquals("ac", currentTypedWord("academic acid\nac"))
    }

    @Test
    fun currentTypedWord_emptyInput() {
        assertEquals("", currentTypedWord(""))
    }

    @Test
    fun currentTypedWord_trailingSpaceOnly() {
        // Cursor is right after a space — no partial word yet.
        assertEquals("", currentTypedWord("academic "))
    }

    @Test
    fun currentTypedWord_respectsCursorMidPhrase() {
        // BUG-007: the word at the cursor must drive suggestions, not the last token.
        // Cursor is after "alp" inside "alpha beta" — the current word is "alp".
        assertEquals("alp", currentTypedWord("alpha beta", cursor = 3))
        // Cursor after "be" — the current word is "be".
        assertEquals("be", currentTypedWord("alpha beta", cursor = 8))
    }

    // ---- completeWord ----

    @Test
    fun completeWord_singlePartialWordNoSeparator() {
        // "a" → pick "acid" → "acid "
        val result = completeWord("a", cursor = 1, suggestion = "acid")
        assertEquals("acid ", result.text)
        assertEquals(5, result.cursor)
    }

    @Test
    fun completeWord_afterSpace() {
        val result = completeWord("academic ac", cursor = 10, suggestion = "acid")
        assertEquals("academic acid ", result.text)
        assertEquals(14, result.cursor)
    }

    @Test
    fun completeWord_afterNewline_bugCase() {
        // This was the reported bug: newline was not treated as a separator,
        // so the entire first line was erased when completing on the second line.
        val before = "academic acid acne\na"
        val result = completeWord(before, cursor = before.length, suggestion = "acid")
        assertEquals("academic acid acne\nacid ", result.text)
        assertEquals(24, result.cursor)
    }

    @Test
    fun completeWord_multilineMultiWord() {
        val before = "academic acid\nac"
        val result = completeWord(before, cursor = before.length, suggestion = "acid")
        assertEquals("academic acid\nacid ", result.text)
        assertEquals(19, result.cursor)
    }

    @Test
    fun completeWord_trailingSpaceBeforePartialWord() {
        // Space is the last separator.
        val result = completeWord("foo ba", cursor = 5, suggestion = "bar")
        assertEquals("foo bar ", result.text)
        assertEquals(8, result.cursor)
    }

    @Test
    fun completeWord_appendsTrailingSpace() {
        val result = completeWord("test", cursor = 4, suggestion = "acid")
        assert(result.text.endsWith(" ")) { "result should end with a space: '${result.text}'" }
    }

    @Test
    fun completeWord_midPhrase_replacesTokenUnderCursorOnly() {
        // BUG-007: editing mid-phrase must not corrupt the text. Cursor inside
        // "alpha" (after "al") completes only that token and preserves the rest.
        val result = completeWord("alpha beta gamma", cursor = 2, suggestion = "abandon")
        assertEquals("abandon beta gamma", result.text)
        assertEquals(7, result.cursor)
    }

    @Test
    fun completeWord_midPhrase_keepsExistingSeparator() {
        // Completing a token that has a following separator keeps that separator
        // (no double space), unlike the end-of-text case which adds a space.
        val result = completeWord("alpha beta", cursor = 3, suggestion = "abandon")
        assertEquals("abandon beta", result.text)
        assertEquals(7, result.cursor)
    }
}
