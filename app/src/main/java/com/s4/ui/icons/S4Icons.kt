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

package com.s4.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom navigation icons. Strokes are baked in black so the [Icon] composable
 * tints them via [LocalContentColor] — selected/unselected states follow the
 * theme tokens instead of a hardcoded brand color.
 */
private const val ICON_STROKE_WIDTH = 2f

val SplitIcon: ImageVector = ImageVector.Builder(
    name = "SplitIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ICON_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(16f, 3f)
        lineTo(21f, 3f)
        lineTo(21f, 8f)
        moveTo(4f, 20f)
        lineTo(21f, 3f)
        moveTo(21f, 16f)
        lineTo(21f, 21f)
        lineTo(16f, 21f)
        moveTo(15f, 15f)
        lineTo(21f, 21f)
        moveTo(4f, 4f)
        lineTo(9f, 9f)
    }
}.build()

val RestoreIcon: ImageVector = ImageVector.Builder(
    name = "RestoreIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ICON_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(3f, 12f)
        curveTo(3f, 7.029f, 7.029f, 3f, 12f, 3f)
        curveTo(16.971f, 3f, 21f, 7.029f, 21f, 12f)
        curveTo(21f, 16.971f, 16.971f, 21f, 12f, 21f)
        curveTo(8.5f, 21f, 5.5f, 19f, 4f, 16f)
        moveTo(3f, 8f)
        lineTo(3f, 12f)
        lineTo(7f, 12f)
    }
}.build()
