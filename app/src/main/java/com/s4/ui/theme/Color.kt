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

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// S4 Design System — Dual-polished color tokens
//
// Philosophy: deep-space dark mode + crisp warm-white light mode.
// Both modes are first-class. The indigo/violet primary has enough contrast
// against both surface treatments; amber is used sparingly for emphasis only.
// ─────────────────────────────────────────────────────────────────────────────

// Brand / accent
val Indigo400  = Color(0xFF818CF8)   // light-mode tint
val Indigo500  = Color(0xFF6366F1)   // brand primary (both modes)
val Indigo600  = Color(0xFF4F46E5)   // dark-mode on-primary
val Indigo100  = Color(0xFFE0E7FF)   // light primary container
val Indigo900  = Color(0xFF1E1B4B)   // dark primary container

// Semantic amber (caution / fingerprint highlight)
val Amber400   = Color(0xFFFBBF24)
val Amber900   = Color(0xFF78350F)

// Semantic teal (success / tertiary)
val Teal400    = Color(0xFF2DD4BF)
val Teal900    = Color(0xFF134E4A)

// ── Dark-mode neutrals  (deep space) ─────────────────────────────────────────
val DarkBg              = Color(0xFF0C0D12)   // near-black canvas
val DarkSurface         = Color(0xFF0C0D12)
val DarkSurfaceVariant  = Color(0xFF1A1C25)   // card / input container
val DarkSurfaceContainerLowest = Color(0xFF080910)
val DarkSurfaceContainerLow    = Color(0xFF111318)
val DarkSurfaceContainer       = Color(0xFF15171F)
val DarkSurfaceContainerHigh   = Color(0xFF1A1C25)
val DarkSurfaceContainerHighest = Color(0xFF1F2230)
val DarkOutline         = Color(0xFF2D3148)   // hairline borders
val DarkOutlineVariant  = Color(0xFF1E2138)
val DarkOnBg            = Color(0xFFF1F2F8)   // near-white text
val DarkOnSurfaceVariant = Color(0xFF8B8FA8)  // muted secondary text
val DarkPrimary         = Indigo400           // 0xFF818CF8
val DarkSecondary       = Color(0xFFA5AACC)
val DarkTertiary        = Teal400
val DarkError           = Color(0xFFFF7B6B)

// ── Light-mode neutrals  (warm white) ────────────────────────────────────────
val LightBg             = Color(0xFFF7F7FA)   // warm cool-white canvas
val LightSurface        = Color(0xFFF7F7FA)
val LightSurfaceVariant = Color(0xFFEEEFF5)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow    = Color(0xFFF2F3F8)
val LightSurfaceContainer       = Color(0xFFECEDF3)
val LightSurfaceContainerHigh   = Color(0xFFE6E7EE)
val LightSurfaceContainerHighest = Color(0xFFE0E1E9)
val LightOutline        = Color(0xFFCACDDF)
val LightOutlineVariant = Color(0xFFD8DAE8)
val LightOnBg           = Color(0xFF0E0F1A)   // near-black text
val LightOnSurfaceVariant = Color(0xFF5A5E7A)
val LightPrimary        = Indigo500           // 0xFF6366F1
val LightSecondary      = Color(0xFF5B5F80)
val LightTertiary       = Color(0xFF0F766E)
val LightError          = Color(0xFFD32F2F)
