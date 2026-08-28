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

import com.s4.model.SplitParams
import com.s4.model.StampingSession
import java.util.Base64

/**
 * Encodes a [StampingSession] to a single self-contained string (the value
 * stored, Keystore-encrypted, under the session's code key), and back.
 *
 * Format: `v1:<b64 field>:<b64 field>:...`. Every field is base64 so no
 * separator character can appear inside a field, mirroring the record format
 * used by [com.s4.data.repository.PinStore]. [decode] fails closed: any
 * malformation, unknown version, or out-of-range value returns null rather
 * than a partial session.
 */
object SessionCodec {

    private const val VERSION = "v1"
    private const val RECORD_FIELDS = 10

    /** Newline-joins the shares; a share phrase never contains a newline. */
    fun encode(session: StampingSession): String = listOf(
        b64(session.threshold.toString()),
        b64(session.shareCount.toString()),
        b64(session.shares.joinToString("\n")),
        b64(session.entropyHex),
        b64(session.seedWordCount.toString()),
        b64(session.fingerprint),
        b64(session.passphraseUsed.toString()),
        b64(session.passphraseLocation),
        b64(session.createdAt.toString()),
    ).joinToString(":", prefix = "$VERSION:")

    fun decode(record: String): StampingSession? {
        val parts = record.split(':')
        if (parts.size != RECORD_FIELDS || parts[0] != VERSION) return null

        val threshold = parts[1].unb64Int() ?: return null
        val shareCount = parts[2].unb64Int() ?: return null
        if (threshold !in 1..SplitParams.MAX_SHARES ||
            shareCount !in threshold..SplitParams.MAX_SHARES
        ) {
            return null
        }

        val sharesRaw = parts[3].unb64String() ?: return null
        val shares = sharesRaw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (shares.isEmpty()) return null

        val entropyHex = parts[4].unb64String() ?: return null
        if (entropyHex.isEmpty()) return null

        val seedWordCount = parts[5].unb64Int() ?: return null
        if (seedWordCount !in intArrayOf(12, 15, 18, 21, 24)) return null

        val fingerprint = parts[6].unb64String() ?: return null
        if (fingerprint.isEmpty()) return null

        val passphraseUsed = parts[7].unb64Boolean() ?: return null
        val passphraseLocation = parts[8].unb64String() ?: return null
        val createdAt = parts[9].unb64Long() ?: return null

        return StampingSession(
            threshold = threshold,
            shareCount = shareCount,
            shares = shares,
            entropyHex = entropyHex,
            seedWordCount = seedWordCount,
            fingerprint = fingerprint,
            passphraseUsed = passphraseUsed,
            passphraseLocation = passphraseLocation,
            createdAt = createdAt,
        )
    }

    private fun b64(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun String.unb64String(): String? = runCatching {
        String(Base64.getDecoder().decode(this), Charsets.UTF_8)
    }.getOrNull()

    private fun String.unb64Int(): Int? = unb64String()?.toIntOrNull()

    private fun String.unb64Long(): Long? = unb64String()?.toLongOrNull()

    private fun String.unb64Boolean(): Boolean? = when (unb64String()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
