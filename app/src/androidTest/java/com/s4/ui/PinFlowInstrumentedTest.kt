package com.s4.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.s4.MainActivity
import com.s4.data.crypto.PinManager
import com.s4.data.repository.PinRepository
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinFlowInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val pin = "123456"
    @Before fun clearPin() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("s4_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        rule.activity.runOnUiThread { rule.activity.recreate() }
        rule.waitForIdle()
        Thread.sleep(800)
    }
    @Test fun pinSetup_isMandatory_noSkip_andCanCreatePin() {
        rule.waitUntil(8000) {
            try { rule.onAllNodesWithText("Create PIN").fetchSemanticsNodes().isNotEmpty() } catch (_: Throwable) { false }
        }
        rule.onNodeWithText("Create PIN").assertIsDisplayed()
        // Mandatory — no skip path
        val skipNodes = try { rule.onAllNodesWithText("Skip for now").fetchSemanticsNodes() } catch (_: Throwable) { null }
        assertTrue(skipNodes == null || skipNodes.isEmpty())
        // Complete setup via UI: fill both fields and confirm
        rule.onNodeWithText("New PIN (6 digits)").performTextInput(pin)
        rule.onNodeWithText("Confirm PIN").performTextInput(pin)
        rule.onNodeWithText("Set PIN & Continue").performClick()
        rule.waitUntil(8000) {
            try { rule.onAllNodesWithText("Split a wallet").fetchSemanticsNodes().isNotEmpty() } catch (_: Throwable) { false }
        }
        rule.onNodeWithText("Split a wallet").assertIsDisplayed()
    }
    @Test fun pinLockout_showsCooldown_after5WrongAttempts() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = ctx.getSharedPreferences("s4_secure_prefs", Context.MODE_PRIVATE)
        val pm = PinManager()
        val salt = pm.generateSalt()
        val hash = pm.hashPin(pin, salt)
        val repo = PinRepository(prefs)
        repo.savePin(hash, salt, PinManager.DEFAULT_ITERATIONS, PinManager.DEFAULT_ALGORITHM)
        repo.resetPinFailedAttempts()
        rule.activity.runOnUiThread { rule.activity.recreate() }
        rule.waitForIdle()
        Thread.sleep(800)
        rule.waitUntil(8000) {
            try { rule.onAllNodesWithText("Enter PIN").fetchSemanticsNodes().isNotEmpty() } catch (_: Throwable) { false }
        }
        repeat(5) { repo.incrementPinFailedAttempts() }
        val lockoutMs = com.s4.data.repository.PinLockoutPolicy.lockoutMs(5)
        repo.setPinLockoutUntil(repo.getMonotonicNow() + lockoutMs)
        rule.activity.runOnUiThread { rule.activity.recreate() }
        rule.waitForIdle()
        Thread.sleep(500)
        val checkRepo = PinRepository(prefs)
        assertTrue(checkRepo.getPinLockoutRemainingMs() > 0)
        rule.waitUntil(8000) {
            try { rule.onAllNodesWithText("Too many failed attempts", substring = true).fetchSemanticsNodes().isNotEmpty() } catch (_: Throwable) { false }
        }
        rule.onNodeWithText("Too many failed attempts", substring = true).assertIsDisplayed()
    }
}
