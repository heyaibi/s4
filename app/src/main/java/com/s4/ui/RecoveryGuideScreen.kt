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

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.s4.S4HeaderBar
import com.s4.guide.RecoveryGuide
import com.s4.ui.theme.MonoMeta
import kotlinx.coroutines.launch

/**
 * The Recovery Guide (R3) — the verbatim-copyable artifact the user hand-writes
 * alongside the shares. Rendered with the real T/N + fingerprint from the last
 * split session, or as a blank template when opened with none.
 */
@Composable
fun RecoveryGuideScreen(viewModel: SplitViewModel, onDone: () -> Unit) {
    val session by viewModel.session.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var showCopyWarning by remember { mutableStateOf(false) }

    val text = remember(session) {
        session?.let {
            RecoveryGuide.render(
                params             = it.params,
                seedWordCount      = it.seedWordCount,
                entropyHex         = it.entropyHex,
                fingerprint        = it.fingerprint,
                passphraseUsed     = it.passphraseUsed,
                passphraseLocation = it.passphraseLocation,
            )
        } ?: RecoveryGuide.renderGeneric()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        S4HeaderBar()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Header ─────────────────────────────────────────────────────────
        ScreenHeader(
            title    = "Recovery Guide",
            subtitle = "Copy this and write it by hand next to your shares — " +
                "your beneficiary can recover the wallet with any SLIP-39 + BIP-39 tool, no app needed.",
        )

        // ── Guide text card ────────────────────────────────────────────────
        SectionCard(eyebrow = "Verbatim guide") {
            Text(
                text     = text,
                style    = MonoMeta.data,
                color    = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── Actions ────────────────────────────────────────────────────────
        val copyGuide: () -> Unit = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("S4 recovery guide", text)))
            }
            copied = true
        }

        ActionButton(
            label        = if (copied) "✓  Guide copied" else "Copy guide",
            loadingLabel = "Copying…",
            busy         = false,
            onClick      = {
                // The session-backed guide embeds the wallet's entropy (byte-equivalent
                // of the seed); gate that copy behind an explicit risk confirmation.
                if (session != null) showCopyWarning = true else copyGuide()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (showCopyWarning) {
            ClipboardRiskDialog(
                title = "Copy the guide to the clipboard?",
                message = "This guide embeds the wallet's entropy — byte-equivalent to the seed itself — " +
                    "and is copied to the system clipboard, where it can be read by other apps (keyboards, " +
                    "accessibility tools) and persists after this app closes. Only proceed if you trust this device.",
                onConfirm = {
                    showCopyWarning = false
                    copyGuide()
                },
                onDismiss = { showCopyWarning = false },
            )
        }

        TextButton(
            onClick  = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
}
