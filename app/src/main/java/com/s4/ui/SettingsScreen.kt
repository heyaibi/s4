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

package com.s4.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s4.data.repository.PinRepository
import com.s4.data.repository.SessionRepository
import com.s4.model.StampingSession
import com.s4.ui.components.S4HeaderBar
import com.s4.ui.theme.RobotoMono
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    pinRepository: PinRepository,
    sessionRepository: SessionRepository,
    onOpenSession: (String) -> Unit,
    onManagePin: () -> Unit,
    onBack: () -> Unit,
) {
    val loadSessions = {
        sessionRepository.codes().mapNotNull { code ->
            sessionRepository.load(code)?.let { code to it }
        }
    }
    var sessions by remember { mutableStateOf(loadSessions()) }
    var wipeCode by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { S4HeaderBar(showSettings = false, onSettingsClick = {}) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            ScreenHeader(
                title = "Settings",
                subtitle = "Manage app protection and preferences.",
            )

            SectionCard(eyebrow = "Security") {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManagePin),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Change PIN",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "PIN protection is enabled (required)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "A mandatory 6-digit PIN protects seed material when the app opens. After 5 failed attempts, a cooldown is enforced. If you forget the PIN, you must reinstall the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }

            SectionCard(eyebrow = "Saved stamping sessions") {
                if (sessions.isEmpty()) {
                    Text(
                        text = "No saved sessions. Save a split for metal stamping from the results screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.forEach { (code, session) ->
                        SessionRow(
                            code = code,
                            session = session,
                            onOpen = { onOpenSession(code) },
                            onErase = { wipeCode = code },
                        )
                    }
                    Text(
                        text = "Tap a session to view its shares. Each session is erased automatically 7 days after saving. Erasing one here is permanent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (wipeCode != null) {
        AlertDialog(
            onDismissRequest = { wipeCode = null },
            title = { Text("Erase this session?") },
            text = { Text("This permanently deletes the shares of session $wipeCode from this phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionRepository.delete(wipeCode!!)
                        sessions = loadSessions()
                        wipeCode = null
                    },
                    modifier = Modifier.testTag("confirmEraseSession"),
                ) {
                    Text("Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeCode = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** One saved stamping session: its paper code, T/N, expiry date, and actions. */
@Composable
private fun SessionRow(
    code: String,
    session: StampingSession,
    onOpen: () -> Unit,
    onErase: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = RobotoMono,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "any ${session.threshold} of ${session.shareCount} shares",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Expires ${expiryDate(session)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onOpen) {
            Text("Open", color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = onErase) {
            Text("Erase", color = MaterialTheme.colorScheme.error)
        }
    }
}

private val expiryFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private fun expiryDate(session: StampingSession): String =
    Instant.ofEpochMilli(session.createdAt + SessionRepository.EXPIRE_AFTER_MILLIS)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(expiryFormatter)
