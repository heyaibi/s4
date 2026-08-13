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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.s4.model.SplitParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests exercising the full JNI path: native split/recover plus
 * conformance against deterministic vectors produced by the reference
 * bc-shamir implementation (its `fake_random` test PRNG).
 */
@RunWith(AndroidJUnit4::class)
class ShamirInstrumentedTest {

    @Test
    fun roundTrip_3of5_16byteSecret() {
        val secret = hex("0ff784df000c4380a5ed683f7e6e3dcf")
        val shares = Shamir.split(SplitParams(3, 5), secret)

        assertEquals(5, shares.size)
        shares.forEach { assertEquals(16, it.size) }

        for (indices in listOf(listOf(1, 2, 3), listOf(1, 3, 5), listOf(2, 4, 5), listOf(3, 4, 5))) {
            assertArrayEquals(secret, Shamir.recover(indices.map { it to shares[it - 1] }))
        }
    }

    @Test
    fun roundTrip_2of7_32byteSecret() {
        val secret = hex("204188bfa6b440a1bdfd6753ff55a8241e07af5c5be943db917e3efabc184b1a")
        val shares = Shamir.split(SplitParams(2, 7), secret)

        assertEquals(7, shares.size)
        shares.forEach { assertEquals(32, it.size) }

        assertArrayEquals(secret, Shamir.recover(listOf(1 to shares[0], 7 to shares[6])))
    }

    @Test
    fun roundTrip_thresholdOne() {
        val secret = hex("deadbeefcafebabe1234567890abcdef")
        val shares = Shamir.split(SplitParams(1, 4), secret)

        assertEquals(4, shares.size)
        shares.forEach { assertArrayEquals(secret, it) }
        assertArrayEquals(secret, Shamir.recover(listOf(3 to shares[2])))
    }

    @Test
    fun roundTrip_16of16() {
        val secret = hex("aabbccddeeff00112233445566778899")
        val shares = Shamir.split(SplitParams(16, 16), secret)

        assertEquals(16, shares.size)
        assertArrayEquals(secret, Shamir.recover((1..16).map { it to shares[it - 1] }))
    }

    @Test
    fun roundTrip_3of6_32byteSecret() {
        val secret = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val shares = Shamir.split(SplitParams(3, 6), secret)

        assertEquals(6, shares.size)
        assertArrayEquals(secret, Shamir.recover(listOf(1 to shares[0], 4 to shares[3], 6 to shares[5])))
    }

    @Test
    fun deterministicVectors_matchReferenceImplementation() {
        // (secret, params, expected shares, recovery subset used for round-trip)
        val cases = listOf(
            // case A: 16-byte secret, t=3, n=5
            VectorCase(
                "0ff784df000c4380a5ed683f7e6e3dcf", SplitParams(3, 5),
                listOf(
                    "00112233445566778899aabbccddeeff",
                    "d43099fe444807c46921a4f33a2a798b",
                    "d9ad4e3bec2e1a7485698823abf05d36",
                    "0d8cf5f6ec337bc764d1866b5d07ca42",
                    "1aa7fe3199bc5092ef3816b074cabdf2",
                ),
                listOf(1, 3, 4),
            ),
            // case B: 32-byte secret, t=2, n=7
            VectorCase(
                "204188bfa6b440a1bdfd6753ff55a8241e07af5c5be943db917e3efabc184b1a",
                SplitParams(2, 7),
                listOf(
                    "2dcd14c2252dc8489af3985030e74d5a48e8eff1478ab86e65b43869bf39d556",
                    "a1dfdd798388aada635b9974472b4fc59a32ae520c42c9f6a0af70149b882487",
                    "2ee99daf727c0c7773b89a18de64497ff7476dacd1015a45f482a893f7402cef",
                    "a2fb5414d4d96ee58a109b3ca9a84be0259d2c0f9ac92bdd3199e0eed3f1dd3e",
                    "2b851d188b8f5b3653659cc0f7fa45102dadf04b708767385cd803862fcb3c3f",
                    "a797d4a32d2a39a4aacd9de48036478fff77b1e83b4f16a099c34bfb0b7acdee",
                    "28a19475dcde9f09ba2e9e881979413592027216e60c8513cdee937c67b2c586",
                ),
                listOf(3, 7),
            ),
            // case E: 32-byte secret, t=3, n=6
            VectorCase(
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
                SplitParams(3, 6),
                listOf(
                    "00112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0f",
                    "39e00ae540414243c4c5c6c748494a4bccedceaf505152d3d4d5d65758595adb",
                    "5e6b353adf027ea323fe825f6db0cc11b2aa139acfb16e74334d92327d03dcc6",
                    "679a1decdb165a976fa2ee23e92468a56e66ef76cb854a207f31feaef9b77812",
                    "0cdf0bef131c0d02090617186b64757a4b0c558203101d0d190a07677b686575",
                    "352e233917082936455a7b64eff0d1ce97c0a96e0724395955766bfbffdcc1a1",
                ),
                listOf(2, 4, 6),
            ),
        )

        for (case in cases) {
            val secret = hex(case.secretHex)
            val shares = Shamir.splitDeterministic(case.params, secret)

            assertEquals(case.expectedShares.size, shares.size)
            case.expectedShares.forEachIndexed { i, expectedHex ->
                assertEquals(expectedHex, toHex(shares[i]))
            }

            // The reference-generated shares must also round-trip back to the secret.
            val recovered = Shamir.recover(case.recoveryIndices.map { it to shares[it - 1] })
            assertArrayEquals(secret, recovered)
        }
    }

    private data class VectorCase(
        val secretHex: String,
        val params: SplitParams,
        val expectedShares: List<String>,
        val recoveryIndices: List<Int>,
    )

    @Test
    fun corruptedShare_failsWithChecksumError() {
        val secret = hex("0ff784df000c4380a5ed683f7e6e3dcf")
        val shares = Shamir.split(SplitParams(3, 5), secret)

        val bad = shares[1].copyOf()
        bad[0] = (bad[0] + 1).toByte()

        val ex = assertThrows(ShamirException::class.java) {
            Shamir.recover(listOf(1 to shares[0], 2 to bad, 3 to shares[2]))
        }
        assertEquals(-104, ex.code)
    }

    @Test
    fun tooFewShares_fails() {
        val secret = hex("0ff784df000c4380a5ed683f7e6e3dcf")
        val shares = Shamir.split(SplitParams(3, 5), secret)

        assertThrows(ShamirException::class.java) {
            Shamir.recover(listOf(1 to shares[0], 2 to shares[1]))
        }
    }

    @Test
    fun nativeLayer_rejectsBadInput() {
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.nativeRecoverForTest(0, intArrayOf(1), arrayOf(ByteArray(16)), 16)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.nativeRecoverForTest(2, intArrayOf(1), arrayOf(ByteArray(16)), 16)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Shamir.nativeRecoverForTest(1, intArrayOf(1), arrayOf(ByteArray(20)), 16)
        }
    }

    private fun hex(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
        }
    }

    private fun toHex(bytes: ByteArray): String = buildString {
        for (b in bytes) {
            append(HEX[(b.toInt() shr 4) and 0xf])
            append(HEX[b.toInt() and 0xf])
        }
    }

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}
