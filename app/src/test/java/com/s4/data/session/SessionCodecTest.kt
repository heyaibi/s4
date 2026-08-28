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

import com.s4.model.StampingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecTest {

    private val session = StampingSession(
        threshold = 3,
        shareCount = 6,
        shares = listOf(
            "academic acid acne adjust again agency album alert alice all along already always " +
                "amber amount analysis analyst anatomy animal annual antenna anyway appeal apple " +
                "approve april apron area",
            "adjust again agency album alert alice all along already always amber amount analysis " +
                "analyst anatomy animal annual antenna anyway appeal apple approve april apron area " +
                "army aroma",
        ),
        entropyHex = "1a2b3c4d5e6f708192a3b4c5d6e7f890112233445566778899aabbccddeeff0011",
        seedWordCount = 24,
        fingerprint = "0123456789abcdef",
        passphraseUsed = false,
        passphraseLocation = "",
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun `encode then decode round-trips all fields`() {
        val record = SessionCodec.encode(session)
        val decoded = SessionCodec.decode(record)

        assertEquals(session, decoded)
    }

    @Test
    fun `round-trips a passphrase session with a location`() {
        val withPassphrase = session.copy(
            passphraseUsed = true,
            passphraseLocation = "in a safe deposit box",
            shares = listOf("academic acid acne adjust"),
            threshold = 1,
            shareCount = 2,
            seedWordCount = 12,
            entropyHex = "ab",
            fingerprint = "fedcba9876543210",
        )

        assertEquals(withPassphrase, SessionCodec.decode(SessionCodec.encode(withPassphrase)))
    }

    @Test
    fun `single-share session round-trips`() {
        val single = session.copy(threshold = 1, shareCount = 1, shares = listOf(session.shares[0]))

        assertEquals(single, SessionCodec.decode(SessionCodec.encode(single)))
    }

    @Test
    fun `max 16-share session round-trips`() {
        val max = session.copy(threshold = 16, shareCount = 16, shares = (1..16).map { session.shares[0] })

        assertEquals(max, SessionCodec.decode(SessionCodec.encode(max)))
    }

    @Test
    fun `decode returns null for an unknown version`() {
        val record = SessionCodec.encode(session).replaceFirst("v1", "v2")

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for a record with the wrong number of fields`() {
        // Regression guard for the v1 record being 10 colon-separated parts
        // (version + 9 fields) — it was once validated against 9.
        val record = SessionCodec.encode(session)
        assertEquals(10, record.split(':').size)

        val truncated = record.substringBeforeLast(':')
        assertNull(SessionCodec.decode(truncated))
    }

    @Test
    fun `decode returns null for a share count above the maximum`() {
        val record = SessionCodec.encode(session.copy(threshold = 17, shareCount = 17))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for a threshold above the maximum`() {
        val record = SessionCodec.encode(session.copy(threshold = 20, shareCount = 20))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null when share count is below threshold`() {
        val record = SessionCodec.encode(session.copy(threshold = 5, shareCount = 3))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for a zero threshold`() {
        val record = SessionCodec.encode(session.copy(threshold = 0, shareCount = 1))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for empty shares`() {
        val record = SessionCodec.encode(session.copy(shares = emptyList()))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for an invalid seed word count`() {
        val record = SessionCodec.encode(session.copy(seedWordCount = 11))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for empty entropy`() {
        val record = SessionCodec.encode(session.copy(entropyHex = ""))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for an empty fingerprint`() {
        val record = SessionCodec.encode(session.copy(fingerprint = ""))

        assertNull(SessionCodec.decode(record))
    }

    @Test
    fun `decode returns null for a non-base64 field`() {
        val parts = SessionCodec.encode(session).split(':')
        val tampered = (listOf(parts[0]) + listOf(parts[1].replaceFirstChar { '!' }) + parts.drop(2))
            .joinToString(":")

        assertNull(SessionCodec.decode(tampered))
    }

    @Test
    fun `decode returns null for a non-numeric threshold field`() {
        val parts = SessionCodec.encode(session).split(':')
        val tampered = (listOf(parts[0]) + listOf("bm90YS1udW1iZXI=") + parts.drop(2)).joinToString(":")

        assertNull(SessionCodec.decode(tampered))
    }

    @Test
    fun `decode returns null for garbage input`() {
        assertNull(SessionCodec.decode("not a record at all"))
        assertNull(SessionCodec.decode(""))
    }

    @Test
    fun `encoded record contains no colons inside the base64 fields`() {
        val record = SessionCodec.encode(session)

        // Every field is base64, so ':' only ever appears as the separator.
        assertEquals(10, record.split(':').size)
        assertTrue(record.startsWith("v1:"))
    }
}