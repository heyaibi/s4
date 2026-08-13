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
 * Thrown when the native bc-slip39 implementation reports a failure.
 *
 * [code] is one of the negative error codes from `slip39-errors.h`
 * (e.g. `ERROR_INVALID_MNEMONIC_CHECKSUM` when a share is corrupted,
 * `ERROR_INVALID_SHARD_SET` when shares come from different wallets,
 * or `ERROR_NOT_ENOUGH_MEMBER_SHARDS` when too few shares are supplied).
 * [message] is a human-readable description of the failure.
 */
class Slip39Exception(val code: Int, message: String) : IllegalArgumentException(message) {

    companion object {
        /** Maps a negative bc-slip39 error code to a human-readable message. */
        fun describe(code: Int): String = when (code) {
            ERROR_NOT_ENOUGH_MNEMONIC_WORDS -> "not enough mnemonic words"
            ERROR_INVALID_MNEMONIC_CHECKSUM -> "invalid mnemonic checksum: one or more shares are incorrect"
            ERROR_SECRET_TOO_SHORT -> "secret too short"
            ERROR_INVALID_GROUP_THRESHOLD -> "invalid group threshold"
            ERROR_INVALID_SINGLETON_MEMBER -> "invalid singleton member"
            ERROR_INSUFFICIENT_SPACE -> "insufficient space"
            ERROR_INVALID_SECRET_LENGTH -> "invalid secret length"
            ERROR_INVALID_PASSPHRASE -> "invalid passphrase"
            ERROR_INVALID_SHARD_SET -> "invalid shard set: shares are not from the same wallet"
            ERROR_EMPTY_MNEMONIC_SET -> "empty mnemonic set"
            ERROR_DUPLICATE_MEMBER_INDEX -> "duplicate member index"
            ERROR_NOT_ENOUGH_MEMBER_SHARDS -> "not enough member shares"
            ERROR_INVALID_MEMBER_THRESHOLD -> "invalid member threshold"
            ERROR_INVALID_PADDING -> "invalid padding"
            ERROR_NOT_ENOUGH_GROUPS -> "not enough groups"
            ERROR_INVALID_SHARD_BUFFER -> "invalid shard buffer"
            else -> "unknown SLIP-39 error"
        }

        private const val ERROR_NOT_ENOUGH_MNEMONIC_WORDS = -1
        private const val ERROR_INVALID_MNEMONIC_CHECKSUM = -2
        private const val ERROR_SECRET_TOO_SHORT = -3
        private const val ERROR_INVALID_GROUP_THRESHOLD = -4
        private const val ERROR_INVALID_SINGLETON_MEMBER = -5
        private const val ERROR_INSUFFICIENT_SPACE = -6
        private const val ERROR_INVALID_SECRET_LENGTH = -7
        private const val ERROR_INVALID_PASSPHRASE = -8
        private const val ERROR_INVALID_SHARD_SET = -9
        private const val ERROR_EMPTY_MNEMONIC_SET = -10
        private const val ERROR_DUPLICATE_MEMBER_INDEX = -11
        private const val ERROR_NOT_ENOUGH_MEMBER_SHARDS = -12
        private const val ERROR_INVALID_MEMBER_THRESHOLD = -13
        private const val ERROR_INVALID_PADDING = -14
        private const val ERROR_NOT_ENOUGH_GROUPS = -15
        private const val ERROR_INVALID_SHARD_BUFFER = -16
    }
}
