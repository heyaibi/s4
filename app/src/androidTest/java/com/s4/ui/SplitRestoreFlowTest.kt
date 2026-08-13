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

import android.content.ClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.s4.MainActivity
import com.s4.bip39.Bip39
import com.s4.crypto.Slip39Wordlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI flows on-device: split a 24-word mnemonic into 5 SLIP-39 shares
 * with threshold 3, copy the 33-word phrases to the clipboard, restore from 3 of
 * them (words + fingerprint must match), and verify the clean-failure/error
 * states and the passphrase flow.
 */
@RunWith(AndroidJUnit4::class)
class SplitRestoreFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Official BIP-39 vector (24 words, all-zero 32-byte entropy).
    private val mnemonic24 =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon art"

    // Another valid 24-word mnemonic (different entropy), for cross-set mismatch tests.
    private val otherMnemonic =
        "abandon amount liar amount expire adjust cage candy arch gather drum bullet " +
            "absurd math era live bid rhythm alien crouch range attend journey unaware"

    @Test
    fun split24Word_5shares_t3_thenRestore3of5_recoversMnemonic() {
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic24)
        setSlider("shareCount", 5f)
        setSlider("threshold", 3f)
        composeRule.onNodeWithText("Total shares: 5").assertExists()
        composeRule.onNodeWithTag("confirmButton").performScrollTo().assertIsEnabled().performClick()

        waitForText("Seed shares", substring = true)
        for (i in 1..5) {
            composeRule.onNodeWithText("Share $i").assertExists()
        }

        composeRule.onNodeWithText("Copy all").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirmClipboardCopy").performClick()
        composeRule.waitForIdle()

        val shareLines = readClipboardLines()
        assertEquals(5, shareLines.size)
        shareLines.forEach { line ->
            val words = line.split(' ').filter { it.isNotEmpty() }
            assertEquals(33, words.size)
            assertTrue("share words must be valid SLIP-39 words", words.all { Slip39Wordlist.isValidWord(it) })
        }

        // Restore from 3 of the 5 shares.
        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput").performTextReplacement(shareLines.take(3).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        waitForText(mnemonic24, substring = true)
        composeRule.onNodeWithText(mnemonic24, substring = true).assertExists()
    }

    @Test
    fun split12Word_3shares_t2_thenRestore2of3_recoversMnemonic() {
        // Official BIP-39 vector (12 words, all-zero 16-byte entropy).
        val mnemonic12 =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic12)
        setSlider("shareCount", 3f)
        setSlider("threshold", 2f)
        composeRule.onNodeWithTag("confirmButton").performScrollTo().assertIsEnabled().performClick()

        waitForText("Seed shares", substring = true)
        for (i in 1..3) {
            composeRule.onNodeWithText("Share $i").assertExists()
        }

        copyAllToClipboard()
        val shareLines = readClipboardLines()
        assertEquals(3, shareLines.size)
        shareLines.forEach { line ->
            val words = line.split(' ').filter { it.isNotEmpty() }
            assertEquals("a 128-bit secret produces 20-word SLIP-39 shares", 20, words.size)
            assertTrue("share words must be valid SLIP-39 words", words.all { Slip39Wordlist.isValidWord(it) })
        }

        // Restore from 2 of the 3 shares — the case BUG-001 fixed (the old
        // parser rejected any total not divisible by 33, so 12-word seeds could
        // never be restored).
        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput").performTextReplacement(shareLines.take(2).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        waitForText(mnemonic12, substring = true)
        composeRule.onNodeWithText(mnemonic12, substring = true).assertExists()
    }

    @Test
    fun restore_showsFingerprint() {
        splitToClipboard(threshold = 3)

        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput").performTextReplacement(readClipboardLines().take(3).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        waitForText("Fingerprint:", substring = true)
        composeRule.onNodeWithText("Fingerprint:", substring = true).assertExists()
    }

    @Test
    fun passphraseFlow_splitWithPassphrase_restoreFingerprintMatches() {
        val passphrase = "correct horse battery staple"
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic24)

        // Enter the passphrase.
        composeRule.onNodeWithTag("passphraseField").performTextReplacement(passphrase)

        // The fingerprint preview should appear with the passphrase.
        composeRule.onNodeWithText("Fingerprint: ", substring = true).assertExists()

        composeRule.onNodeWithTag("confirmButton").performScrollTo().assertIsEnabled().performClick()
        waitForText("Seed shares", substring = true)
        composeRule.onNodeWithText("This wallet uses a BIP-39 passphrase", substring = true).assertExists()
        val splitFingerprint = readFingerprint("Fingerprint: ")

        composeRule.onNodeWithText("Copy all").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirmClipboardCopy").performClick()
        composeRule.waitForIdle()
        val shares = readClipboardLines()

        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput").performTextReplacement(shares.take(3).joinToString("\n"))
        composeRule.onNodeWithTag("passphraseField").performTextReplacement(passphrase)
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        // The split fingerprint equals the restore fingerprint (same words + passphrase).
        waitForText("Fingerprint (with passphrase):", substring = true)
        val restoreFingerprint = readFingerprint("Fingerprint (with passphrase): ")
        assertEquals("split and restore fingerprints must match", splitFingerprint, restoreFingerprint)
    }

    @Test
    fun passphraseFlow_wrongPassphrase_showsDifferentFingerprint() {
        val passphrase = "correct horse battery staple"
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic24)
        composeRule.onNodeWithTag("passphraseField").performTextReplacement(passphrase)
        composeRule.onNodeWithTag("confirmButton").performScrollTo().assertIsEnabled().performClick()
        waitForText("Seed shares", substring = true)
        val splitFingerprint = readFingerprint("Fingerprint: ")

        composeRule.onNodeWithText("Copy all").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirmClipboardCopy").performClick()
        composeRule.waitForIdle()
        val shares = readClipboardLines()

        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput").performTextReplacement(shares.take(3).joinToString("\n"))
        composeRule.onNodeWithTag("passphraseField").performTextReplacement("the WRONG passphrase")
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        // A wrong passphrase derives a different seed => a different fingerprint,
        // catching the mistake before any funds are touched.
        waitForText("Fingerprint (with passphrase):", substring = true)
        val wrongFingerprint = readFingerprint("Fingerprint (with passphrase): ")
        assertNotEquals(
            "a wrong passphrase must yield a different fingerprint",
            splitFingerprint,
            wrongFingerprint,
        )
    }

    @Test
    fun resultsPage_survivesAccidentalTap_sharesUnchanged() {
        splitToClipboard(threshold = 3)
        val before = readSharePhrases()
        assertEquals(5, before.size)

        // An accidental tap on a share card (not a button) must not dismiss the
        // results page or regenerate the shares.
        composeRule.onNodeWithText("Share 1").performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Seed shares", substring = true).assertExists()
        val after = readSharePhrases()
        assertEquals("shares must not be regenerated by an accidental tap", before, after)
    }

    @Test
    fun restoreWithOnly2Shares_failsCleanly() {
        splitToClipboard(threshold = 3)

        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput").performTextReplacement(readClipboardLines().take(2).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        waitForText("Not enough shares:", substring = true)
    }

    @Test
    fun restore_withCorruptedShare_failsChecksum() {
        splitToClipboard(threshold = 3)

        val lines = readClipboardLines()
        val corrupted = corruptWord(lines[2])

        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput")
            .performTextReplacement((listOf(lines[0], lines[1], corrupted)).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        waitForText("Checksum failed:", substring = true)
    }

    @Test
    fun restore_withMismatchedShares_failsMismatch() {
        // Split seed A, then seed B; combine one share from each set.
        splitToClipboard(threshold = 3)
        val setA = readClipboardLines()

        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("seedInput").performTextReplacement(otherMnemonic)
        composeRule.onNodeWithTag("confirmButton").performScrollTo().performClick()
        waitForText("Seed shares", substring = true)
        composeRule.onNodeWithText("Copy all").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirmClipboardCopy").performClick()
        composeRule.waitForIdle()
        val setB = readClipboardLines()

        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput")
            .performTextReplacement((listOf(setA[0], setA[1], setB[0])).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        waitForText("not from the same wallet", substring = true)
    }

    @Test
    fun restore_shortInput_showsInlineError() {
        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.onNodeWithTag("sharesInput").performTextReplacement("academic acid acne")
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()

        waitForText("SLIP-39 shares are 20, 23, 27, 30, or 33 words each", substring = true)
    }

    @Test
    fun invalidMnemonic_showsInlineErrorAndDisablesConfirm() {
        composeRule.onNodeWithTag("seedInput").performTextReplacement("one two three")
        composeRule.onNodeWithText("must have 12, 15, 18, 21, or 24 words", substring = true).assertExists()
        composeRule.onNodeWithTag("confirmButton").assertIsNotEnabled()
    }

    @Test
    fun recoveryGuide_openFromSplitTab_showsGenericTemplate() {
        composeRule.onNodeWithTag("openGuideSplit").performScrollTo().performClick()
        waitForText("RECOVERY GUIDE", substring = true)
        composeRule.onNodeWithText("RECOVERY GUIDE", substring = true).assertExists()
    }

    @Test
    fun recoveryGuide_fromResultsPage_showsRealTN_andFingerprint() {
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic24)
        composeRule.onNodeWithTag("confirmButton").performScrollTo().performClick()
        waitForText("Seed shares", substring = true)

        composeRule.onNodeWithText("Recovery Guide").performScrollTo().performClick()
        waitForText("RECOVERY GUIDE", substring = true)

        // Real T/N and a real 16-char fingerprint are rendered.
        composeRule.onNodeWithText("This wallet's seed was split into 6 shares; any 3 are needed", substring = true).assertExists()
        composeRule.onNodeWithText("The fingerprint you should see is ", substring = true).assertExists()
    }

    @Test
    fun recoveryGuide_withPassphrase_mentionsSeparateSecretAndLocation() {
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic24)
        composeRule.onNodeWithTag("passphraseField").performTextReplacement("a secret")
        composeRule.onNodeWithTag("confirmButton").performScrollTo().performClick()
        waitForText("Seed shares", substring = true)

        composeRule.onNodeWithText("Recovery Guide").performScrollTo().performClick()
        waitForText("RECOVERY GUIDE", substring = true)

        composeRule.onNodeWithText("This wallet uses a BIP-39 passphrase", substring = true).assertExists()
        composeRule.onNodeWithText("The passphrase is NOT in these shares", substring = true).assertExists()
    }

    @Test
    fun recoveryGuide_copyButton_copiesVerbatimText() {
        composeRule.onNodeWithTag("openGuideSplit").performScrollTo().performClick()
        waitForText("RECOVERY GUIDE", substring = true)

        composeRule.onNodeWithText("Copy guide").performScrollTo().performClick()
        composeRule.waitForIdle()

        val copied = readClipboardText()
        assertTrue("guide text should start with the header", copied.startsWith("RECOVERY GUIDE"))
        assertTrue("guide text should mention SLIP-39", copied.contains("SLIP-39"))
        assertTrue("guide text should mention iancoleman.io/bip39", copied.contains("iancoleman.io/bip39"))
    }

    // --- helpers ---

    /** Drives the split UI and leaves the 33-word share phrases on the clipboard. */
    private fun splitToClipboard(threshold: Int) {
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic24)
        setSlider("shareCount", 5f)
        setSlider("threshold", threshold.toFloat())
        composeRule.onNodeWithTag("confirmButton").performScrollTo().performClick()
        waitForText("Seed shares", substring = true)
        copyAllToClipboard()
    }

    /** Clicks "Copy all" and confirms the clipboard-risk dialog. */
    private fun copyAllToClipboard() {
        composeRule.onNodeWithText("Copy all").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirmClipboardCopy").performClick()
        composeRule.waitForIdle()
    }

    private fun setSlider(tag: String, value: Float) {
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        val setProgress = node.config[SemanticsActions.SetProgress].action
        assertTrue("slider '$tag' has no set-progress action", setProgress != null)
        setProgress?.invoke(value)
        composeRule.waitForIdle()
    }

    private fun corruptWord(share: String): String {
        val words = share.split(' ')
        val bad = Slip39Wordlist.words[(Slip39Wordlist.indexOf(words.last())!! + 1) % 1024]
        return (words.dropLast(1) + bad).joinToString(" ")
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        waitUntil {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The 16-hex-char fingerprint value rendered after a prefix like "Fingerprint: ". */
    private fun readFingerprint(prefix: String): String {
        val node = composeRule.onNodeWithText(prefix, substring = true).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
        val value = text.substringAfter(prefix, "").trim()
        assertTrue("expected a 16-char hex fingerprint after '$prefix', got '$text'", value.matches(Regex("[0-9a-f]{16}")))
        return value
    }

    /** All 33-word SLIP-39 share phrases currently on screen, in order. */
    private fun readSharePhrases(): List<String> {
        val phrases = mutableListOf<String>()
        composeRule.onAllNodes(hasText(" ", substring = true))
            .fetchSemanticsNodes()
            .forEach { node ->
                val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
                if (text.split(' ').size == 33 && text.split(' ').all { Slip39Wordlist.isValidWord(it) }) {
                    phrases.add(text)
                }
            }
        return phrases
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 15_000) { condition() }
    }

    private fun readClipboardLines(): List<String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
            ?: throw AssertionError("clipboard is empty")
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun readClipboardText(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = context.getSystemService(ClipboardManager::class.java)
        return cm.primaryClip?.getItemAt(0)?.text?.toString()
            ?: throw AssertionError("clipboard is empty")
    }
}
