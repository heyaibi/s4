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

package com.s4.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.s4.R

// ─────────────────────────────────────────────────────────────────────────────
// Font families
// ─────────────────────────────────────────────────────────────────────────────

/** Roboto Mono — data, chips, eyebrows, fingerprints, share text. */
val RobotoMono = FontFamily(
    Font(R.font.roboto_mono_400, FontWeight.Normal),
    Font(R.font.roboto_mono_500, FontWeight.Medium),
    Font(R.font.roboto_mono_600, FontWeight.SemiBold),
    Font(R.font.roboto_mono_700, FontWeight.Bold),
)

/**
 * Default system sans — tighter line heights and crisper letter-spacing than
 * the stock M3 defaults for a more intentional editorial feel.
 */
private val Sans = FontFamily.Default

// ─────────────────────────────────────────────────────────────────────────────
// Typography scale
// ─────────────────────────────────────────────────────────────────────────────

val S4Typography = Typography(
    // Used for brand/hero moments — not used yet, kept for future.
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.75).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Screen title (ScreenHeader uses this).
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Mono meta — for the data / eyebrow / chip / fingerprint layer.
// Applied directly (not via MaterialTheme.typography) to keep it distinct.
// ─────────────────────────────────────────────────────────────────────────────

object MonoMeta {
    /** Chip label, slider heading value, top-bar eyebrow. */
    val value = TextStyle(
        fontFamily = RobotoMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
    )

    /** Multi-line data blocks: share mnemonics, guide text, seed words. */
    val data = TextStyle(
        fontFamily = RobotoMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp,
        textAlign = TextAlign.Start,
    )

    /** Fingerprint / hash display — slightly bolder. */
    val fingerprint = TextStyle(
        fontFamily = RobotoMono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.0.sp,
    )
}
