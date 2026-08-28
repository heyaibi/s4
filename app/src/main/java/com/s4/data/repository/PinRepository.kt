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

import android.content.Context
import android.content.SharedPreferences
import com.s4.data.crypto.PrefsCrypto

/**
 * Facade over PIN credential material and lockout state.
 */
class PinRepository(
    private val prefs: SharedPreferences,
    crypto: PrefsCrypto? = null,
    elapsedRealtimeProvider: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREFS_NAME = "s4_secure_prefs"
    }

    private val store = ProtectedPrefsStore(prefs, crypto)
    private val clock = MonotonicClock(prefs, elapsedRealtimeProvider)
    private val pinStore = PinStore(prefs, store, clock)

    fun isPinSet(): Boolean = pinStore.isPinSet()

    fun isPinUsable(): Boolean {
        if (!isPinSet()) return false
        return getPinData() != null
    }

    fun savePin(pinHash: ByteArray, salt: ByteArray, iterations: Int, algorithm: String): Boolean =
        pinStore.savePin(pinHash, salt, iterations, algorithm)

    fun clearPin() = pinStore.clearPin()

    fun getPinData(): PinData? = pinStore.getPinData()

    fun getPinFailedAttempts(): Int = pinStore.getPinFailedAttempts()

    fun incrementPinFailedAttempts(): Int = pinStore.incrementPinFailedAttempts()

    fun resetPinFailedAttempts() = pinStore.resetPinFailedAttempts()

    fun getMonotonicNow(): Long = clock.now()

    fun getPinLockoutUntil(): Long = pinStore.getPinLockoutUntil()

    fun setPinLockoutUntil(deadline: Long) = pinStore.setPinLockoutUntil(deadline)

    fun getPinLockoutRemainingMs(): Long = pinStore.getPinLockoutRemainingMs()

    fun consumeTamperFlag(): Boolean = store.consumeTamperFlag()
}
