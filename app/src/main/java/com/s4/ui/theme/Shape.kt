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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * S4 shapes — slightly softer than the old hairline look, but still intentional
 * and precise. Large containers get 16dp, cards and inputs 10dp, chips 6dp.
 * Nothing exceeds 20dp — no pill blobs.
 */
val S4Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),    // suggestion chips, small badges
    small      = RoundedCornerShape(10.dp),   // buttons, text fields, small cards
    medium     = RoundedCornerShape(12.dp),   // cards, dialogs
    large      = RoundedCornerShape(16.dp),   // bottom sheets, large panels
    extraLarge = RoundedCornerShape(20.dp),   // drawers (unused currently)
)
