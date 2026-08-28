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

package com.s4.ui.pin

import com.s4.data.repository.PinRepository

internal sealed class PinGateDecision {
    data object NoPinConfigured : PinGateDecision()
    data object PinUnreadable : PinGateDecision()
    data class Verify(
        val expectedHash: ByteArray,
        val salt: ByteArray,
        val iterations: Int,
        val algorithm: String,
    ) : PinGateDecision()
}

internal fun resolvePinGate(repository: PinRepository): PinGateDecision {
    if (!repository.isPinSet()) return PinGateDecision.NoPinConfigured
    val pinData = repository.getPinData() ?: return PinGateDecision.PinUnreadable
    return PinGateDecision.Verify(
        expectedHash = pinData.hash,
        salt = pinData.salt,
        iterations = pinData.iterations,
        algorithm = pinData.algorithm,
    )
}

internal sealed class AuthPinSubmitDecision {
    data object Unlock : AuthPinSubmitDecision()
    data object IncorrectPin : AuthPinSubmitDecision()
    data object NoPinConfigured : AuthPinSubmitDecision()
    data object PinUnreadable : AuthPinSubmitDecision()
}

internal fun decideAuthPinSubmit(
    repository: PinRepository,
    verifyPin: (String, ByteArray, ByteArray, Int, String) -> Boolean,
    typedPin: String,
): AuthPinSubmitDecision {
    return when (val decision = resolvePinGate(repository)) {
        is PinGateDecision.NoPinConfigured -> AuthPinSubmitDecision.NoPinConfigured
        is PinGateDecision.PinUnreadable -> AuthPinSubmitDecision.PinUnreadable
        is PinGateDecision.Verify ->
            if (verifyPin(typedPin, decision.salt, decision.expectedHash, decision.iterations, decision.algorithm)) {
                AuthPinSubmitDecision.Unlock
            } else {
                AuthPinSubmitDecision.IncorrectPin
            }
    }
}
