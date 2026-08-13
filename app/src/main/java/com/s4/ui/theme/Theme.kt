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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Color schemes — static (no dynamic color).  Dynamic color is disabled so the
// brand identity is preserved regardless of the user's system wallpaper.
// ─────────────────────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary                = DarkPrimary,           // Indigo400 #818CF8
    onPrimary              = Color(0xFF0E0F1A),
    primaryContainer       = Indigo900,             // #1E1B4B
    onPrimaryContainer     = Indigo100,             // #E0E7FF
    secondary              = DarkSecondary,
    onSecondary            = Color(0xFF0E0F1A),
    secondaryContainer     = Color(0xFF222440),
    onSecondaryContainer   = Color(0xFFC8CCEC),
    tertiary               = DarkTertiary,          // Teal400
    onTertiary             = Teal900,
    tertiaryContainer      = Color(0xFF0A2C2A),
    onTertiaryContainer    = Color(0xFFB2E8E3),
    error                  = DarkError,
    onError                = Color(0xFF1A0700),
    errorContainer         = Color(0xFF5C1A12),
    onErrorContainer       = Color(0xFFFFDAD6),
    background             = DarkBg,
    onBackground           = DarkOnBg,
    surface                = DarkSurface,
    onSurface              = DarkOnBg,
    surfaceVariant         = DarkSurfaceVariant,
    onSurfaceVariant       = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow    = DarkSurfaceContainerLow,
    surfaceContainer       = DarkSurfaceContainer,
    surfaceContainerHigh   = DarkSurfaceContainerHigh,
    surfaceContainerHighest= DarkSurfaceContainerHighest,
    outline                = DarkOutline,
    outlineVariant         = DarkOutlineVariant,
    inverseSurface         = DarkOnBg,
    inverseOnSurface       = DarkBg,
    inversePrimary         = Indigo600,
    scrim                  = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary                = LightPrimary,          // Indigo500 #6366F1
    onPrimary              = Color.White,
    primaryContainer       = Indigo100,             // #E0E7FF
    onPrimaryContainer     = Indigo900,
    secondary              = LightSecondary,
    onSecondary            = Color.White,
    secondaryContainer     = Color(0xFFE4E5F4),
    onSecondaryContainer   = Color(0xFF1A1C35),
    tertiary               = LightTertiary,
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFCCFBF1),
    onTertiaryContainer    = Teal900,
    error                  = LightError,
    onError                = Color.White,
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),
    background             = LightBg,
    onBackground           = LightOnBg,
    surface                = LightSurface,
    onSurface              = LightOnBg,
    surfaceVariant         = LightSurfaceVariant,
    onSurfaceVariant       = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow    = LightSurfaceContainerLow,
    surfaceContainer       = LightSurfaceContainer,
    surfaceContainerHigh   = LightSurfaceContainerHigh,
    surfaceContainerHighest= LightSurfaceContainerHighest,
    outline                = LightOutline,
    outlineVariant         = LightOutlineVariant,
    inverseSurface         = Color(0xFF1F2230),
    inverseOnSurface       = LightBg,
    inversePrimary         = Indigo400,
    scrim                  = Color(0xFF0E0F1A),
)

/**
 * S4 theme. Dynamic colour is intentionally disabled — the brand identity
 * (indigo primary, deep-space dark, warm-white light) must be consistent
 * across all devices and wallpapers for a security-critical app.
 */
@Composable
fun S4Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = S4Typography,
        shapes      = S4Shapes,
        content     = content,
    )
}
