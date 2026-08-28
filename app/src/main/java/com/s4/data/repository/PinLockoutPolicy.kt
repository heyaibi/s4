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

import kotlin.math.pow

/**
 * Exponential-backoff lockout policy shared by every PIN entry point so the
 * two can never drift apart.
 *
 * The lockout activates after [MAX_ATTEMPTS] consecutive failures and doubles
 * in length with each further failure: 30s, 1m, 2m, ...
 */
object PinLockoutPolicy {
    const val MAX_ATTEMPTS = 5
    const val BASE_LOCKOUT_MS = 30_000L
    const val MAX_LOCKOUT_MS = 24 * 60 * 60 * 1000L

    fun lockoutMs(attempts: Int): Long {
        if (attempts < MAX_ATTEMPTS) return 0L
        val exponent = (attempts - MAX_ATTEMPTS).coerceAtLeast(0)
        return (BASE_LOCKOUT_MS * 2.0.pow(exponent)).toLong().coerceAtMost(MAX_LOCKOUT_MS)
    }
}
