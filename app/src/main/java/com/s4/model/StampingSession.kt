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

package com.s4.model

/**
 * A split result persisted for the metal-stamping workflow.
 *
 * This is the on-disk form of a [com.s4.ui.SplitSession]: the same fields,
 * plus a [createdAt] timestamp. The BIP-39 passphrase itself is deliberately
 * never persisted — only [passphraseUsed] and [passphraseLocation] (matching
 * the Recovery Guide, which also never stores the passphrase).
 */
data class StampingSession(
    val threshold: Int,
    val shareCount: Int,
    /** SLIP-39 mnemonic shares — one 20/23/27/30/33-word phrase per share. */
    val shares: List<String>,
    /** The split BIP-39 entropy as hex. */
    val entropyHex: String,
    /** BIP-39 seed word count (12/15/18/21/24). */
    val seedWordCount: Int,
    /** SHA-256 fingerprint of the derived seed. */
    val fingerprint: String,
    /** Whether a BIP-39 passphrase was used (it is never stored). */
    val passphraseUsed: Boolean,
    /** Where the user says the passphrase is kept (blank = user fills by hand). */
    val passphraseLocation: String,
    /** Epoch millis when the session was saved. */
    val createdAt: Long,
)
