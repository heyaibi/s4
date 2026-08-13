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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s4.S4HeaderBar
import com.s4.ui.theme.MonoMeta
import com.s4.ui.theme.RobotoMono
import kotlinx.coroutines.launch

/**
 * Full-screen result of a split. Stays on screen until the user explicitly
 * leaves (onDone, system back, or switching tabs) — no accidental dismissal,
 * and the shares are never regenerated while this page is up.
 */
@Composable
fun SplitResultScreen(
    viewModel: SplitViewModel,
    onOpenGuide: () -> Unit,
    onDone: () -> Unit,
) {
    val session = viewModel.session.collectAsState().value
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var showCopyWarning by remember { mutableStateOf(false) }

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
            title    = "Seed shares",
            subtitle = "Keep each share in a separate, secure location. Any " +
                "${session?.params?.threshold ?: "—"} of ${session?.shares?.size ?: "—"} " +
                "restore the wallet. Write them down — S4 never stores them.",
        )

        // ── Session metadata ───────────────────────────────────────────────
        session?.let { sess ->
            // Passphrase warning
            if (sess.passphraseUsed) {
                WarningBanner(
                    "This wallet uses a BIP-39 passphrase. The shares recover only the seed words — " +
                    "the passphrase must be kept separately."
                )
            }

            // Fingerprint strip
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("Fingerprint: ")
                            withStyle(
                                SpanStyle(
                                    fontFamily    = RobotoMono,
                                    fontWeight    = FontWeight.Bold,
                                    color         = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.0.sp,
                                ),
                            ) { append(sess.fingerprint) }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Share cards
            sess.shares.forEachIndexed { index, mnemonic ->
                ShareCard(index = index + 1, mnemonic = mnemonic)
            }
        }

        // ── Actions ────────────────────────────────────────────────────────
        val copyText = session?.shares?.joinToString("\n")
        ActionButton(
            label        = if (copied) "Copied" else "Copy all",
            loadingLabel = "Copying…",
            busy         = false,
            enabled      = copyText != null,
            onClick      = { if (copyText != null) showCopyWarning = true },
            modifier     = Modifier.fillMaxWidth(),
        )

        if (showCopyWarning && copyText != null) {
            ClipboardRiskDialog(
                title = "Copy the full seed to the clipboard?",
                message = "This copies every share — the complete seed — to the system clipboard, " +
                    "where it can be read by other apps (keyboards, accessibility tools) and persists " +
                    "after this app closes. Only proceed if you trust this device.",
                onConfirm = {
                    showCopyWarning = false
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("S4 seed shares", copyText)))
                    }
                    copied = true
                },
                onDismiss = { showCopyWarning = false },
            )
        }

        TextButton(
            onClick  = onOpenGuide,
            enabled  = session != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Recovery Guide", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ─────────────────────────────────────────────────────────────────────────────
// ShareCard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single share card: numbered badge on the left, mnemonic words on the right.
 * The left accent strip uses primaryContainer to create visual scan-ability.
 */
@Composable
private fun ShareCard(index: Int, mnemonic: String) {
    SectionCard {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.Top,
        ) {
            ShareIndexBadge(index = index)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text  = "Share $index",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = mnemonic,
                    style = MonoMeta.data,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
