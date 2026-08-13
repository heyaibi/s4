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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s4.R
import com.s4.ui.components.S4HeaderBar
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
    val fingerprintPrefix = stringResource(R.string.fingerprint_prefix)

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
                title = stringResource(R.string.results_title),
                subtitle = session?.let {
                    stringResource(R.string.results_subtitle_format, it.params.threshold, it.shares.size)
                } ?: stringResource(R.string.results_subtitle_placeholder),
            )

        // ── Session metadata ───────────────────────────────────────────────
        session?.let { sess ->
            // Passphrase warning
            if (sess.passphraseUsed) {
                WarningBanner(stringResource(R.string.passphrase_warning))
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
                            append(fingerprintPrefix)
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
            label        = if (copied) stringResource(R.string.copied) else stringResource(R.string.copy_all),
            loadingLabel = stringResource(R.string.copying),
            busy         = false,
            enabled      = copyText != null,
            onClick      = { if (copyText != null) showCopyWarning = true },
            modifier     = Modifier.fillMaxWidth(),
        )

        if (showCopyWarning && copyText != null) {
            ClipboardRiskDialog(
                title    = stringResource(R.string.copy_seed_title),
                message  = stringResource(R.string.copy_seed_message),
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
            Text(
                stringResource(R.string.recovery_guide),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(
            onClick  = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.done),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    text  = stringResource(R.string.share_label_format, index),
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
