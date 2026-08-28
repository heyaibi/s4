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

import java.security.SecureRandom

/**
 * Generates the short, hand-writable session codes the user copies onto paper
 * and re-types to resume a stamped session.
 *
 * The alphabet excludes visually confusable characters (0/O, 1/I) so a code
 * written in pen and re-typed weeks later cannot be misread. [isValidCode]
 * normalizes case and validates what the user typed back in.
 */
object SessionCodeGenerator {

    /** Uppercase, unambiguous: no 0, 1, I, or O. */
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    const val CODE_LENGTH = 6

    private val random = SecureRandom()

    fun generate(): String = buildString(CODE_LENGTH) {
        repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** True when [raw] (case-insensitive, whitespace-trimmed) is a well-formed code. */
    fun isValidCode(raw: String): Boolean {
        val code = raw.trim().uppercase()
        return code.length == CODE_LENGTH && code.all { it in ALPHABET }
    }

    /** True when [char] is a valid code character (any case). */
    fun isAllowedChar(char: Char): Boolean = char.uppercaseChar() in ALPHABET

    /**
     * Normalizes user input: trims whitespace and uppercases.
     */
    fun normalize(raw: String): String = raw.trim().uppercase()

    /**
     * A collision-free integer identity for [code], used as the PendingIntent
     * request code for the session's expiry alarm. Treats the code as a
     * base-31 number, so distinct codes never map to the same id (31^6 < 2^31).
     */
    fun requestCode(code: String): Int {
        var value = 0
        for (c in code.trim().uppercase()) {
            value = value * ALPHABET.length + ALPHABET.indexOf(c)
        }
        return value and 0x7fffffff
    }
}
