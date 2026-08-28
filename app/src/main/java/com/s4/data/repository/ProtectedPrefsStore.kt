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

package com.s4.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.s4.data.crypto.KeystoreManager
import com.s4.data.crypto.PrefsCrypto
import java.security.MessageDigest
import java.util.Base64

internal class ProtectedPrefsStore(
    private val prefs: SharedPreferences,
    crypto: PrefsCrypto? = null,
    private val cryptoFactory: () -> PrefsCrypto? = { runCatching { KeystoreManager() }.getOrNull() },
) {
    companion object {
        const val ENC_PREFIX = "enc:"
        private const val ENCRYPTED_PARTS = 3
        private val processTamperLatch = java.util.concurrent.atomic.AtomicBoolean(false)

        fun consumeProcessTamperFlag(): Boolean = processTamperLatch.getAndSet(false)

        fun markTampered() {
            processTamperLatch.set(true)
        }
    }

    private val injectedCrypto: PrefsCrypto? = crypto

    @Volatile
    private var fallbackCrypto: PrefsCrypto? = null

    private val keystore: PrefsCrypto?
        get() = injectedCrypto ?: fallbackCryptoOrCreate()

    private fun fallbackCryptoOrCreate(): PrefsCrypto? {
        fallbackCrypto?.let { return it }
        synchronized(this) {
            fallbackCrypto?.let { return it }
            val created = cryptoFactory()
            if (created != null) fallbackCrypto = created
            return created
        }
    }

    fun consumeTamperFlag(): Boolean = consumeProcessTamperFlag()

    fun rawToString(value: Any?): String? = when (value) {
        is String -> value
        is Int -> value.toString()
        is Long -> value.toString()
        is Float -> value.toString()
        is Double -> value.toString()
        is Boolean -> value.toString()
        else -> null
    }

    fun readRawPref(key: String): String? {
        val value = try {
            prefs.all[key]
        } catch (e: Exception) {
            return null
        }
        return rawToString(value)
    }

    fun protectString(key: String, value: String): String {
        val ks = keystore
        if (ks == null) {
            markTampered()
            throw IllegalStateException("no crypto available; refusing to persist '$key' in plaintext")
        }
        return try {
            val aad = key.toByteArray(Charsets.UTF_8)
            val (ciphertext, iv) = ks.encrypt(value.toByteArray(Charsets.UTF_8), aad)
            val mac = ks.hmac(aad + iv + ciphertext)
            ENC_PREFIX +
                Base64.getEncoder().encodeToString(iv) + ":" +
                Base64.getEncoder().encodeToString(ciphertext) + ":" +
                Base64.getEncoder().encodeToString(mac)
        } catch (e: Exception) {
            markTampered()
            throw IllegalStateException("failed to protect value for key '$key'", e)
        }
    }

    fun unprotectString(key: String, stored: String, default: String): String {
        return tryUnprotect(key, stored) ?: run {
            markTampered()
            default
        }
    }

    fun readProtectedValueOrNull(key: String): String? {
        val stored = readStoredString(key) ?: return null
        return tryUnprotect(key, stored) ?: run {
            markTampered()
            null
        }
    }

    private fun tryUnprotect(key: String, stored: String): String? {
        if (!stored.startsWith(ENC_PREFIX)) return null
        val ks = keystore
        if (ks == null) return null
        return runCatching {
            val parts = stored.removePrefix(ENC_PREFIX).split(":")
            if (parts.size != ENCRYPTED_PARTS) {
                throw IllegalStateException("malformed protected value")
            }
            val iv = Base64.getDecoder().decode(parts[0])
            val ciphertext = Base64.getDecoder().decode(parts[1])
            val mac = Base64.getDecoder().decode(parts[2])
            val aad = key.toByteArray(Charsets.UTF_8)
            val computed = ks.hmac(aad + iv + ciphertext)
            if (!MessageDigest.isEqual(computed, mac)) {
                throw IllegalStateException("integrity check failed")
            }
            String(ks.decrypt(ciphertext, iv, aad), Charsets.UTF_8)
        }.getOrNull()
    }

    fun protectedPutString(key: String, value: String): Boolean {
        return try {
            prefs.edit { putString(key, protectString(key, value)) }
            true
        } catch (e: IllegalStateException) {
            markTampered()
            false
        }
    }

    fun protectedPutAll(entries: List<Pair<String, String>>): Boolean {
        val protected = try {
            entries.map { (key, value) -> key to protectString(key, value) }
        } catch (e: IllegalStateException) {
            return false
        }
        val editor = prefs.edit()
        protected.forEach { (key, blob) -> editor.putString(key, blob) }
        val committed = editor.commit()
        if (!committed) markTampered()
        return committed
    }

    fun protectedGetString(key: String, default: String): String {
        val stored = readStoredString(key) ?: return default
        return unprotectString(key, stored, default)
    }

    fun protectedPutInt(key: String, value: Int): Boolean = protectedPutString(key, value.toString())

    fun protectedGetInt(key: String, default: Int): Int =
        protectedGetString(key, default.toString()).toIntOrNull() ?: default

    fun protectedPutBoolean(key: String, value: Boolean): Boolean = protectedPutString(key, value.toString())

    fun protectedGetBoolean(key: String, default: Boolean): Boolean =
        protectedGetString(key, default.toString()).toBooleanStrictOrNull() ?: default

    fun removeProtected(key: String) {
        prefs.edit { remove(key) }
    }

    private fun readStoredString(key: String): String? {
        return try {
            prefs.getString(key, null)
        } catch (e: ClassCastException) {
            when (val legacy = prefs.all[key]) {
                is Int -> legacy.toString()
                is Long -> legacy.toString()
                is Float -> legacy.toString()
                is Double -> legacy.toString()
                is Boolean -> legacy.toString()
                else -> null
            }
        }
    }
}
