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

package com.s4.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager(
    private val masterKeyAlias: String = KEY_ALIAS,
    private val hmacKeyAlias: String = HMAC_KEY_ALIAS,
) : PrefsCrypto {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "S4MasterKey"
        private const val HMAC_KEY_ALIAS = "S4HmacKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val masterKey: SecretKey = recoverKey(masterKeyAlias, ::generateMasterKey)
    private val hmacKey: SecretKey = recoverKey(hmacKeyAlias, ::generateHmacKey)

    private fun recoverKey(alias: String, generate: (String) -> Unit): SecretKey {
        return KeystoreKeyRecovery(
            containsAlias = { keyStore.containsAlias(it) },
            readSecretKey = { (keyStore.getEntry(it, null) as KeyStore.SecretKeyEntry).secretKey },
            deleteAlias = { keyStore.deleteEntry(it) },
            generateKey = generate,
        ).ensureKey(alias)
    }

    private fun generateMasterKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(parameterSpec)
        keyGenerator.generateKey()
    }

    private fun generateHmacKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE,
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN,
        )
            .setKeySize(256)
            .build()

        keyGenerator.init(parameterSpec)
        keyGenerator.generateKey()
    }

    override fun encrypt(data: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(data)
        val iv = cipher.iv
        return Pair(ciphertext, iv)
    }

    override fun decrypt(ciphertext: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    override fun hmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal(data)
    }
}
