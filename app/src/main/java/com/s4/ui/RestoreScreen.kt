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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s4.R
import com.s4.bip39.Bip39
import com.s4.crypto.Slip39
import com.s4.crypto.Slip39Exception
import com.s4.crypto.Slip39Wordlist
import com.s4.ui.components.S4HeaderBar
import com.s4.ui.theme.MonoMeta
import com.s4.ui.theme.RobotoMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A successfully restored wallet: seed words + verification fingerprint. */
data class RestoreResult(
    val words: List<String>,
    val fingerprint: String,
    val passphraseUsed: Boolean,
)

@Composable
fun RestoreScreen(
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit = {},
    initialShares: String = "",
    initialPassphrase: String = "",
) {
    // Share/passphrase fields use plain `remember` (not `rememberSaveable`) so
    // the secret text never lands in system-managed saved-state bundles.
    var input by remember { mutableStateOf(initialShares) }
    LaunchedEffect(initialShares) {
        if (initialShares != input) {
            input = initialShares
        }
    }
    var passphrase by remember { mutableStateOf(initialPassphrase) }
    var passphraseVisible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fingerprintPrefix = stringResource(R.string.fingerprint_prefix)
    val fingerprintWithPassphrasePrefix = stringResource(R.string.fingerprint_with_passphrase_prefix)
    val errNotEnoughShares = stringResource(R.string.restore_err_not_enough)
    val errMismatchedSet = stringResource(R.string.restore_err_mismatch)
    val errBadChecksum = stringResource(R.string.restore_err_checksum)
    val errTooFewWords = stringResource(R.string.restore_err_too_few_words)
    val errFallback = stringResource(R.string.restore_err_fallback)
    val errInvalidInput = stringResource(R.string.restore_err_invalid_input)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        S4HeaderBar(onSettingsClick = onOpenSettings)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ── Header ─────────────────────────────────────────────────────────
            ScreenHeader(
                title    = stringResource(R.string.restore_title),
                subtitle = stringResource(R.string.restore_subtitle),
            )

        // ── Share input ────────────────────────────────────────────────────
        ShareInput(
            value         = input,
            onValueChange = {
                input  = it
                error  = null
                result = null
            },
        )

        // ── Passphrase ─────────────────────────────────────────────────────
        PassphraseField(
            value           = passphrase,
            visible         = passphraseVisible,
            onValueChange   = { passphrase = it; error = null; result = null },
            onToggleVisible = { passphraseVisible = !passphraseVisible },
            modifier        = Modifier.fillMaxWidth(),
        )

        // ── Error ──────────────────────────────────────────────────────────
        error?.let { ErrorBanner(it) }

        // ── Result card ────────────────────────────────────────────────────
        result?.let { res ->
            SectionCard(eyebrow = stringResource(R.string.recovered_eyebrow)) {
                Text(
                    text  = res.words.joinToString(" "),
                    style = MonoMeta.data,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = buildAnnotatedString {
                        append(
                            if (res.passphraseUsed) fingerprintWithPassphrasePrefix else fingerprintPrefix,
                        )
                        withStyle(
                            SpanStyle(
                                fontFamily    = RobotoMono,
                                fontWeight    = FontWeight.Bold,
                                color         = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.0.sp,
                            ),
                        ) { append(res.fingerprint) }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    stringResource(
                        if (res.passphraseUsed) R.string.fingerprint_verify_passphrase
                        else R.string.fingerprint_verify
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── CTA ────────────────────────────────────────────────────────────
        ActionButton(
            label        = stringResource(R.string.restore_wallet),
            loadingLabel = stringResource(R.string.restoring),
            busy         = busy,
            onClick      = {
                busy   = true
                error  = null
                result = null
                scope.launch {
                    try {
                        val candidates = parseShares(input)
                        val entropy = withContext(Dispatchers.Default) { combineFirst(candidates) }
                        val words    = Bip39.entropyToMnemonic(entropy)
                        val fp       = withContext(Dispatchers.Default) {
                            Bip39.fingerprint(Bip39.deriveSeed(words, passphrase))
                        }
                        result = RestoreResult(words = words, fingerprint = fp, passphraseUsed = passphrase.isNotEmpty())
                    } catch (e: Slip39Exception) {
                        error = when (e.code) {
                            ERROR_NOT_ENOUGH_SHARES -> errNotEnoughShares
                            ERROR_MISMATCHED_SET -> errMismatchedSet
                            ERROR_BAD_CHECKSUM -> errBadChecksum
                            ERROR_NOT_ENOUGH_WORDS -> errTooFewWords
                            else -> e.message ?: errFallback
                        }
                    } catch (e: IllegalArgumentException) {
                        error = e.message ?: errInvalidInput
                    } catch (e: Exception) {
                        error = e.message ?: errFallback
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("restoreButton"),
        )

        TextButton(
            onClick  = onOpenGuide,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("openGuideRestore"),
        ) {
            Text(
                stringResource(R.string.recovery_guide),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// ShareInput
// ─────────────────────────────────────────────────────────────────────────────

/** Multi-line share input with SLIP-39 word suggestions for the word being typed. */
@Composable
private fun ShareInput(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var textFieldValue by remember(value) {
            mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
        }

        val typedCount = remember(value) {
            value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        }
        val cursor = textFieldValue.selection.end
        val currentWord = remember(value, cursor) { currentTypedWord(value, cursor) }
        val suggestions = remember(currentWord) {
            if (currentWord.isEmpty()) emptyList()
            else Slip39Wordlist.words.filter { it.startsWith(currentWord) }.take(4)
        }

        OutlinedTextField(
            value          = textFieldValue,
            onValueChange  = {
                textFieldValue = it
                if (it.text != value) {
                    onValueChange(it.text)
                }
            },
            label          = { Text(stringResource(R.string.shares_label)) },
            placeholder    = { Text(stringResource(R.string.shares_placeholder)) },
            supportingText = {
                Text(pluralStringResource(R.plurals.shares_typed, typedCount, typedCount))
            },
            minLines       = 6,
            shape          = MaterialTheme.shapes.small,
            colors         = s4TextFieldColors(),
            modifier       = Modifier
                .fillMaxWidth()
                .testTag("sharesInput"),
        )

        if (suggestions.isNotEmpty()) {
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { word ->
                    val completed = completeWord(value, cursor, word)
                    SuggestionChip(
                        word    = word,
                        onClick = {
                            val newTfv = TextFieldValue(completed.text, selection = TextRange(completed.cursor))
                            textFieldValue = newTfv
                            onValueChange(completed.text)
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Splits the pasted text into candidate share-sets. SLIP-39 share length
 * depends on the secret size (20/23/27/30/33 words), so every legal length that
 * divides the total word count is tried; the native decoder enforces the real
 * length via the RS1024 checksum. Ambiguous totals (e.g. 60 words = 3×20 or
 * 2×30) are resolved by trying each candidate in [combineFirst].
 */
private fun parseShares(input: String): List<List<String>> {
    val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    require(words.isNotEmpty()) { "enter at least one share" }
    val lengths = LEGAL_SHARE_WORDS.filter { words.size % it == 0 }
    require(lengths.isNotEmpty()) {
        "SLIP-39 shares are 20, 23, 27, 30, or 33 words each — you typed ${words.size} words"
    }
    return lengths.sorted().map { words.chunked(it).map { chunk -> chunk.joinToString(" ") } }
}

/** Combines the first candidate share-set that the native decoder accepts. */
private fun combineFirst(candidates: List<List<String>>): ByteArray {
    var lastError: IllegalArgumentException? = null
    for (shares in candidates) {
        try {
            return Slip39.combine(shares)
        } catch (e: Slip39Exception) {
            if (lastError !is Slip39Exception) {
                lastError = e
            }
        } catch (e: IllegalArgumentException) {
            if (lastError == null) {
                lastError = e
            }
        }
    }
    throw lastError ?: IllegalArgumentException("invalid shares")
}

/** Legal SLIP-39 share lengths in words (7 metadata + ceil(bits*8/10)). */
private val LEGAL_SHARE_WORDS = listOf(20, 23, 27, 30, 33)
private const val ERROR_NOT_ENOUGH_SHARES = -12
private const val ERROR_MISMATCHED_SET   = -9
private const val ERROR_BAD_CHECKSUM     = -2
// -1 is ERROR_NOT_ENOUGH_MNEMONIC_WORDS (unknown words never reach this mapping:
// the JNI pre-validates wordlist membership and throws IllegalArgumentException).
private const val ERROR_NOT_ENOUGH_WORDS = -1
