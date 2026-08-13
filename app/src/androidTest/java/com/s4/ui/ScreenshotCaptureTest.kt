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

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.s4.MainActivity
import com.s4.bip39.Bip39
import com.s4.crypto.Slip39
import com.s4.model.SplitParams
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the app to every screen in a representative state and parks on each
 * one so `make screens` / `make screens-dark` can capture it.
 *
 * adb screencap returns a black frame because the app sets FLAG_SECURE, but the
 * emulator's own screenshot (`adb emu screenrecord screenshot`) captures the
 * real display including the system bars. This test is run in the background by
 * the Makefile; on each view it writes a marker file (`<name>.park`) and stays
 * put, and the Makefile waits for the marker, screencaps the emulator, and lets
 * the test move on. Theme is controlled by `cmd uimode night no|yes` before the
 * run, so Compose's dark-mode detection picks it up.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun parkOnEachView() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        // Clear any leftover markers from a previous run so the host's poll
        // can't trip on a stale one before this run parks on the view.
        filesDir.listFiles { it.name.endsWith(".park") }?.forEach { it.delete() }

        fun park(name: String) {
            closeKeyboard()
            File(filesDir, "$name.park").writeText("1")
            Thread.sleep(PARK_MS)
        }

        // 1. Split screen: seed + passphrase, so the fingerprint preview card
        //    is visible. This is also the README screenshot (screen-<theme>.png).
        composeRule.onNodeWithTag("seedInput").performTextReplacement(MNEMONIC_24)
        composeRule.onNodeWithTag("passphraseField").performTextReplacement(PASSPHRASE)
        composeRule.waitForIdle()
        park("screen")

        // 2. Split screen in entropy-hex input mode.
        composeRule.onNodeWithTag("toggleInputMode").performClick()
        composeRule.onNodeWithTag("entropyInput").performTextReplacement(ENTROPY_HEX)
        composeRule.waitForIdle()
        park("split-entropy")

        // 3. Back to the mnemonic, drop the passphrase (so the results page has
        //    no passphrase warning), and split into the default 6 shares / T=3.
        composeRule.onNodeWithTag("toggleInputMode").performClick()
        composeRule.onNodeWithTag("passphraseField").performTextReplacement("")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirmButton").performScrollTo().performClick()
        waitForText("Seed shares")
        composeRule.waitForIdle()
        park("results")

        // 4. Session-backed Recovery Guide (real T/N + fingerprint).
        composeRule.onNodeWithText("Recovery Guide").performScrollTo().performClick()
        waitForText("RECOVERY GUIDE")
        composeRule.waitForIdle()
        park("guide")

        // 5. Done drops the session and pops back to the split screen (the nav
        //    helper pops to the start destination). Opening the guide from the
        //    split tab again then renders the generic blank template.
        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("openGuideSplit").performScrollTo().performClick()
        waitForText("RECOVERY GUIDE")
        composeRule.waitForIdle()
        park("guide-blank")
        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 6. Restore screen.
        composeRule.onNodeWithTag("tabRestore").performClick()
        composeRule.waitForIdle()
        park("restore")

        // 7. Restore from 3 shares of a real split → recovered seed + fingerprint.
        val shares = Slip39.split(SplitParams(threshold = 3, shareCount = 5), entropy())
        composeRule.onNodeWithTag("sharesInput").performTextReplacement(shares.take(3).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()
        waitForText(MNEMONIC_24)
        composeRule.onNodeWithText(MNEMONIC_24, substring = true).performScrollTo()
        composeRule.waitForIdle()
        park("restore-result")

        // 8. Restore failure: only 2 shares, threshold 3 → error banner.
        composeRule.onNodeWithTag("sharesInput").performScrollTo().performTextReplacement(shares.take(2).joinToString("\n"))
        composeRule.onNodeWithTag("restoreButton").performScrollTo().performClick()
        waitForText("Not enough shares")
        composeRule.onNodeWithText("Not enough shares", substring = true).performScrollTo()
        composeRule.waitForIdle()
        park("restore-error")
    }

    private fun closeKeyboard() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            activity.currentFocus?.clearFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = true) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun entropy(): ByteArray = Bip39.mnemonicToEntropy(MNEMONIC_24.split(" "))

    private companion object {
        /** How long to stay parked on each view so the host can screencap. */
        const val PARK_MS = 8_000L

        // A valid 24-word BIP-39 mnemonic (real-looking words, not the
        // all-"abandon" test vector) so the screenshots look natural.
        val MNEMONIC_24 =
            "auction patrol ask innocent asset race solve riot still jealous miracle toss " +
                "other excuse hammer trust budget guitar salad maple quiz save buddy flower"
        val PASSPHRASE = "correct horse battery staple"
        val ENTROPY_HEX = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
