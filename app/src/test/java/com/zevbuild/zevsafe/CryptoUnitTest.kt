package com.zevbuild.zevsafe

import com.zevbuild.zevsafe.crypto.CryptoEngine
import com.zevbuild.zevsafe.crypto.VaultVersion
import com.zevbuild.zevsafe.ui.components.PasswordEvaluator
import com.zevbuild.zevsafe.ui.components.PasswordStrengthLevel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
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

        val key = CryptoEngine.deriveKeyV2(password, salt, keyfileHash)
        val plaintext = "ZevSafe v2 Enhanced Security with Keyfile 2FA".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(plaintext)

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = decryptCipher.doFinal(ciphertext)

        assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testV3HeaderMagicDetection() {
        val v3Magic = byteArrayOf(0x5A, 0x56, 0x33, 0x00) // "ZV3\0"
        assertTrue(CryptoEngine.isV3Header(v3Magic))

        val v2Magic = byteArrayOf(0x5A, 0x56, 0x32, 0x00) // "ZV2\0"
        assertFalse(CryptoEngine.isV3Header(v2Magic))
        assertTrue(CryptoEngine.isV2Header(v2Magic))

        val randomBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        assertFalse(CryptoEngine.isV3Header(randomBytes))
    }

    @Test
    fun testV3ChunkIVComputation() {
        val baseIVPrefix = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val ivNonFinal = CryptoEngine.computeChunkIV(baseIVPrefix, 42, isLast = false)
        assertEquals(12, ivNonFinal.size)
        // Check prefix
        for (i in 0 until 7) {
            assertEquals((i + 1).toByte(), ivNonFinal[i])
        }
        // Check chunk counter 42 (big-endian at index 7..10)
        assertEquals(0.toByte(), ivNonFinal[7])
        assertEquals(0.toByte(), ivNonFinal[8])
        assertEquals(0.toByte(), ivNonFinal[9])
        assertEquals(42.toByte(), ivNonFinal[10])
        // Check isLast flag
        assertEquals(0x00.toByte(), ivNonFinal[11])

        val ivFinal = CryptoEngine.computeChunkIV(baseIVPrefix, 0, isLast = true)
        assertEquals(0x01.toByte(), ivFinal[11])
    }

    @Test
    fun testV3ChunkAADComputation() {
        val salt = ByteArray(32) { (it + 10).toByte() }
        val aad = CryptoEngine.computeChunkAAD(CryptoEngine.V3_MAGIC, CryptoEngine.V3_VERSION_BYTE, salt, 5, isLast = false)
        assertEquals(42, aad.size)

        // Magic ZV3\0
        assertEquals(0x5A.toByte(), aad[0])
        assertEquals(0x56.toByte(), aad[1])
        assertEquals(0x33.toByte(), aad[2])
        assertEquals(0x00.toByte(), aad[3])
        // Version 0x03
        assertEquals(0x03.toByte(), aad[4])
        // Salt bytes
        assertEquals(10.toByte(), aad[5])
        // Chunk index 5 at offset 37..40
        assertEquals(0.toByte(), aad[37])
        assertEquals(0.toByte(), aad[38])
        assertEquals(0.toByte(), aad[39])
        assertEquals(5.toByte(), aad[40])
        // isLast flag at 41
        assertEquals(0x00.toByte(), aad[41])
    }

    @Test
    fun testV3ChunkEncryptAndDecryptRoundTrip() {
        val password = "StrongPassword456!".toCharArray()
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val baseIVPrefix = ByteArray(7).apply { SecureRandom().nextBytes(this) }
        val key = CryptoEngine.deriveKeyV3(password, salt, null)

        val plaintext = "ZevSafe v3 STREAM AEAD Chunk Payload Data".toByteArray(Charsets.UTF_8)
        val framedChunk = CryptoEngine.encryptChunk(key, plaintext, baseIVPrefix, chunkIndex = 0, isLast = true, salt = salt)

        val decrypted = CryptoEngine.decryptChunk(key, framedChunk, baseIVPrefix, chunkIndex = 0, isLast = true, salt = salt)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testV3ChunkTamperingDetection() {
        val password = "StrongPassword456!".toCharArray()
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val baseIVPrefix = ByteArray(7).apply { SecureRandom().nextBytes(this) }
        val key = CryptoEngine.deriveKeyV3(password, salt, null)

        val plaintext = "Sensitive tamper test data".toByteArray(Charsets.UTF_8)
        val framedChunk = CryptoEngine.encryptChunk(key, plaintext, baseIVPrefix, chunkIndex = 0, isLast = true, salt = salt)

        // Tamper with one ciphertext byte
        framedChunk[CryptoEngine.CHUNK_HEADER_SIZE + 2] = (framedChunk[CryptoEngine.CHUNK_HEADER_SIZE + 2].toInt() xor 0xFF).toByte()

        try {
            CryptoEngine.decryptChunk(key, framedChunk, baseIVPrefix, chunkIndex = 0, isLast = true, salt = salt)
            fail("Expected exception when decrypting tampered chunk")
        } catch (_: Exception) {
            // Success: tamper detected!
        }
    }

    @Test
    fun testV3ContainerHeaderSerializationAndParsing() {
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val baseIVPrefix = ByteArray(7).apply { SecureRandom().nextBytes(this) }
        val manifestOffset = 104857600L // 100 MB

        val headerBytes = CryptoEngine.createV3ContainerHeader(
            salt = salt,
            baseIVPrefix = baseIVPrefix,
            chunkSize = CryptoEngine.DEFAULT_CHUNK_SIZE,
            flags = CryptoEngine.V3_FLAG_KEYFILE,
            manifestOffset = manifestOffset
        )

        assertEquals(CryptoEngine.V3_HEADER_SIZE, headerBytes.size)

        val parsed = CryptoEngine.parseVaultHeader(ByteArrayInputStream(headerBytes))
        assertEquals(VaultVersion.V3, parsed.version)
        assertTrue(parsed.requiresKeyfile)
        assertEquals(CryptoEngine.DEFAULT_CHUNK_SIZE, parsed.chunkSize)
        assertEquals(manifestOffset, parsed.manifestOffset)
        assertArrayEquals(salt, parsed.salt)
        assertArrayEquals(baseIVPrefix, parsed.baseIVPrefix)
    }

    @Test
    fun testV3ManifestEnvelopeEncryptionAndDecryption() {
        val password = "TestPassword789!".toCharArray()
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val key = CryptoEngine.deriveKeyV3(password, salt, null)

        val catalogJson = """{"version":3,"totalSize":1024,"fileCount":2,"files":[{"path":"test.txt","size":500}]}"""
        val envelope = CryptoEngine.encryptManifestEnvelope(catalogJson, key, salt)

        val decryptedJson = CryptoEngine.decryptManifestEnvelope(envelope, key)
        assertEquals(catalogJson, decryptedJson)
    }

    @Test
    fun testPasswordEvaluator() {
        assertEquals(PasswordStrengthLevel.VERY_WEAK, PasswordEvaluator.evaluate(""))
        assertEquals(PasswordStrengthLevel.VERY_WEAK, PasswordEvaluator.evaluate("short"))
        assertEquals(PasswordStrengthLevel.WEAK, PasswordEvaluator.evaluate("abc123"))
        assertEquals(PasswordStrengthLevel.FAIR, PasswordEvaluator.evaluate("mediumPw1"))
        assertEquals(PasswordStrengthLevel.VERY_STRONG, PasswordEvaluator.evaluate("VeryStrongPassword123!@#"))
    }
}
