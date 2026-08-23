package com.example.zevsafe

import com.example.zevsafe.crypto.CryptoEngine
import com.example.zevsafe.crypto.VaultVersion
import com.example.zevsafe.ui.components.PasswordEvaluator
import com.example.zevsafe.ui.components.PasswordStrengthLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class CryptoUnitTest {

    @Test
    fun testV1KeyDerivationAndEncryption() {
        val password = "SuperSecretPassword123!".toCharArray()
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

        val key = CryptoEngine.deriveKeyV1(password, salt)
        val plaintext = "Hello Zero-Knowledge Android Vault!".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(plaintext)

        // Decrypt
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = decryptCipher.doFinal(ciphertext)

        assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testV2KeyDerivationWithKeyfileXOR() {
        val password = "SuperSecretPassword123!".toCharArray()
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val keyfileHash = ByteArray(32) { (it * 7).toByte() }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

        // Derive key with keyfile
        val key = CryptoEngine.deriveKeyV2(password, salt, keyfileHash)
        val plaintext = "ZevSafe v2 Enhanced Security with Keyfile 2FA".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(plaintext)

        // Decrypt with correct keyfile
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = decryptCipher.doFinal(ciphertext)

        assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testPasswordEvaluator() {
        assertEquals(PasswordStrengthLevel.VERY_WEAK, PasswordEvaluator.evaluate(""))
        assertEquals(PasswordStrengthLevel.VERY_WEAK, PasswordEvaluator.evaluate("short"))
        assertEquals(PasswordStrengthLevel.WEAK, PasswordEvaluator.evaluate("abc123"))
        assertEquals(PasswordStrengthLevel.FAIR, PasswordEvaluator.evaluate("mediumPw1"))
        assertEquals(PasswordStrengthLevel.VERY_STRONG, PasswordEvaluator.evaluate("VeryStrongPassword123!@#"))
    }

    @Test
    fun testV2HeaderMagicDetection() {
        val v2Magic = byteArrayOf(0x5A, 0x56, 0x32, 0x00)
        assertTrue(CryptoEngine.isV2Header(v2Magic))

        val randomBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        assertFalse(CryptoEngine.isV2Header(randomBytes))
    }
}
