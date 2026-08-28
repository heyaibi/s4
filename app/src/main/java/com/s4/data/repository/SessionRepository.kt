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
import com.s4.data.session.SessionExpiryAlarm
import com.s4.data.session.SessionStore
import com.s4.model.StampingSession

/**
 * Facade over the persisted metal-stamping sessions. Sessions are kept in their
 * own SharedPreferences file, encrypted under the Android Keystore (same
 * crypto as the PIN verifier), addressed by a short paper-written code, and
 * expire after [EXPIRE_AFTER_MILLIS].
 *
 * Expiry is two-layered: the store's lazy expiry makes an expired session
 * unreadable (and sweeps it) the moment the app touches it — the actual
 * guarantee — while [SessionExpiryAlarm] + [SessionBootReceiver] scrub the
 * encrypted bytes from disk near the deadline even if the app is never opened.
 */
class SessionRepository(
    private val prefs: SharedPreferences,
    private val expiryAlarm: SessionExpiryAlarm? = null,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        SessionExpiryAlarm(context),
    )

    companion object {
        private const val PREFS_NAME = "s4_stamp_prefs"

        /** One week — a saved session is never stored on disk for longer. */
        const val EXPIRE_AFTER_MILLIS = SessionStore.EXPIRE_AFTER_MILLIS
    }

    private val store = SessionStore(prefs, ProtectedPrefsStore(prefs))

    /**
     * Saves [session] under a fresh code and returns it, or null on failure.
     * Arms the background scrub alarm for the session's expiry.
     */
    fun save(session: StampingSession): String? {
        val code = store.save(session)
        if (code != null) {
            expiryAlarm?.schedule(code, session.createdAt + EXPIRE_AFTER_MILLIS)
        }
        return code
    }

    /** Loads the session for [code], or null when unknown/expired/unreadable. */
    fun load(code: String): StampingSession? = store.load(code)

    /** True when a record exists under [code] (readable or not). */
    fun exists(code: String): Boolean = store.exists(code)

    /**
     * Deletes the session under [code] and disarms its expiry alarm. Returns
     * false when no record existed.
     */
    fun delete(code: String): Boolean {
        expiryAlarm?.cancel(code)
        return store.delete(code)
    }

    /** All known live, unexpired session codes (expired ones are swept). */
    fun codes(): List<String> = store.codes()

    /** Physically deletes every session past its expiry. */
    fun sweepExpired() = store.sweepExpired()

    /**
     * Re-arms the expiry alarm for every live session (used after a reboot,
     * when the OS has cleared all alarms).
     */
    fun rescheduleAll() {
        val alarm = expiryAlarm ?: return
        for (code in codes()) {
            val session = store.load(code) ?: continue
            alarm.schedule(code, session.createdAt + EXPIRE_AFTER_MILLIS)
        }
    }
}
