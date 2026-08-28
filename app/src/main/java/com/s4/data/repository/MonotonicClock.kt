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
import android.os.SystemClock
import androidx.core.content.edit

internal class MonotonicClock(
    private val prefs: SharedPreferences,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private companion object {
        const val KEY_ANCHOR = "monotonic_clock_anchor"
        const val KEY_ANCHOR_ELAPSED = "monotonic_clock_anchor_elapsed"
    }

    private var anchor = 0L
    private var anchorElapsed = 0L

    fun now(): Long {
        val elapsed = elapsedRealtimeProvider()
        val (a, e) = effectiveBase(elapsed)
        anchor = a
        anchorElapsed = e
        return a + (elapsed - e)
    }

    fun persistNow() {
        val elapsed = elapsedRealtimeProvider()
        val (a, e) = effectiveBase(elapsed)
        val next = a + (elapsed - e)
        anchor = next
        anchorElapsed = elapsed
        prefs.edit {
            putLong(KEY_ANCHOR, next)
            putLong(KEY_ANCHOR_ELAPSED, elapsed)
        }
    }

    private fun effectiveBase(elapsed: Long): Pair<Long, Long> {
        var effAnchor = anchor
        var effElapsed = anchorElapsed
        val persistedAnchor = prefs.getLong(KEY_ANCHOR, 0L)
        if (persistedAnchor > effAnchor) {
            effAnchor = persistedAnchor
            effElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED, 0L)
        }
        if (elapsed < effElapsed) effElapsed = elapsed
        return effAnchor to effElapsed
    }
}
