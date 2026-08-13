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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareCodecTest {

    @Test
    fun encode_usesIndexColonHex() {
        assertEquals("3:00ff10", ShareCodec.encode(3, byteArrayOf(0x00, -0x01, 0x10)))
    }

    @Test
    fun toHex_roundTripsAllByteValues() {
        val bytes = ByteArray(256) { it.toByte() }
        val hex = ShareCodec.toHex(bytes)
        assertEquals("0001020304", hex.substring(0, 10))
        assertEquals("fdfeff", hex.substring(hex.length - 6))
        assertArrayEquals(bytes, ShareCodec.fromHex(hex))
    }

    @Test
    fun fromHex_ignoresWhitespaceAndAcceptsUppercase() {
        assertArrayEquals(
            byteArrayOf(0x0a, -0x01),
            ShareCodec.fromHex("0A FF  \n"),
        )
    }

    @Test
    fun decode_parsesIndexAndData() {
        val (index, bytes) = ShareCodec.decode("  7:1a2b3c ")
        assertEquals(7, index)
        assertArrayEquals(byteArrayOf(0x1a, 0x2b, 0x3c), bytes)
    }

    @Test
    fun decode_encodeRoundTrip() {
        val original = "12:deadbeefcafe"
        val (index, bytes) = ShareCodec.decode(original)
        assertEquals(original, ShareCodec.encode(index, bytes))
    }

    @Test
    fun decode_rejectsMissingColon() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.decode("1234abcd")
        }
    }

    @Test
    fun decode_rejectsMissingIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.decode(":1234abcd")
        }
    }

    @Test
    fun decode_rejectsNonNumericIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.decode("x:1234")
        }
    }

    @Test
    fun decode_rejectsZeroOrNegativeIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.decode("0:1234")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.decode("-2:1234")
        }
    }

    @Test
    fun decode_rejectsMissingData() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.decode("3:")
        }
    }

    @Test
    fun toReadableHex_groupsByFiveAndWraps() {
        assertEquals(
            "01234 56789 abcde ffedc\nba987 65432 10",
            ShareCodec.toReadableHex(ShareCodec.fromHex("0123456789abcdeffedcba9876543210")),
        )
    }

    @Test
    fun toReadableHex_roundTripsThroughFromHex() {
        val hex = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertArrayEquals(ShareCodec.fromHex(hex), ShareCodec.fromHex(ShareCodec.toReadableHex(ShareCodec.fromHex(hex))))
    }

    @Test
    fun fromHex_rejectsOddLength() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.fromHex("abc")
        }
    }

    @Test
    fun fromHex_rejectsNonHexCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCodec.fromHex("0x1z")
        }
    }
}
