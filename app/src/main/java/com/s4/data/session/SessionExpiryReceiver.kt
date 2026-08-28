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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.s4.data.repository.SessionRepository

/**
 * Woken by the per-session expiry alarm to physically scrub any expired
 * stamping sessions from disk even when the app is not open.
 *
 * It deliberately sweeps every expired session (not just the one the alarm was
 * armed for): a session is only deleted once it is verifiably past its 7-day
 * expiry, so a device clock moved forward cannot cause an early wipe.
 */
class SessionExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        SessionRepository(context).sweepExpired()
    }
}
