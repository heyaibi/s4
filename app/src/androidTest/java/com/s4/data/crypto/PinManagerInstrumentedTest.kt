package com.s4.data.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinManagerInstrumentedTest {
    private val pinManager = PinManager(iterations = 1000, keyLengthBits = 256)
    @Test fun hash_and_verify_default() {
        val pin = "123456"; val salt = pinManager.generateSalt(); val hash = pinManager.hashPin(pin, salt)
        assertEquals(32, hash.size); assertTrue(pinManager.verifyPin(pin, salt, hash))
    }
    @Test fun rejects_wrong_pin() {
        val pin = "123456"; val salt = pinManager.generateSalt(); val hash = pinManager.hashPin(pin, salt)
        assertFalse(pinManager.verifyPin("654321", salt, hash))
    }
    @Test fun custom_iterations() {
        val pin = "123456"; val salt = pinManager.generateSalt(); val ci = 500
        val hash = PinManager(iterations = ci).hashPin(pin, salt)
        assertTrue(pinManager.verifyPin(pin, salt, hash, ci))
    }
    @Test fun rejects_wrong_iterations() {
        val pin = "123456"; val salt = pinManager.generateSalt(); val hash = pinManager.hashPin(pin, salt)
        assertFalse(pinManager.verifyPin(pin, salt, hash, iterations = 999))
    }
    @Test fun different_salts_diff_hashes() {
        val pin = "123456"; val s1 = pinManager.generateSalt(); val s2 = pinManager.generateSalt()
        assertNotEquals(pinManager.hashPin(pin, s1).toList(), pinManager.hashPin(pin, s2).toList())
    }
    @Test fun rejects_wrong_salt() {
        val pin = "123456"; val s1 = pinManager.generateSalt(); val s2 = pinManager.generateSalt()
        val hash = pinManager.hashPin(pin, s1)
        assertFalse(pinManager.verifyPin(pin, s2, hash))
    }
    @Test fun salt_unique_16() {
        val s1 = pinManager.generateSalt(); val s2 = pinManager.generateSalt()
        assertEquals(16, s1.size); assertEquals(16, s2.size); assertNotEquals(s1.toList(), s2.toList())
    }
    @Test fun rejects_empty_hash() {
        val pin = "123456"; val salt = pinManager.generateSalt()
        assertFalse(pinManager.verifyPin(pin, salt, byteArrayOf()))
    }
    @Test fun rejects_short_pin() {
        val salt = pinManager.generateSalt(); val hash = pinManager.hashPin("123456", salt)
        assertFalse(pinManager.verifyPin("12345", salt, hash))
    }
    @Test fun defaults_correct() {
        assertEquals("PBKDF2WithHmacSHA256", PinManager.DEFAULT_ALGORITHM)
        assertEquals(120_000, PinManager.DEFAULT_ITERATIONS)
        assertEquals(256, PinManager.DEFAULT_KEY_LENGTH_BITS)
    }
}
