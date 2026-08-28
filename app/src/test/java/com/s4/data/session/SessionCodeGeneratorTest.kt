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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodeGeneratorTest {

    @Test
    fun `generated codes have the configured length`() {
        repeat(100) {
            assertEquals(SessionCodeGenerator.CODE_LENGTH, SessionCodeGenerator.generate().length)
        }
    }

    @Test
    fun `generated codes use only the unambiguous alphabet`() {
        repeat(1000) {
            val code = SessionCodeGenerator.generate()
            assertTrue("code '$code' must be uppercase alphanumeric", code.all { it.isLetterOrDigit() })
            assertFalse("code '$code' must not contain 0", code.contains('0'))
            assertFalse("code '$code' must not contain O", code.contains('O'))
            assertFalse("code '$code' must not contain 1", code.contains('1'))
            assertFalse("code '$code' must not contain I", code.contains('I'))
        }
    }

    @Test
    fun `generated codes are effectively unique`() {
        val seen = mutableSetOf<String>()
        repeat(10_000) { seen += SessionCodeGenerator.generate() }
        assertEquals(10_000, seen.size)
    }

    @Test
    fun `isValidCode accepts a freshly generated code`() {
        repeat(100) { assertTrue(SessionCodeGenerator.isValidCode(SessionCodeGenerator.generate())) }
    }

    @Test
    fun `isValidCode accepts lowercase and surrounding whitespace`() {
        assertTrue(SessionCodeGenerator.isValidCode("x7k2m9"))
        assertTrue(SessionCodeGenerator.isValidCode("  X7K2M9  "))
    }

    @Test
    fun `isValidCode rejects wrong lengths`() {
        assertFalse(SessionCodeGenerator.isValidCode("X7K2M"))
        assertFalse(SessionCodeGenerator.isValidCode("X7K2M99"))
        assertFalse(SessionCodeGenerator.isValidCode(""))
    }

    @Test
    fun `isValidCode rejects confusable and disallowed characters`() {
        assertFalse(SessionCodeGenerator.isValidCode("X7K2MO"))
        assertFalse(SessionCodeGenerator.isValidCode("X7K2MI"))
        assertFalse(SessionCodeGenerator.isValidCode("X7K2M1"))
        assertFalse(SessionCodeGenerator.isValidCode("X7K2M0"))
        assertFalse(SessionCodeGenerator.isValidCode("X7K2M-"))
        assertFalse(SessionCodeGenerator.isValidCode("X7K2M ")) // trailing space -> trim -> still 6? no, space is trimmed
    }

    @Test
    fun `normalize trims and uppercases`() {
        assertEquals("X7K2M9", SessionCodeGenerator.normalize("  x7k2m9  "))
    }

    @Test
    fun `requestCode is collision-free across a large code space`() {
        val seen = mutableSetOf<Int>()
        repeat(20_000) { seen += SessionCodeGenerator.requestCode(SessionCodeGenerator.generate()) }
        assertEquals(20_000, seen.size)
    }

    @Test
    fun `requestCode is stable for the same code`() {
        val code = SessionCodeGenerator.generate()
        assertEquals(SessionCodeGenerator.requestCode(code), SessionCodeGenerator.requestCode(code.lowercase()))
        assertEquals(SessionCodeGenerator.requestCode(code), SessionCodeGenerator.requestCode(" $code "))
    }

    @Test
    fun `requestCode stays positive and never exceeds Int max`() {
        repeat(10_000) {
            val rc = SessionCodeGenerator.requestCode(SessionCodeGenerator.generate())
            assertTrue("requestCode must be non-negative", rc >= 0)
        }
    }

    @Test
    fun `isAllowedChar mirrors the code alphabet`() {
        assertTrue(SessionCodeGenerator.isAllowedChar('x'))
        assertTrue(SessionCodeGenerator.isAllowedChar('2'))
        assertFalse(SessionCodeGenerator.isAllowedChar('0'))
        assertFalse(SessionCodeGenerator.isAllowedChar('o'))
        assertFalse(SessionCodeGenerator.isAllowedChar('1'))
        assertFalse(SessionCodeGenerator.isAllowedChar('i'))
        assertFalse(SessionCodeGenerator.isAllowedChar('-'))
    }

    @Test
    fun `distinct generated codes have distinct request codes`() {
        // Two different codes must never map to the same PendingIntent request id.
        val a = "X7K2M9"
        val b = "X7K2N9"
        assertNotEquals(a, b)
        assertNotEquals(SessionCodeGenerator.requestCode(a), SessionCodeGenerator.requestCode(b))
    }
}