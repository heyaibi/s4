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

import com.s4.model.SplitParams
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JVM-side tests of the [Shamir] facade's input validation.
 *
 * These must NOT trigger a native call (no .so exists on the JVM), so they
 * only exercise paths that fail before the JNI layer. Native round-trips and
 * the bc-shamir reference vectors live in ShamirInstrumentedTest.
 */
class ShamirTest {

    @Test
    fun split_rejectsOddSecretLength() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.split(SplitParams(3, 5), ByteArray(17))
        }
    }

    @Test
    fun split_rejectsSecretTooShort() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.split(SplitParams(3, 5), ByteArray(15))
        }
    }

    @Test
    fun split_rejectsSecretTooLong() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.split(SplitParams(3, 5), ByteArray(33))
        }
    }

    @Test
    fun splitParams_rejectsZeroThreshold() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitParams(0, 5)
        }
    }

    @Test
    fun splitParams_rejectsThresholdAboveShareCount() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitParams(4, 3)
        }
    }

    @Test
    fun splitParams_rejectsShareCountAboveMax() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitParams(2, 17)
        }
    }

    @Test
    fun recover_rejectsEmptyShareList() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.recover(emptyList())
        }
    }

    @Test
    fun recover_rejectsMismatchedShareLengths() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.recover(listOf(1 to ByteArray(16), 2 to ByteArray(16), 3 to ByteArray(20)))
        }
    }

    @Test
    fun recover_rejectsShareWithOddLength() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.recover(listOf(1 to ByteArray(17)))
        }
    }

    @Test
    fun recover_rejectsIndexBelowOne() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.recover(listOf(0 to ByteArray(16)))
        }
    }

    @Test
    fun recover_rejectsIndexAbove255() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.recover(listOf(256 to ByteArray(16)))
        }
    }
}
