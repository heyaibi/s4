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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s4.R
import com.s4.bip39.Bip39
import com.s4.crypto.ShareCodec
import com.s4.data.repository.PinRepository
import com.s4.data.repository.SessionRepository
import com.s4.data.session.SessionCodeGenerator
import com.s4.model.SplitParams
import com.s4.ui.components.S4HeaderBar
import com.s4.ui.pin.PinVerifyDialog
import com.s4.ui.theme.MonoMeta
import com.s4.ui.theme.RobotoMono
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SecretInputMode { MNEMONIC, ENTROPY_HEX }

@Composable
fun SplitScreen(
    viewModel: SplitViewModel,
    pinRepository: PinRepository,
    sessionRepository: SessionRepository,
    onSplitComplete: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var inputModeName by rememberSaveable { mutableStateOf(SecretInputMode.MNEMONIC.name) }
    val inputMode = SecretInputMode.valueOf(inputModeName)
    // Seed/entropy/passphrase fields use plain `remember` (not `rememberSaveable`)
    // so the secret text is never written to system-managed saved-state bundles,
    // which can land on disk and survive process death.
    var mnemonic by remember { mutableStateOf(TextFieldValue("")) }
    var entropyHex by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by rememberSaveable { mutableStateOf(false) }
    var passphraseLocation by remember { mutableStateOf("") }
    var shareCount by rememberSaveable { mutableIntStateOf(6) }
    var threshold by rememberSaveable { mutableIntStateOf(3) }

    val busy by viewModel.busy.collectAsState()
    val splitError by viewModel.error.collectAsState()
    val pendingNavigation by viewModel.pendingNavigation.collectAsState()

    val scope = rememberCoroutineScope()
    var showResumeDialog by remember { mutableStateOf(false) }
    var showResumePinDialog by remember { mutableStateOf(false) }
    var resumeCode by remember { mutableStateOf("") }
    var resumeError by remember { mutableStateOf<String?>(null) }
    var isResuming by remember { mutableStateOf(false) }
    val resumeErrNotFound = stringResource(R.string.resume_err_not_found)
    val resumeErrFormat = stringResource(R.string.resume_err_format)

    val resume: (String) -> Unit = { code ->
        scope.launch {
            isResuming = true
            resumeError = null
            val loaded = withContext(Dispatchers.Default) { sessionRepository.load(code) }
            isResuming = false
            if (loaded != null) {
                viewModel.resumeSession(loaded.toSplitSession(), code)
                showResumeDialog = false
                onSplitComplete()
            } else {
                resumeError = resumeErrNotFound
            }
        }
    }

    val submitResume: () -> Unit = {
        val code = SessionCodeGenerator.normalize(resumeCode)
        if (!SessionCodeGenerator.isValidCode(code)) {
            resumeError = resumeErrFormat
        } else if (pinRepository.isPinSet()) {
            showResumePinDialog = true
        } else {
            resume(code)
        }
    }

    LaunchedEffect(pendingNavigation) {
        if (pendingNavigation) {
            viewModel.onResultsShown()
            onSplitComplete()
        }
    }

    val validationError = remember(inputMode, mnemonic.text, entropyHex) {
        validateSecretInput(inputMode, mnemonic.text, entropyHex)
    }
    val hasInput = if (inputMode == SecretInputMode.MNEMONIC) mnemonic.text.isNotBlank() else entropyHex.isNotBlank()
    val showError = validationError != null && hasInput

    val fingerprint = remember(inputMode, mnemonic.text, entropyHex, passphrase) {
        computeFingerprint(inputMode, mnemonic.text, entropyHex, passphrase)
    }

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
            ScreenHeader(
                title    = stringResource(R.string.split_title),
                subtitle = stringResource(R.string.split_subtitle),
            )

            SecretInputSection(
                inputMode         = inputMode,
                mnemonic          = mnemonic,
                entropyHex        = entropyHex,
                showError         = showError,
                validationError   = validationError,
                onInputModeChange = { inputModeName = it.name },
                onMnemonicChange  = { mnemonic = it },
                onEntropyHexChange = { entropyHex = it },
            )

            PassphraseField(
                value           = passphrase,
                visible         = passphraseVisible,
                onValueChange   = { passphrase = it },
                onToggleVisible = { passphraseVisible = !passphraseVisible },
                modifier        = Modifier.fillMaxWidth(),
            )

            if (passphrase.isNotEmpty()) {
                PassphraseDetailSection(
                    fingerprint               = fingerprint,
                    passphraseLocation        = passphraseLocation,
                    onPassphraseLocationChange = { passphraseLocation = it },
                )
            }

            ShareConfigSection(
                shareCount        = shareCount,
                threshold         = threshold,
                onShareCountChange = { value ->
                    shareCount = value.roundToInt()
                    if (threshold > shareCount) threshold = shareCount
                },
                onThresholdChange = { value -> threshold = value.roundToInt() },
            )

            splitError?.let { ErrorBanner(it) }

            ActionButton(
                label        = stringResource(R.string.split_into, shareCount),
                loadingLabel = stringResource(R.string.splitting),
                busy         = busy,
                enabled      = validationError == null,
                onClick      = {
                    if (validationError == null) {
                        val words = decodeWords(inputMode, mnemonic.text, entropyHex)
                        viewModel.split(
                            params             = SplitParams(threshold, shareCount),
                            words              = words,
                            passphrase         = passphrase,
                            passphraseLocation = passphraseLocation,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirmButton"),
            )

            TextButton(
                onClick  = onOpenGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("openGuideSplit"),
            ) {
                Text(
                    stringResource(R.string.recovery_guide),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                onClick  = { showResumeDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resumeButton"),
            ) {
                Text(
                    stringResource(R.string.resume),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showResumeDialog) {
                AlertDialog(
                    onDismissRequest = { showResumeDialog = false },
                    title = { Text(stringResource(R.string.resume_dialog_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = resumeCode,
                                onValueChange = { input ->
                                    resumeCode = input
                                        .uppercase()
                                        .filter { SessionCodeGenerator.isAllowedChar(it) }
                                        .take(SessionCodeGenerator.CODE_LENGTH)
                                    resumeError = null
                                },
                                label = { Text(stringResource(R.string.resume_code_label)) },
                                placeholder = { Text(stringResource(R.string.resume_code_placeholder)) },
                                supportingText = resumeError?.let { error ->
                                    { Text(error, color = MaterialTheme.colorScheme.error) }
                                },
                                isError = resumeError != null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = MaterialTheme.shapes.small,
                                colors = s4TextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resumeCodeInput"),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = submitResume,
                            enabled = !isResuming,
                            modifier = Modifier.testTag("resumeSubmit"),
                        ) {
                            Text(
                                if (isResuming) stringResource(R.string.resuming) else stringResource(R.string.resume_submit),
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResumeDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            if (showResumePinDialog) {
                PinVerifyDialog(
                    repository   = pinRepository,
                    title        = stringResource(R.string.resume_pin_title),
                    description  = stringResource(R.string.resume_pin_description),
                    confirmLabel = stringResource(R.string.resume_submit),
                    onDismiss    = { showResumePinDialog = false },
                    onVerified   = {
                        showResumePinDialog = false
                        resume(SessionCodeGenerator.normalize(resumeCode))
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Secret input section (mnemonic / entropy hex + mode toggle)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecretInputSection(
    inputMode: SecretInputMode,
    mnemonic: TextFieldValue,
    entropyHex: String,
    showError: Boolean,
    validationError: String?,
    onInputModeChange: (SecretInputMode) -> Unit,
    onMnemonicChange: (TextFieldValue) -> Unit,
    onEntropyHexChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (inputMode) {
            SecretInputMode.MNEMONIC -> MnemonicInput(
                value          = mnemonic,
                onValueChange  = onMnemonicChange,
                showError      = showError,
                validationError = validationError,
            )

            SecretInputMode.ENTROPY_HEX -> OutlinedTextField(
                value         = entropyHex,
                onValueChange = onEntropyHexChange,
                label         = { Text(stringResource(R.string.entropy_label)) },
                placeholder   = { Text(stringResource(R.string.entropy_placeholder)) },
                supportingText = {
                    Text(
                        if (showError) validationError.orEmpty()
                        else stringResource(R.string.entropy_supporting)
                    )
                },
                isError    = showError,
                singleLine = true,
                shape      = MaterialTheme.shapes.small,
                colors     = s4TextFieldColors(),
                modifier   = Modifier
                    .fillMaxWidth()
                    .testTag("entropyInput"),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    onInputModeChange(
                        if (inputMode == SecretInputMode.MNEMONIC) SecretInputMode.ENTROPY_HEX
                        else SecretInputMode.MNEMONIC
                    )
                },
                modifier = Modifier.testTag("toggleInputMode"),
            ) {
                Text(
                    text = stringResource(
                        if (inputMode == SecretInputMode.MNEMONIC) R.string.use_entropy_hex
                        else R.string.use_seed_phrase
                    ),
                    style = MonoMeta.value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Passphrase detail card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PassphraseDetailSection(
    fingerprint: String?,
    passphraseLocation: String,
    onPassphraseLocationChange: (String) -> Unit,
) {
    val fingerprintPrefix = stringResource(R.string.fingerprint_prefix)
    SectionCard(eyebrow = stringResource(R.string.passphrase_details)) {
        fingerprint?.let { fp ->
            Text(
                text = buildAnnotatedString {
                    append(fingerprintPrefix)
                    withStyle(
                        SpanStyle(
                            fontFamily    = RobotoMono,
                            fontWeight    = FontWeight.Bold,
                            color         = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.0.sp,
                        ),
                    ) { append(fp) }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(R.string.passphrase_fp_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.passphrase_not_sharded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            value           = passphraseLocation,
            onValueChange   = onPassphraseLocationChange,
            label           = { Text(stringResource(R.string.passphrase_location_label)) },
            placeholder     = { Text(stringResource(R.string.passphrase_location_placeholder)) },
            singleLine      = true,
            shape           = MaterialTheme.shapes.small,
            colors          = s4TextFieldColors(),
            modifier        = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Share configuration card (T/N sliders)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShareConfigSection(
    shareCount: Int,
    threshold: Int,
    onShareCountChange: (Float) -> Unit,
    onThresholdChange: (Float) -> Unit,
) {
    SectionCard(eyebrow = stringResource(R.string.config_eyebrow)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SliderHeading(stringResource(R.string.total_shares), shareCount)
            Slider(
                value          = shareCount.toFloat(),
                onValueChange  = onShareCountChange,
                valueRange = 2f..SplitParams.MAX_SHARES.toFloat(),
                steps      = SplitParams.MAX_SHARES - 3,
                colors     = s4SliderColors(),
                modifier   = Modifier.testTag("shareCount"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SliderHeading(stringResource(R.string.restore_threshold), threshold)
            Slider(
                value         = threshold.toFloat(),
                onValueChange = onThresholdChange,
                valueRange    = 1f..shareCount.toFloat(),
                steps         = (shareCount - 2).coerceAtLeast(0),
                colors        = s4SliderColors(),
                modifier      = Modifier.testTag("threshold"),
            )
        }
        Text(
            stringResource(R.string.any_of_shares, threshold, shareCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MnemonicInput
// ─────────────────────────────────────────────────────────────────────────────

/** Mnemonic field with live BIP-39 word suggestions while typing. */
@Composable
private fun MnemonicInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    showError: Boolean,
    validationError: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val typedCount = remember(value.text) {
            value.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        }
        val cursor = value.selection.end
        val currentWord = remember(value.text, cursor) { currentTypedWord(value.text, cursor) }
        val suggestions = remember(currentWord) {
            if (currentWord.isEmpty()) emptyList()
            else Bip39.words.filter { it.startsWith(currentWord) }.take(4)
        }

        OutlinedTextField(
            value          = value,
            onValueChange  = onValueChange,
            label          = { Text(stringResource(R.string.seed_phrase_label)) },
            placeholder    = { Text(stringResource(R.string.seed_phrase_placeholder)) },
            supportingText = {
                Text(
                    if (showError) validationError.orEmpty()
                    else pluralStringResource(R.plurals.words_typed, typedCount, typedCount)
                )
            },
            isError  = showError,
            minLines = 3,
            shape    = MaterialTheme.shapes.small,
            colors   = s4TextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("seedInput"),
        )

        if (suggestions.isNotEmpty()) {
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { word ->
                    val completed = completeWord(value.text, cursor, word)
                    SuggestionChip(
                        word    = word,
                        onClick = {
                            onValueChange(
                                TextFieldValue(completed.text, selection = TextRange(completed.cursor)),
                            )
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SliderHeading
// ─────────────────────────────────────────────────────────────────────────────

/** "Total shares: **6**" with the number in mono primary. */
@Composable
private fun SliderHeading(prefix: String, value: Int) {
    Text(
        text = buildAnnotatedString {
            append(prefix)
            append(": ")
            withStyle(
                SpanStyle(
                    fontFamily  = RobotoMono,
                    fontWeight  = FontWeight.Bold,
                    color       = MaterialTheme.colorScheme.primary,
                    fontSize    = MaterialTheme.typography.titleMedium.fontSize,
                ),
            ) { append(value.toString()) }
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Slider colours
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun s4SliderColors() = SliderDefaults.colors(
    thumbColor              = MaterialTheme.colorScheme.primary,
    activeTrackColor        = MaterialTheme.colorScheme.primary,
    inactiveTrackColor      = MaterialTheme.colorScheme.outlineVariant,
    activeTickColor         = Color.Transparent,
    inactiveTickColor       = Color.Transparent,
    disabledThumbColor      = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledActiveTrackColor  = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledInactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
)

// ─────────────────────────────────────────────────────────────────────────────
// Validation / decode helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun validateSecretInput(mode: SecretInputMode, mnemonic: String, entropyHex: String): String? =
    try {
        when (mode) {
            SecretInputMode.MNEMONIC -> {
                val words = mnemonic.trim().split(WHITESPACE).filter { it.isNotEmpty() }
                Bip39.mnemonicToEntropy(words)
            }
            SecretInputMode.ENTROPY_HEX -> {
                val bytes = ShareCodec.fromHex(entropyHex)
                if (bytes.size !in Bip39.VALID_ENTROPY_SIZES) {
                    throw IllegalArgumentException("entropy must be 16, 20, 24, 28, or 32 bytes (32–64 hex chars)")
                }
            }
        }
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

private fun decodeWords(mode: SecretInputMode, mnemonic: String, entropyHex: String): List<String> =
    when (mode) {
        SecretInputMode.MNEMONIC ->
            Bip39.mnemonicToEntropy(mnemonic.trim().split(WHITESPACE).filter { it.isNotEmpty() }).let {
                Bip39.entropyToMnemonic(it)
            }
        SecretInputMode.ENTROPY_HEX -> Bip39.entropyToMnemonic(ShareCodec.fromHex(entropyHex))
    }

private fun computeFingerprint(
    mode: SecretInputMode,
    mnemonic: String,
    entropyHex: String,
    passphrase: String,
): String? = try {
    val words = decodeWords(mode, mnemonic, entropyHex)
    Bip39.fingerprint(Bip39.deriveSeed(words, passphrase))
} catch (e: IllegalArgumentException) {
    null
}

private val WHITESPACE = Regex("\\s+")
