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

/**
 * Builds the verbatim-copyable Recovery Guide (plan R3 / Phase 0.5) — the
 * hand-written artifact a beneficiary can follow with any surviving SLIP-39 and
 * BIP-39 tooling, without the app or domain knowledge.
 *
 * Rendered with the user's real T/N, seed word count, entropy hex, fingerprint,
 * and (when a BIP-39 passphrase was used) a slot for where it is kept. The text
 * names only frozen standards and long-lived tools.
 */
object RecoveryGuide {

    private const val BIP39_TOOL = "iancoleman.io/bip39"
    private const val SLIP39_TOOL = "iancoleman.io/slip39"

    /**
     * Renders the guide for a completed split.
     *
     * @param params threshold/share-count the seed was split into.
     * @param seedWordCount BIP-39 word count of the seed (12/15/18/21/24).
     * @param entropyHex the split BIP-39 entropy as hex (16–32 bytes) — the exact
     *   value the beneficiary gets from a SLIP-39 tool, so it is pre-filled.
     * @param fingerprint the SHA-256 fingerprint shown at split/restore.
     * @param passphraseUsed whether the wallet uses a BIP-39 passphrase.
     * @param passphraseLocation where the passphrase is kept (only rendered in
     *   the passphrase variant; may be blank for the user to fill by hand).
     */
    fun render(
        params: SplitParams,
        seedWordCount: Int,
        entropyHex: String,
        fingerprint: String,
        passphraseUsed: Boolean,
        passphraseLocation: String = "",
    ): String = buildString {
        append("RECOVERY GUIDE — keep this with your shares.\n\n")
        append("This wallet's seed was split into ${params.shareCount} shares; any ${params.threshold} are needed. ")
        append("The shares are SLIP-39 mnemonic shares (real words, not hex).\n\n")

        if (passphraseUsed) {
            append("This wallet uses a BIP-39 passphrase. The passphrase is NOT in these shares — it is kept ")
            append("separately (see the note at the end). To control the wallet you need BOTH the seed words ")
            append("(from these shares) and the passphrase.\n\n")
        }

        append("To recover the wallet:\n\n")
        append("1. Enter ${params.threshold} shares into any SLIP-39 tool (e.g. $SLIP39_TOOL, or a Trezor device).\n\n")
        append("2. The result is ${entropyHex.length / 2} bytes of hex. That value IS the BIP-39 entropy.\n\n")
        append("3. Convert it to the $seedWordCount seed words at $BIP39_TOOL (\"Raw Entropy\" tab), or with:\n")
        append("   pip install mnemonic\n")
        append("   python -c \"from mnemonic import Mnemonic; print(Mnemonic('english').to_mnemonic(bytes.fromhex('$entropyHex')))\"\n\n")
        if (passphraseUsed) {
            append("4. Import the $seedWordCount seed words AND the passphrase into any BIP-39 wallet. ")
        } else {
            append("4. Import the $seedWordCount seed words into any BIP-39 wallet — that is the whole wallet. ")
        }
        append("The fingerprint you should see is $fingerprint.\n\n")

        append("The BIP-39 and SLIP-39 wordlists are published and frozen in their official specs.\n")
        if (passphraseUsed) {
            append("\nThe BIP-39 passphrase is a separate secret that is not in these shares. Note here where it is ")
            append("kept: ${passphraseLocation.ifBlank { "____________________" }}. Without it, the seed words alone ")
            append("cannot control this wallet.\n")
        }
    }

    /**
     * Renders the guide with placeholder slots, for when it is opened from the
     * tabs with no active split session. The user fills in T/N and the
     * fingerprint by hand.
     */
    fun renderGeneric(): String = buildString {
        append("RECOVERY GUIDE — keep this with your shares.\n\n")
        append("This wallet's seed was split into **N** shares; any **T** are needed. ")
        append("The shares are SLIP-39 mnemonic shares (real words, not hex).\n\n")
        append("To recover the wallet:\n\n")
        append("1. Enter **T** shares into any SLIP-39 tool (e.g. $SLIP39_TOOL, or a Trezor device).\n\n")
        append("2. The result is 16 or 32 bytes of hex. That value IS the BIP-39 entropy.\n\n")
        append("3. Convert it to the 12 or 24 seed words at $BIP39_TOOL (\"Raw Entropy\" tab), or with:\n")
        append("   pip install mnemonic\n")
        append("   python -c \"from mnemonic import Mnemonic; print(Mnemonic('english').to_mnemonic(bytes.fromhex('<hex>')))\"\n\n")
        append("4. Import the 12 or 24 seed words into any BIP-39 wallet — that is the whole wallet. ")
        append("The fingerprint you should see is <fingerprint>.\n\n")
        append("The BIP-39 and SLIP-39 wordlists are published and frozen in their official specs.\n")
    }
}
