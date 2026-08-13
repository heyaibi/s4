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

package com.s4.guide

import com.s4.model.SplitParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryGuideTest {

    @Test
    fun render_fillsTNShareCountAndFingerprint() {
        val text = RecoveryGuide.render(
            params = SplitParams(3, 6),
            seedWordCount = 24,
            entropyHex = "aabbccdd",
            fingerprint = "89c4a8ef",
            passphraseUsed = false,
        )

        assertTrue(text.contains("split into 6 shares; any 3 are needed"))
        assertTrue(text.contains("RECOVERY GUIDE — keep this with your shares."))
        assertTrue(text.contains("4 bytes of hex")) // aabbccdd = 4 bytes
        assertTrue(text.contains("the 24 seed words at iancoleman.io/bip39"))
        assertTrue(text.contains("bytes.fromhex('aabbccdd')"))
        assertTrue(text.contains("The fingerprint you should see is 89c4a8ef."))
    }

    @Test
    fun render_passphraseVariant_mentionsSeparateSecretAndLocation() {
        val text = RecoveryGuide.render(
            params = SplitParams(2, 5),
            seedWordCount = 12,
            entropyHex = "0102030405060708090a0b0c0d0e0f",
            fingerprint = "0011aabb",
            passphraseUsed = true,
            passphraseLocation = "in the safe deposit box",
        )

        assertTrue(text.contains("uses a BIP-39 passphrase"))
        assertTrue(text.contains("The passphrase is NOT in these shares"))
        assertTrue(text.contains("Import the 12 seed words AND the passphrase"))
        assertTrue(text.contains("kept: in the safe deposit box"))
        assertTrue(text.contains("Without it, the seed words alone cannot"))
        assertTrue(text.contains("control this wallet."))
    }

    @Test
    fun render_passphraseVariant_blankLocationLeavesFillInLine() {
        val text = RecoveryGuide.render(
            params = SplitParams(3, 6),
            seedWordCount = 24,
            entropyHex = "ff",
            fingerprint = "12345678",
            passphraseUsed = true,
            passphraseLocation = "",
        )
        assertTrue(text.contains("kept: ____________________"))
    }

    @Test
    fun render_noPassphrase_omitsPassphraseParagraphs() {
        val text = RecoveryGuide.render(
            params = SplitParams(3, 6),
            seedWordCount = 24,
            entropyHex = "ff",
            fingerprint = "12345678",
            passphraseUsed = false,
        )
        assertTrue(!text.contains("BIP-39 passphrase"))
        assertTrue(text.contains("Import the 24 seed words into any BIP-39 wallet — that is the whole wallet."))
    }

    @Test
    fun renderGeneric_usesPlaceholders() {
        val text = RecoveryGuide.renderGeneric()
        assertTrue(text.contains("**N** shares; any **T** are needed"))
        assertTrue(text.contains("<fingerprint>"))
        assertTrue(text.contains("iancoleman.io/slip39"))
        assertTrue(text.contains("The result is 16 or 32 bytes of hex"))
        assertTrue(text.contains("Convert it to the 12 or 24 seed words"))
        assertTrue(text.contains("Import the 12 or 24 seed words into any BIP-39 wallet"))
    }
}
