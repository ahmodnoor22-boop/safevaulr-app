package com.example.data.security

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$password"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(passphrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, keyPhrase: String): String {
        return try {
            val encrypted = encryptBytes(plainText.toByteArray(Charsets.UTF_8), keyPhrase)
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherText: String, keyPhrase: String): String {
        return try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            if (combined.size < 16) return cipherText
            val decrypted = decryptBytes(combined, keyPhrase)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }

    fun encryptBytes(plainBytes: ByteArray, keyPhrase: String): ByteArray {
        val key = deriveKey(keyPhrase)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val encrypted = cipher.doFinal(plainBytes)
        return iv + encrypted
    }

    fun decryptBytes(cipherBytes: ByteArray, keyPhrase: String): ByteArray {
        if (cipherBytes.size < 16) return cipherBytes
        val iv = cipherBytes.copyOfRange(0, 16)
        val encrypted = cipherBytes.copyOfRange(16, cipherBytes.size)
        val key = deriveKey(keyPhrase)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        return cipher.doFinal(encrypted)
    }

    /**
     * Splits arbitrary file bytes (photos, videos, audio, PDFs, Office docs, ZIP/RAR, etc.)
     * into AES-256 encrypted Base64 chunks safely sized (< 650 KB) for Firestore documents.
     */
    fun chunkAndEncryptFile(
        bytes: ByteArray,
        keyPhrase: String,
        rawChunkSize: Int = 350_000
    ): List<String> {
        if (bytes.isEmpty()) {
            val emptyEnc = encryptBytes(ByteArray(0), keyPhrase)
            return listOf(Base64.encodeToString(emptyEnc, Base64.NO_WRAP))
        }
        val chunks = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + rawChunkSize, bytes.size)
            val slice = bytes.copyOfRange(offset, end)
            val encryptedSlice = encryptBytes(slice, keyPhrase)
            chunks.add(Base64.encodeToString(encryptedSlice, Base64.NO_WRAP))
            offset = end
        }
        return chunks
    }

    fun decryptAndReassembleChunks(
        encryptedBase64Chunks: List<String>,
        keyPhrase: String
    ): ByteArray {
        val out = ByteArrayOutputStream()
        for (b64 in encryptedBase64Chunks) {
            val cipherBytes = Base64.decode(b64, Base64.NO_WRAP)
            val plainSlice = decryptBytes(cipherBytes, keyPhrase)
            out.write(plainSlice)
        }
        return out.toByteArray()
    }
}
