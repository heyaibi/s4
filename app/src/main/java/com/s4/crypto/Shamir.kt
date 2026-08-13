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

/**
 * Kotlin facade over the JNI bindings to the vendored bc-shamir C library.
 *
 * Splits/recover secrets that are 16–32 bytes (even length). The share index
 * is NOT embedded in the share bytes, so the caller must keep each share
 * paired with its index; [recover] expects that pairing.
 */
object Shamir {

    private const val MIN_SECRET_LENGTH = 16
    private const val MAX_SECRET_LENGTH = 32

    @Volatile
    private var nativeLoaded = false

    private fun ensureNativeLoaded() {
        if (!nativeLoaded) {
            synchronized(this) {
                if (!nativeLoaded) {
                    System.loadLibrary("shamir_jni")
                    nativeLoaded = true
                }
            }
        }
    }

    /** Splits [secret] into [params.shareCount] shares; any [params.threshold] recover it. */
    fun split(params: SplitParams, secret: ByteArray): List<ByteArray> {
        validateSecret(secret.size)
        ensureNativeLoaded()
        return nativeSplit(params.threshold, params.shareCount, secret).toList()
    }

    /**
     * Recovers a secret from exactly [shares] shares (one pair per share:
     * `index` 1-based, `data` the share bytes). All shares must be the same
     * length. Throws [ShamirException] if the digest does not validate (a
     * share is wrong) or the set cannot be interpolated.
     */
    fun recover(shares: List<Pair<Int, ByteArray>>): ByteArray {
        require(shares.isNotEmpty()) { "at least one share is required" }
        val shareLength = shares.first().second.size
        validateSecret(shareLength)
        require(shares.all { it.second.size == shareLength }) {
            "all shares must have the same length"
        }
        require(shares.all { it.first in 1..255 }) {
            "share index must be in 1..255"
        }

        val threshold = shares.size
        // bc-shamir coordinates the share at position i with x = i (0-based,
        // matching its reference test suite). The 1-based display index maps
        // to the library coordinate via x = index - 1.
        val x = IntArray(threshold) { shares[it].first - 1 }
        val data = Array(threshold) { shares[it].second }

        ensureNativeLoaded()
        return nativeRecover(threshold, x, data, shareLength)
    }

    /**
     * Test-only deterministic split matching the upstream bc-shamir `fake_random`
     * PRNG, so output equals the reference implementation's test vectors.
     */
    internal fun splitDeterministic(params: SplitParams, secret: ByteArray): List<ByteArray> {
        validateSecret(secret.size)
        ensureNativeLoaded()
        return nativeSplitDeterministic(params.threshold, params.shareCount, secret).toList()
    }

    /** Test-only passthrough to the JNI recover layer, bypassing facade validation. */
    internal fun nativeRecoverForTest(
        threshold: Int,
        x: IntArray,
        shares: Array<ByteArray>,
        shareLength: Int,
    ): ByteArray {
        ensureNativeLoaded()
        return nativeRecover(threshold, x, shares, shareLength)
    }

    private fun validateSecret(length: Int) {
        require(length in MIN_SECRET_LENGTH..MAX_SECRET_LENGTH) {
            "secret length must be in $MIN_SECRET_LENGTH..$MAX_SECRET_LENGTH bytes"
        }
        require(length % 2 == 0) { "secret length must be even" }
    }

    @JvmStatic
    private external fun nativeSplit(threshold: Int, shareCount: Int, secret: ByteArray): Array<ByteArray>

    @JvmStatic
    private external fun nativeSplitDeterministic(threshold: Int, shareCount: Int, secret: ByteArray): Array<ByteArray>

    @JvmStatic
    private external fun nativeRecover(threshold: Int, x: IntArray, shares: Array<ByteArray>, shareLength: Int): ByteArray
}
