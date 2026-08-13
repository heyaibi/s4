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
 * Encodes/decodes shares in the app's `index:hex` text format and provides hex
 * helpers.
 *
 * Indices are 1-based and stored separately from the share bytes (bc-shamir does
 * not embed the index — see [Shamir]). [decode] is the inverse of
 * [encode]; [fromHex] is case-insensitive and ignores whitespace.
 */
object ShareCodec {

    /** Formats one share as `index:hex`. */
    fun encode(index: Int, share: ByteArray): String = "$index:${toHex(share)}"

    /**
     * Parses a single `index:hex` line into `(index, bytes)`.
     *
     * @throws IllegalArgumentException if the line is not `index:hex` with a
     *   1-based index and valid hexadecimal data.
     */
    fun decode(line: String): Pair<Int, ByteArray> {
        val colon = line.indexOf(':')
        if (colon <= 0) {
            throw IllegalArgumentException("expected 'index:hex' but got '${line.trim()}'")
        }
        val index = line.substring(0, colon).trim().toIntOrNull()
            ?: throw IllegalArgumentException("invalid share index in '${line.trim()}'")
        require(index >= 1) { "share index must be >= 1 but got $index" }
        val hex = line.substring(colon + 1)
        if (hex.isBlank()) {
            throw IllegalArgumentException("missing share data in '${line.trim()}'")
        }
        return index to fromHex(hex)
    }

    fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            append(HEX[(b.toInt() ushr 4) and 0xF])
            append(HEX[b.toInt() and 0xF])
        }
    }

    /**
     * Hex grouped for human reading and writing — 5-char groups, a few per line.
     * Whitespace is ignored by [fromHex], so the grouped form can be pasted back.
     */
    fun toReadableHex(bytes: ByteArray): String =
        toHex(bytes).chunked(5).chunked(4).joinToString("\n") { it.joinToString(" ") }

    /**
     * Parses hexadecimal (case-insensitive, whitespace ignored) into bytes.
     *
     * @throws IllegalArgumentException if the string is not even-length hex.
     */
    fun fromHex(hex: String): ByteArray {
        val digits = hex.filterNot { it.isWhitespace() }
        require(digits.length % 2 == 0) { "hex must have an even number of digits" }
        require(digits.all { it.isHexDigit() }) {
            "invalid hex character in '${hex.trim()}'"
        }
        return ByteArray(digits.length / 2) { i ->
            ((digits[i * 2].hexValue() shl 4) or digits[i * 2 + 1].hexValue()).toByte()
        }
    }

    private const val HEX = "0123456789abcdef"

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun Char.hexValue(): Int = when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        in 'A'..'F' -> this - 'A' + 10
        else -> 0
    }
}
