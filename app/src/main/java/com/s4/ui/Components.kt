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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.s4.R
import com.s4.ui.theme.MonoMeta

// ─────────────────────────────────────────────────────────────────────────────
// ScreenHeader
// ─────────────────────────────────────────────────────────────────────────────

/** Screen hero: title + one-line subtitle. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActionButton
// ─────────────────────────────────────────────────────────────────────────────

/** Primary action button with a busy/loading state. */
@Composable
fun ActionButton(
    label: String,
    loadingLabel: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled && !busy) 1f else 0.5f,
        animationSpec = tween(200),
        label = "buttonAlpha",
    )
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor   = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .height(52.dp)
            .alpha(alpha),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier  = Modifier.size(18.dp),
                color     = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(loadingLabel, style = MaterialTheme.typography.labelLarge)
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ErrorBanner
// ─────────────────────────────────────────────────────────────────────────────

/** Animated inline error message — error-tinted banner. */
@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text.isNotEmpty(),
        enter   = fadeIn(tween(200)) + slideInVertically(tween(200)) { -8 },
        exit    = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = MaterialTheme.colorScheme.errorContainer,
            shape    = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text  = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TextField colours
// ─────────────────────────────────────────────────────────────────────────────

/** Hairline text-field colors — transparent container, single-pixel border. */
@Composable
fun s4TextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor         = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor       = MaterialTheme.colorScheme.outline,
    focusedLabelColor          = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor        = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor           = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor         = MaterialTheme.colorScheme.onSurface,
    cursorColor                = MaterialTheme.colorScheme.primary,
    focusedContainerColor      = Color.Transparent,
    unfocusedContainerColor    = Color.Transparent,
    errorBorderColor           = MaterialTheme.colorScheme.error,
    errorLabelColor            = MaterialTheme.colorScheme.error,
    errorSupportingTextColor   = MaterialTheme.colorScheme.error,
    errorContainerColor        = Color.Transparent,
    unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedSupportingTextColor   = MaterialTheme.colorScheme.onSurfaceVariant,
)

// ─────────────────────────────────────────────────────────────────────────────
// PassphraseField
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Optional BIP-39 passphrase field with a masked/reveal toggle.
 * The passphrase is never stored or sharded by the app.
 */
@Composable
fun PassphraseField(
    value: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        enabled         = enabled,
        label           = { Text(stringResource(R.string.passphrase_label)) },
        placeholder     = { Text(stringResource(R.string.passphrase_placeholder)) },
        supportingText  = { Text(stringResource(R.string.passphrase_supporting)) },
        singleLine      = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon    = {
            TextButton(onClick = onToggleVisible, enabled = enabled) {
                Text(
                    text  = if (visible) stringResource(R.string.hide) else stringResource(R.string.show),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        shape   = MaterialTheme.shapes.small,
        colors  = s4TextFieldColors(),
        modifier = modifier.testTag("passphraseField"),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SuggestionChip
// ─────────────────────────────────────────────────────────────────────────────

/** A tappable word-suggestion pill (BIP-39 or SLIP-39). */
@Composable
fun SuggestionChip(word: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick  = onClick,
        shape    = MaterialTheme.shapes.extraSmall,
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Text(
            text     = word,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style    = MonoMeta.value,
            color    = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InfoRow  (new — used inside cards for labelled data)
// ─────────────────────────────────────────────────────────────────────────────

/** A labelled row: "Label" on the left, `value` on the right with mono treatment. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = value,
            style = MonoMeta.value,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ShareIndexBadge  (new — numbered circle for share cards)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A small filled circle with a share index number — used as a visual anchor
 * on each share card to make scanning and counting shares effortless.
 */
@Composable
fun ShareIndexBadge(index: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = "$index",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SectionCard  (new — wraps a content block with a labelled top-left eyebrow)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A card container with an optional mono-style eyebrow label.
 * Used for the T/N config card, passphrase card, and result cards.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        color    = MaterialTheme.colorScheme.surfaceContainer,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            eyebrow?.let {
                Text(
                    text  = it.uppercase(),
                    style = MonoMeta.value,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WarningBanner
// ─────────────────────────────────────────────────────────────────────────────

/** Amber-tinted informational panel — less severe than ErrorBanner. */
@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color    = MaterialTheme.colorScheme.tertiaryContainer,
        shape    = MaterialTheme.shapes.small,
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ClipboardRiskDialog  (secret-material copy confirmation)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Confirmation dialog shown before copying secret material (the full share set,
 * or a guide embedding the wallet's entropy) to the system clipboard. The
 * clipboard is readable by other apps (IMEs, accessibility services) and
 * persists after this app closes, so the copy is gated behind an explicit
 * "Copy anyway" confirm.
 */
@Composable
fun ClipboardRiskDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick  = onConfirm,
                modifier = Modifier.testTag("confirmClipboardCopy"),
            ) {
                Text(stringResource(R.string.copy_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
