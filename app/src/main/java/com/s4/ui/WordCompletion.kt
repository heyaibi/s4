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

/**
 * Returns the partial word under [cursor] in [text] — the token containing the
 * cursor, truncated at the cursor (so suggestions match what has been typed so
 * far). Tokens are delimited by spaces **and** newlines.
 */
internal fun currentTypedWord(text: String, cursor: Int = text.length): String {
    val start = tokenStart(text, cursor)
    return text.substring(start, cursor).trim().lowercase()
}

/**
 * Replaces the token under [cursor] in [text] with [suggestion], preserving all
 * surrounding text (including multi-line structure). A trailing space is
 * appended when the cursor is at the end of the text; otherwise the existing
 * separator is kept. The returned [WordCompletion.cursor] positions the caret
 * right after the inserted suggestion.
 */
internal fun completeWord(text: String, cursor: Int, suggestion: String): WordCompletion {
    val start = tokenStart(text, cursor)
    val end = tokenEnd(text, cursor)
    val trailing = if (end < text.length) "" else " "
    val completed = text.substring(0, start) + suggestion + trailing + text.substring(end)
    return WordCompletion(completed, start + suggestion.length + trailing.length)
}

/** The text and caret position after a word completion. */
internal data class WordCompletion(val text: String, val cursor: Int)

private fun tokenStart(text: String, cursor: Int): Int {
    var i = cursor.coerceIn(0, text.length)
    while (i > 0 && !isSeparator(text[i - 1])) i--
    return i
}

private fun tokenEnd(text: String, cursor: Int): Int {
    var i = cursor.coerceIn(0, text.length)
    while (i < text.length && !isSeparator(text[i])) i++
    return i
}

private fun isSeparator(c: Char): Boolean = c == ' ' || c == '\n'
