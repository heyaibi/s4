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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules a best-effort one-shot alarm that wakes [SessionExpiryReceiver]
 * around a session's expiry so the encrypted record is physically scrubbed
 * from disk even when the app is not opened.
 *
 * Delivery is deliberately approximate: [AlarmManager.setAndAllowWhileIdle] is
 * inexact (fires within ~15 minutes of the target in idle) and needs no special
 * permission — unlike exact alarms, which are default-denied since Android 14.
 * The real "guarantee" of erase-after-7-days is the store's lazy expiry, which
 * treats an expired session as deleted regardless of when this alarm fires;
 * this class only removes the lingering bytes as soon as the OS allows.
 */
class SessionExpiryAlarm(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Arms the scrub for [code] at [expiresAtMillis] (wall clock). */
    fun schedule(code: String, expiresAtMillis: Long) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiresAtMillis, pendingIntent(code))
    }

    /** Disarms a pending scrub for [code] (e.g. on "Done stamping"). */
    fun cancel(code: String) {
        alarmManager.cancel(pendingIntent(code))
    }

    private fun pendingIntent(code: String): PendingIntent {
        val intent = Intent(context, SessionExpiryReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            SessionCodeGenerator.requestCode(code),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
