package com.example.crypto

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    
    // We derive a stable 16-byte IV from the key to ensure seamless symmetric decryption
    // in the offline-first/local-simulation environment.
    private fun getIv(keyBytes: ByteArray): IvParameterSpec {
        val ivBytes = ByteArray(16)
        System.arraycopy(keyBytes, 0, ivBytes, 0, 16)
        return IvParameterSpec(ivBytes)
    }

    fun deriveKey(code1: String, code2: String): SecretKeySpec {
        // Sort codes alphabetically to guarantee consistent key derivation on both ends of the conversation
        val sortedCodes = listOf(code1.trim(), code2.trim()).sorted()
        val combined = "${sortedCodes[0]}:${sortedCodes[1]}"
        
        // Hash with SHA-256 to produce a 256-bit (32-byte) key
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun encrypt(plainText: String, key: SecretKeySpec): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = getIv(key.encoded)
            cipher.init(Cipher.ENCRYPT_MODE, key, iv)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            "Encryption Error"
        }
    }

    fun decrypt(cipherText: String, key: SecretKeySpec): String {
        if (cipherText.isEmpty() || cipherText == "Encryption Error") return cipherText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = getIv(key.encoded)
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            val decodedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            "🔒 [Decryption Error: Key Mismatch]"
        }
    }
}
