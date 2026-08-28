package com.s4.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.s4.data.crypto.PinManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinStoreInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var pinStore: PinStore
    private lateinit var store: ProtectedPrefsStore
    private lateinit var prefs: android.content.SharedPreferences
    @Before fun setUp() {
        ProtectedPrefsStore.consumeProcessTamperFlag()
        prefs = newPrefs("pin_store_instr")
        store = ProtectedPrefsStore(prefs)
        pinStore = PinStore(prefs, store, MonotonicClock(prefs))
    }
    private fun newPrefs(tag: String): android.content.SharedPreferences {
        val p = context.getSharedPreferences("${tag}_${System.currentTimeMillis()}", Context.MODE_PRIVATE)
        p.edit().clear().commit(); return p
    }
    @Test fun savePersistsWholeCredentialUnderOneKey() {
        val hash = byteArrayOf(1,2,3,4); val salt = byteArrayOf(5,6,7,8)
        assertTrue(pinStore.savePin(hash, salt, 50_000, "PBKDF2WithHmacSHA256"))
        assertTrue(prefs.contains("pin_record")); assertFalse(prefs.contains("pin_hash"))
        val d = pinStore.getPinData()!!
        assertEquals(hash.toList(), d.hash.toList()); assertEquals(salt.toList(), d.salt.toList())
        assertEquals(50_000, d.iterations)
    }
    @Test fun overwritesExisting() {
        val h1 = byteArrayOf(1,2,3,4); val s1 = byteArrayOf(5,6,7,8)
        val h2 = byteArrayOf(9,10,11,12); val s2 = byteArrayOf(13,14,15,16)
        assertTrue(pinStore.savePin(h1,s1,1000,PinManager.DEFAULT_ALGORITHM))
        assertTrue(pinStore.savePin(h2,s2,2000,"PBKDF2WithHmacSHA512"))
        val d = pinStore.getPinData()!!
        assertEquals(h2.toList(), d.hash.toList()); assertEquals(2000, d.iterations)
    }
    @Test fun roundtripRealPbkdf2() {
        val pm = PinManager(iterations = 1000)
        val pin = "123456"; val salt = pm.generateSalt(); val hash = pm.hashPin(pin, salt)
        assertTrue(pinStore.savePin(hash, salt, 1000, PinManager.DEFAULT_ALGORITHM))
        val d = pinStore.getPinData()!!
        assertTrue(pm.verifyPin(pin, d.salt, d.hash, d.iterations, d.algorithm))
    }
    @Test fun refusesInvalidCredential() {
        assertFalse(pinStore.savePin(ByteArray(0), byteArrayOf(5,6,7,8), 1000, PinManager.DEFAULT_ALGORITHM))
        assertFalse(pinStore.savePin(byteArrayOf(1,2,3,4), ByteArray(0), 1000, PinManager.DEFAULT_ALGORITHM))
        assertFalse(pinStore.savePin(byteArrayOf(1,2,3,4), byteArrayOf(5,6,7,8), 0, PinManager.DEFAULT_ALGORITHM))
        assertNull(prefs.getString("pin_record", null)); assertFalse(pinStore.isPinSet())
    }
    @Test fun corruptedRecordFailsClosed() {
        assertTrue(pinStore.savePin(byteArrayOf(1,2,3,4), byteArrayOf(5,6,7,8), 1000, PinManager.DEFAULT_ALGORITHM))
        prefs.edit().putString("pin_record", "enc:broken").commit()
        assertNull(pinStore.getPinData())
        assertTrue(store.consumeTamperFlag())
        assertTrue(pinStore.isPinSet())
    }
    @Test fun prefersRecordOverLegacy() {
        pinStore.savePin(byteArrayOf(1,2,3,4), byteArrayOf(5,6,7,8), 1000, PinManager.DEFAULT_ALGORITHM)
        ProtectedPrefsStore(prefs).protectedPutString("pin_hash", java.util.Base64.getEncoder().encodeToString(byteArrayOf(9,9,9,9)))
        ProtectedPrefsStore(prefs).protectedPutString("pin_salt", java.util.Base64.getEncoder().encodeToString(byteArrayOf(8,8,8,8)))
        assertEquals(byteArrayOf(1,2,3,4).toList(), pinStore.getPinData()!!.hash.toList())
    }
}
