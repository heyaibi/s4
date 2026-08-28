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

package com.s4.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s4.bip39.Bip39
import com.s4.crypto.Slip39
import com.s4.model.SplitParams
import com.s4.model.StampingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The full result of a split, held in memory (never persisted). */
data class SplitSession(
    val params: SplitParams,
    /** SLIP-39 mnemonic shares — one 20/23/27/30/33-word phrase per share. */
    val shares: List<String>,
    /** The split BIP-39 entropy as hex — pre-filled into the Recovery Guide. */
    val entropyHex: String,
    /** BIP-39 seed word count (12/15/18/21/24), derived from the entropy size. */
    val seedWordCount: Int,
    /** SHA-256 fingerprint of the derived seed, shown at split and restore. */
    val fingerprint: String,
    /** Whether a BIP-39 passphrase was used (it is never sharded or stored). */
    val passphraseUsed: Boolean,
    /** Where the user says the passphrase is kept (blank = user fills by hand). */
    val passphraseLocation: String,
)

/**
 * Holds the current split result in memory (never persisted).
 *
 * [split] runs the native split off the main thread and exposes a [SplitSession]
 * (SLIP-39 mnemonic shares + fingerprint + passphrase info), or an [error]
 * message on failure.
 */
class SplitViewModel : ViewModel() {

    private val _session = MutableStateFlow<SplitSession?>(null)
    val session: StateFlow<SplitSession?> = _session.asStateFlow()

    /**
     * The paper-written code of the persisted stamping session the current
     * [session] was saved under (null when the session was never saved, or was
     * wiped). Drives the "saved" state and the "Done stamping" action.
     */
    private val _savedCode = MutableStateFlow<String?>(null)
    val savedCode: StateFlow<String?> = _savedCode.asStateFlow()

    /** True after a split completes, until the results page is shown. */
    private val _pendingNavigation = MutableStateFlow(false)
    val pendingNavigation: StateFlow<Boolean> = _pendingNavigation.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Splits [words] (already validated) into [params.shareCount] SLIP-39
     * mnemonic shares. The [passphrase], if any, is used only to derive the
     * verification fingerprint — it is never stored or sharded.
     */
    fun split(
        params: SplitParams,
        words: List<String>,
        passphrase: String,
        passphraseLocation: String,
    ) {
        _busy.value = true
        _error.value = null
        _session.value = null
        _savedCode.value = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val entropy = Bip39.mnemonicToEntropy(words)
                val shares = Slip39.split(params, entropy)
                val seed = Bip39.deriveSeed(words, passphrase)
                val fingerprint = Bip39.fingerprint(seed)
                _session.value = SplitSession(
                    params = params,
                    shares = shares,
                    entropyHex = entropy.toHex(),
                    seedWordCount = words.size,
                    fingerprint = fingerprint,
                    passphraseUsed = passphrase.isNotEmpty(),
                    passphraseLocation = passphraseLocation,
                )
                _pendingNavigation.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "split failed"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Called once the results page has been shown; disarms the navigation guard. */
    fun onResultsShown() {
        _pendingNavigation.value = false
    }

    /**
     * Marks the current session as persisted under [code] (called after a
     * successful "Save for stamping"). The in-memory session itself is
     * unchanged.
     */
    fun markSessionSaved(code: String) {
        _savedCode.value = code
    }

    /**
     * Loads a persisted stamping session back into memory (the Resume flow),
     * ready to continue stamping.
     */
    fun resumeSession(session: SplitSession, code: String) {
        _session.value = session
        _savedCode.value = code
        _pendingNavigation.value = false
        _error.value = null
    }

    fun dismissResult() {
        _session.value = null
        _savedCode.value = null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** Maps a persisted [StampingSession] back into the in-memory [SplitSession]. */
fun StampingSession.toSplitSession(): SplitSession = SplitSession(
    params = SplitParams(threshold, shareCount),
    shares = shares,
    entropyHex = entropyHex,
    seedWordCount = seedWordCount,
    fingerprint = fingerprint,
    passphraseUsed = passphraseUsed,
    passphraseLocation = passphraseLocation,
)

/** Maps the in-memory [SplitSession] into its persisted form (no passphrase). */
fun SplitSession.toStampingSession(createdAt: Long = System.currentTimeMillis()): StampingSession =
    StampingSession(
        threshold = params.threshold,
        shareCount = params.shareCount,
        shares = shares,
        entropyHex = entropyHex,
        seedWordCount = seedWordCount,
        fingerprint = fingerprint,
        passphraseUsed = passphraseUsed,
        passphraseLocation = passphraseLocation,
        createdAt = createdAt,
    )
