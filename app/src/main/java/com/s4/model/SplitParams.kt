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

package com.s4.model

/** Parameters for splitting a secret: `threshold` of `shareCount` shares are required to recover it. */
data class SplitParams(
    val threshold: Int,
    val shareCount: Int,
) {
    init {
        require(threshold in 1..MAX_SHARES) { "threshold must be in 1..$MAX_SHARES" }
        require(shareCount in threshold..MAX_SHARES) { "shareCount must be in $threshold..$MAX_SHARES" }
    }

    companion object {
        const val MAX_SHARES = 16
    }
}
