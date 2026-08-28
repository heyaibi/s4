package com.s4.data.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class JvmPrefsCrypto(seed: String = "jvm-prefs-crypto") : PrefsCrypto {
    private val aesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("$seed-aes".toByteArray(Charsets.UTF_8)), "AES")
    private val hmacKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("$seed-hmac".toByteArray(Charsets.UTF_8)), "HmacSHA256")
    override fun encrypt(data: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(data)
        return Pair(ciphertext, cipher.iv)
    }
    override fun decrypt(ciphertext: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
    override fun hmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal(data)
    }
}
