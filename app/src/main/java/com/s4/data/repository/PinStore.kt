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
import com.s4.data.crypto.PinManager
import java.util.Base64

data class PinData(
    val hash: ByteArray,
    val salt: ByteArray,
    val iterations: Int,
    val algorithm: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PinData) return false
        return hash.contentEquals(other.hash) &&
            salt.contentEquals(other.salt) &&
            iterations == other.iterations &&
            algorithm == other.algorithm
    }

    override fun hashCode(): Int {
        var result = hash.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iterations
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

internal class PinStore(
    private val prefs: SharedPreferences,
    private val store: ProtectedPrefsStore,
    private val clock: MonotonicClock,
) {
    companion object {
        private const val KEY_PIN_RECORD = "pin_record"
        private const val RECORD_VERSION = "v1"
        private const val RECORD_FIELDS = 5

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_ITERATIONS = "pin_iterations"
        private const val KEY_PIN_ALGORITHM = "pin_algorithm"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"

        private val lockoutLock = Any()
    }

    fun isPinSet(): Boolean {
        if (prefs.contains(KEY_PIN_RECORD)) return true
        return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)
    }

    fun savePin(pinHash: ByteArray, salt: ByteArray, iterations: Int, algorithm: String): Boolean {
        val record = buildRecord(pinHash, salt, iterations, algorithm) ?: return false
        return store.protectedPutAll(listOf(KEY_PIN_RECORD to record))
    }

    /** Clears any stored PIN credential (record + legacy keys). */
    fun clearPin() {
        store.removeProtected(KEY_PIN_RECORD)
        prefs.edit {
            remove(KEY_PIN_RECORD)
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_ITERATIONS)
            remove(KEY_PIN_ALGORITHM)
        }
        resetPinFailedAttempts()
    }

    fun getPinData(): PinData? {
        if (prefs.contains(KEY_PIN_RECORD)) {
            val record = store.readProtectedValueOrNull(KEY_PIN_RECORD) ?: return null
            return parseRecord(record)
        }
        val hashB64 = store.unprotectString(KEY_PIN_HASH, prefs.getString(KEY_PIN_HASH, null) ?: return null, "")
        val saltB64 = store.unprotectString(KEY_PIN_SALT, prefs.getString(KEY_PIN_SALT, null) ?: return null, "")
        if (hashB64.isEmpty() || saltB64.isEmpty()) return null
        return try {
            val hash = Base64.getDecoder().decode(hashB64)
            val salt = Base64.getDecoder().decode(saltB64)
            val iterations = prefs.getInt(KEY_PIN_ITERATIONS, PinManager.DEFAULT_ITERATIONS)
            val algorithm = prefs.getString(KEY_PIN_ALGORITHM, PinManager.DEFAULT_ALGORITHM)
                ?: PinManager.DEFAULT_ALGORITHM
            PinData(hash, salt, iterations, algorithm)
        } catch (e: Exception) {
            null
        }
    }

    fun getPinFailedAttempts(): Int = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)

    fun incrementPinFailedAttempts(): Int {
        synchronized(lockoutLock) {
            val count = getPinFailedAttempts() + 1
            prefs.edit { putInt(KEY_PIN_FAILED_ATTEMPTS, count) }
            return count
        }
    }

    fun resetPinFailedAttempts() {
        synchronized(lockoutLock) {
            prefs.edit {
                remove(KEY_PIN_FAILED_ATTEMPTS)
                remove(KEY_PIN_LOCKOUT_UNTIL)
            }
        }
    }

    fun getPinLockoutUntil(): Long = prefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)

    fun setPinLockoutUntil(deadline: Long) {
        synchronized(lockoutLock) {
            if (deadline > 0L) clock.persistNow()
            prefs.edit { putLong(KEY_PIN_LOCKOUT_UNTIL, deadline) }
        }
    }

    fun getPinLockoutRemainingMs(): Long {
        val deadline = getPinLockoutUntil()
        if (deadline <= 0L) return 0L
        return (deadline - clock.now()).coerceAtLeast(0L)
    }

    private fun buildRecord(pinHash: ByteArray, salt: ByteArray, iterations: Int, algorithm: String): String? {
        if (pinHash.isEmpty() || salt.isEmpty() || iterations <= 0 || algorithm.isEmpty()) return null
        return listOf(
            RECORD_VERSION,
            Base64.getEncoder().encodeToString(salt),
            iterations.toString(),
            Base64.getEncoder().encodeToString(algorithm.toByteArray(Charsets.UTF_8)),
            Base64.getEncoder().encodeToString(pinHash),
        ).joinToString(":")
    }

    private fun parseRecord(record: String): PinData? {
        val parts = record.split(':')
        if (parts.size != RECORD_FIELDS || parts[0] != RECORD_VERSION) return null
        val salt = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return null
        val iterations = parts[2].toIntOrNull() ?: return null
        val algorithm = runCatching {
            String(Base64.getDecoder().decode(parts[3]), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val hash = runCatching { Base64.getDecoder().decode(parts[4]) }.getOrNull() ?: return null
        if (salt.isEmpty() || hash.isEmpty() || algorithm.isEmpty() || iterations <= 0) return null
        return PinData(hash, salt, iterations, algorithm)
    }
}
