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

import android.annotation.SuppressLint
import com.s4.model.SplitParams

/**
 * Kotlin facade over the JNI bindings to the vendored bc-slip39 C library.
 *
 * Splits a 16–32 byte (even-length) secret into [SplitParams.shareCount]
 * SLIP-39 mnemonic shares; any [SplitParams.threshold] shares recover the
 * exact secret bytes.
 *
 * Scheme (fixed by the app, see plan.md):
 * - Single SLIP-39 group: `group_threshold = 1`, `[{threshold, count}]`.
 * - SLIP-39 encryption passphrase is `""` (the BIP-39 passphrase is a separate,
 *   preserved secret and is never sharded).
 * - `iteration_exponent = 0` (the spec default).
 */
object Slip39 {

    private const val MIN_SECRET_LENGTH = 16
    private const val MAX_SECRET_LENGTH = 32

    @Volatile
    private var nativeLoaded = false

    /**
     * Loads the native library. On Android this is `libslip39_jni.so`. JVM unit
     * tests load a host build via the `slip39.native.library` system property
     * (set in `app/build.gradle.kts`); that property is only ever present in
     * the test JVM, never on device.
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun ensureNativeLoaded() {
        if (nativeLoaded) return
        synchronized(this) {
            if (nativeLoaded) return
            val hostLib = System.getProperty("slip39.native.library")
            if (hostLib != null) {
                System.load(hostLib)
            } else {
                System.loadLibrary("slip39_jni")
            }
            nativeLoaded = true
        }
    }

    /**
     * Splits [secret] into [params.shareCount] SLIP-39 mnemonic shares; any
     * [params.threshold] of them recover the secret.
     *
     * @throws IllegalArgumentException if [secret] is not 16–32 even-length bytes.
     */
    fun split(params: SplitParams, secret: ByteArray): List<String> {
        validateSecret(secret.size)
        ensureNativeLoaded()
        return nativeGenerate(params.threshold, params.shareCount, secret).toList()
    }

    /**
     * Recovers the secret bytes from at least the threshold number of [mnemonics]
     * (SLIP-39 shares, each a whitespace-delimited phrase).
     *
     * [passphrase] is the SLIP-39 *encryption* passphrase, always `""` for shares
     * created by this app. It is exposed so tests can verify against the official
     * trezor vectors (which use `b"TREZOR"`); it is distinct from the BIP-39
     * passphrase, which is never sharded.
     *
     * @throws Slip39Exception with the native error code on a bad share set
     *   (corrupted word, wrong checksum, mismatched identifiers, too few shares).
     */
    fun combine(mnemonics: List<String>, passphrase: String = ""): ByteArray {
        require(mnemonics.isNotEmpty()) { "at least one share is required" }
        require(mnemonics.all { it.isNotBlank() }) { "share must not be blank" }
        ensureNativeLoaded()
        return nativeCombine(mnemonics.toTypedArray(), passphrase)
    }

    /**
     * PBKDF2-HMAC-SHA512, used for the BIP-39 seed derivation
     * (`PBKDF2-HMAC-SHA512(mnemonic, "mnemonic" + passphrase, 2048)`) and the
     * resulting 64-byte seed. Reuses the vendored bc-crypto-base pbkdf2.
     */
    fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations >= 1) { "iterations must be >= 1" }
        ensureNativeLoaded()
        return nativePbkdf2Sha512(password, salt, iterations)
    }

    private fun validateSecret(length: Int) {
        require(length in MIN_SECRET_LENGTH..MAX_SECRET_LENGTH) {
            "secret length must be in $MIN_SECRET_LENGTH..$MAX_SECRET_LENGTH bytes"
        }
        require(length % 2 == 0) { "secret length must be even" }
    }

    @JvmStatic
    private external fun nativeGenerate(threshold: Int, shareCount: Int, secret: ByteArray): Array<String>

    @JvmStatic
    private external fun nativeCombine(mnemonics: Array<String>, passphrase: String): ByteArray

    @JvmStatic
    private external fun nativePbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray
}
