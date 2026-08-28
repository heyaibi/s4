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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s4.data.crypto.PinManager
import com.s4.data.repository.PinRepository
import com.s4.ui.ErrorBanner
import com.s4.ui.components.S4HeaderBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PinManagementScreen(
    repository: PinRepository,
    onBack: () -> Unit,
) {
    var newPinText by remember { mutableStateOf("") }
    var confirmPinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isPinSet = repository.isPinSet()

    Scaffold(
        topBar = { S4HeaderBar(showSettings = false, onSettingsClick = {}) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Filled.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(20.dp).size(32.dp),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (isPinSet) "Change PIN" else "Set PIN",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = if (isPinSet) {
                        "Replace the 6-digit PIN used to protect seed material."
                    } else {
                        "Set a 6-digit PIN to protect seed material."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = newPinText,
                    onValueChange = { input ->
                        newPinText = input.filter { it.isDigit() }.take(6)
                        errorMessage = ""
                        successMessage = ""
                    },
                    label = { Text("New PIN (6 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = confirmPinText,
                    onValueChange = { input ->
                        confirmPinText = input.filter { it.isDigit() }.take(6)
                        errorMessage = ""
                        successMessage = ""
                    },
                    label = { Text("Confirm new PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage.isNotEmpty()) {
                    ErrorBanner(errorMessage)
                }
            }

            Text(
                text = "Stored as a PBKDF2-HMAC-SHA256 hash (120,000 rounds). Not recoverable — remember it.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            if (successMessage.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = successMessage,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            var showVerifyDialog by remember { mutableStateOf(false) }

            if (showVerifyDialog) {
                PinVerifyDialog(
                    repository = repository,
                    title = "Verify current PIN",
                    description = "Enter your current PIN to change it.",
                    confirmLabel = "Confirm",
                    onDismiss = { showVerifyDialog = false },
                    onVerified = {
                        showVerifyDialog = false
                        // Proceed to save new PIN after verification
                        val newPin = newPinText
                        val pinManager = PinManager()
                        isSubmitting = true
                        scope.launch {
                            val salt = withContext(Dispatchers.Default) { pinManager.generateSalt() }
                            val hash = withContext(Dispatchers.Default) { pinManager.hashPin(newPin, salt) }
                            val saved = withContext(Dispatchers.Default) {
                                pinManager.verifyPin(
                                    newPin,
                                    salt,
                                    hash,
                                    PinManager.DEFAULT_ITERATIONS,
                                    PinManager.DEFAULT_ALGORITHM,
                                )
                            } && repository.savePin(
                                hash,
                                salt,
                                PinManager.DEFAULT_ITERATIONS,
                                PinManager.DEFAULT_ALGORITHM,
                            )
                            isSubmitting = false
                            if (saved) {
                                repository.resetPinFailedAttempts()
                                repository.setPinLockoutUntil(0L)
                                newPinText = ""
                                confirmPinText = ""
                                errorMessage = ""
                                successMessage = "PIN updated successfully"
                            } else {
                                errorMessage = "Could not save PIN. Please try again."
                                successMessage = ""
                            }
                        }
                    },
                )
            }

            com.s4.ui.ActionButton(
                label = if (isPinSet) "Update PIN" else "Set PIN",
                loadingLabel = "Please wait…",
                busy = isSubmitting,
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@ActionButton
                    if (newPinText.length != 6) {
                        errorMessage = "PIN must be exactly 6 digits."
                        successMessage = ""
                    } else if (newPinText != confirmPinText) {
                        errorMessage = "PINs do not match."
                        successMessage = ""
                    } else {
                        if (isPinSet) {
                            // Require current PIN verification before change
                            showVerifyDialog = true
                        } else {
                            val newPin = newPinText
                            val pinManager = PinManager()
                            isSubmitting = true
                            scope.launch {
                                val salt = withContext(Dispatchers.Default) { pinManager.generateSalt() }
                                val hash = withContext(Dispatchers.Default) { pinManager.hashPin(newPin, salt) }
                                val saved = withContext(Dispatchers.Default) {
                                    pinManager.verifyPin(
                                        newPin,
                                        salt,
                                        hash,
                                        PinManager.DEFAULT_ITERATIONS,
                                        PinManager.DEFAULT_ALGORITHM,
                                    )
                                } && repository.savePin(
                                    hash,
                                    salt,
                                    PinManager.DEFAULT_ITERATIONS,
                                    PinManager.DEFAULT_ALGORITHM,
                                )
                                isSubmitting = false
                                if (saved) {
                                    newPinText = ""
                                    confirmPinText = ""
                                    errorMessage = ""
                                    successMessage = "PIN set successfully"
                                } else {
                                    errorMessage = "Could not save PIN. Please try again."
                                    successMessage = ""
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            androidx.compose.material3.TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
