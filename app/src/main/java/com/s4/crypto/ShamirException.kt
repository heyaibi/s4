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

package com.s4.crypto

/**
 * Thrown when the native bc-shamir implementation reports a failure.
 *
 * [code] is one of the negative error codes from `shamir-constants.h`
 * (e.g. checksum failure when a share is wrong, or interpolation failure).
 */
class ShamirException(val code: Int, message: String) : IllegalArgumentException(message)
