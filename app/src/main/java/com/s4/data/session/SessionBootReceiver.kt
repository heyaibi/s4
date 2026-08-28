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
 * Re-arms the per-session expiry alarms after a reboot (alarms do not survive
 * device restart), and sweeps any sessions that expired while the device was
 * off. Without this, a session saved just before a reboot would otherwise keep
 * its encrypted blob on disk until the next app open.
 */
class SessionBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SessionRepository(context).apply {
            sweepExpired()
            rescheduleAll()
        }
    }
}
