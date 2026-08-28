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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import com.s4.data.crypto.PinManager
import com.s4.data.repository.PinRepository
import com.s4.data.repository.SessionRepository
import com.s4.data.session.SessionCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI flows on-device for the metal-stamping feature: save a split
 * to encrypted storage under a short code, resume it later with just the code +
 * PIN, open it from Settings, and wipe it with "Done stamping".
 */
@RunWith(AndroidJUnit4::class)
class StampFlowInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val pin = "123456"
    private val stampPrefsName = "s4_stamp_prefs"

    private val mnemonic24 =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon art"

    @Before
    fun clearStateAndUnlock() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Delete the pref files outright so no state survives from manual testing
        // or a previous test (clear() on the cached instance is not enough).
        ctx.deleteSharedPreferences("s4_secure_prefs")
        ctx.deleteSharedPreferences(stampPrefsName)

        val pm = PinManager()
        val salt = pm.generateSalt()
        val hash = pm.hashPin(pin, salt)
        PinRepository(ctx.getSharedPreferences("s4_secure_prefs", Context.MODE_PRIVATE))
            .savePin(hash, salt, PinManager.DEFAULT_ITERATIONS, PinManager.DEFAULT_ALGORITHM)

        composeRule.activity.runOnUiThread { composeRule.activity.recreate() }
        composeRule.waitForIdle()
        Thread.sleep(800)
        waitForText("Enter PIN", substring = true)
        try {
            composeRule.onAllNodesWithText("Enter PIN")[1].performTextReplacement(pin)
            composeRule.onNodeWithText("Unlock").performClick()
            composeRule.waitForIdle()
            Thread.sleep(800)
        } catch (_: Throwable) {
            try {
                composeRule.onAllNodesWithText("Enter PIN", substring = true)[1].performTextReplacement(pin)
                composeRule.onNodeWithText("Unlock").performClick()
                composeRule.waitForIdle()
                Thread.sleep(800)
            } catch (_: Throwable) {}
        }
        waitForText("Split a wallet", substring = true)
    }

    // --- helpers ---

    /** Splits the 24-word mnemonic, saves it for stamping (PIN-gated), returns the code. */
    private fun splitAndSave(): String {
        composeRule.onNodeWithTag("seedInput").performTextReplacement(mnemonic24)
        composeRule.onNodeWithTag("confirmButton").performScrollTo().assertIsEnabled().performClick()
        waitForText("Seed shares", substring = true)
        composeRule.onNodeWithTag("saveForStamping").performScrollTo().performClick()
        waitForText("Enter your PIN", substring = true)
        enterPinInDialog()
        waitForText("Your stamping code", substring = true)
        return savedCodeFromPrefs()
    }

    /** Types the PIN into the verification dialog and confirms it. */
    private fun enterPinInDialog() {
        composeRule.onNodeWithTag("pinVerifyInput").performTextReplacement(pin)
        composeRule.onNodeWithTag("pinVerifyConfirm").performClick()
        composeRule.waitForIdle()
    }

    /** Reads the saved session's code from the on-disk pref keys (the code is the pref key). */
    private fun savedCodeFromPrefs(): String = savedCodesFromPrefs().single()

    /** All session codes currently on disk, derived from the pref key names. */
    private fun savedCodesFromPrefs(): List<String> =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(stampPrefsName, Context.MODE_PRIVATE)
            .all.keys
            .map { it.removePrefix("stamp_session_") }
            .filter { SessionCodeGenerator.isValidCode(it) } // excludes the registry key (…_codes)

    private fun sessionRepository(): SessionRepository =
        SessionRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun waitForText(text: String, substring: Boolean = false) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()) return
            composeRule.waitForIdle()
        }
        // Diagnostics: dump everything currently on screen.
        val dump = composeRule.onAllNodesWithText("", substring = true)
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }
            .filter { it.isNotBlank() }
            .distinct()
        throw AssertionError("Timed out waiting for '$text' (substring=$substring). On screen: $dump")
    }

    // --- save + resume ---

    @Test
    fun saveSession_persistsShares_andResumeRestoresThem() {
        val code = splitAndSave()

        // Durable on disk: a fresh repository over the real prefs reads it back.
        val loaded = sessionRepository().load(code)
        assertNotNull("saved session must be on disk", loaded)
        assertEquals(6, loaded!!.shares.size)

        // Dismiss back to Split, then resume with just the code + PIN.
        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        waitForText("Split a wallet", substring = true)

        composeRule.onNodeWithTag("resumeButton").performScrollTo().performClick()
        waitForText("Resume a stamped session", substring = true)
        composeRule.onNodeWithTag("resumeCodeInput").performTextReplacement(code)
        composeRule.onNodeWithTag("resumeSubmit").performClick()
        waitForText("Enter your PIN", substring = true)
        enterPinInDialog()

        waitForText("Seed shares", substring = true)
        composeRule.onNodeWithText("Share 1").assertExists()
        composeRule.onNodeWithText("Share 6").assertExists()
    }

    @Test
    fun resume_wrongCode_showsNotFound() {
        // A well-formed code is validated first, then the PIN is requested, and
        // only then is the code looked up — so the "not found" error appears
        // after the PIN is verified.
        composeRule.onNodeWithTag("resumeButton").performScrollTo().performClick()
        waitForText("Resume a stamped session", substring = true)
        composeRule.onNodeWithTag("resumeCodeInput").performTextReplacement("ZZZZZZ")
        composeRule.onNodeWithTag("resumeSubmit").performClick()
        waitForText("Enter your PIN", substring = true)
        enterPinInDialog()

        waitForText("No saved session matches this code", substring = true)
    }

    // --- multiple sessions coexist ---

    @Test
    fun twoSavedSessions_coexistAndAreIndependentlyListed() {
        val codeA = splitAndSave()

        // Split a different seed and save it as a second session.
        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        waitForText("Split a wallet", substring = true)
        composeRule.onNodeWithTag("seedInput").performTextReplacement(
            "abandon amount liar amount expire adjust cage candy arch gather drum bullet " +
                "absurd math era live bid rhythm alien crouch range attend journey unaware",
        )
        composeRule.onNodeWithTag("confirmButton").performScrollTo().performClick()
        waitForText("Seed shares", substring = true)
        composeRule.onNodeWithTag("saveForStamping").performScrollTo().performClick()
        waitForText("Enter your PIN", substring = true)
        enterPinInDialog()
        waitForText("Your stamping code", substring = true)
        val codes = savedCodesFromPrefs()
        assertTrue("expected two sessions total, was $codes", codes.size == 2)
        val codeB = codes.first { it != codeA }
        assertEquals(setOf(codeA, codeB), sessionRepository().codes().toSet())
    }

    // --- wipe ---

    @Test
    fun doneStamping_wipesSessionFromDisk() {
        val code = splitAndSave()

        composeRule.onNodeWithTag("doneStamping").performScrollTo().performClick()
        waitForText("Finish stamping?", substring = true)
        composeRule.onNodeWithText("Erase").performClick()
        waitForText("Split a wallet", substring = true)

        assertNull("the wiped session must be gone from disk", sessionRepository().load(code))
        assertTrue(sessionRepository().codes().isEmpty())
    }

    @Test
    fun doneStamping_cancel_keepsSession() {
        val code = splitAndSave()

        composeRule.onNodeWithTag("doneStamping").performScrollTo().performClick()
        waitForText("Finish stamping?", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertNotNull("cancelling must keep the session", sessionRepository().load(code))
    }

    // --- settings ---

    @Test
    fun settings_listsSavedSession_andOpensIt() {
        splitAndSave()

        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        waitForText("Split a wallet", substring = true)

        composeRule.onNodeWithTag("settingsButton").performScrollTo().performClick()
        waitForText("Settings")
        waitForText("SAVED STAMPING SESSIONS")
        composeRule.onNodeWithText("any 3 of 6 shares", substring = true).assertExists()

        composeRule.onNodeWithText("Open").performClick()
        waitForText("Seed shares", substring = true)
        composeRule.onNodeWithText("Share 1").assertExists()
    }

    @Test
    fun settings_erase_removesSession() {
        val code = splitAndSave()

        composeRule.onNodeWithText("Done").performScrollTo().performClick()
        waitForText("Split a wallet", substring = true)

        composeRule.onNodeWithTag("settingsButton").performScrollTo().performClick()
        waitForText("Settings")
        waitForText("SAVED STAMPING SESSIONS")
        composeRule.onNodeWithText("Erase").performClick()
        waitForText("Erase this session?", substring = true)
        composeRule.onNodeWithTag("confirmEraseSession").performClick()
        composeRule.waitForIdle()

        assertNull(sessionRepository().load(code))
        composeRule.onNodeWithText("No saved sessions", substring = true).assertExists()
    }
}