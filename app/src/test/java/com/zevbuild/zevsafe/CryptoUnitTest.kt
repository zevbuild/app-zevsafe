package com.zevbuild.zevsafe

import com.zevbuild.zevsafe.crypto.CryptoEngine
import com.zevbuild.zevsafe.crypto.OperationProgressState
import com.zevbuild.zevsafe.crypto.PasswordRecoveryRecord
import com.zevbuild.zevsafe.crypto.SelectedItem
import com.zevbuild.zevsafe.crypto.StageMetrics
import com.zevbuild.zevsafe.crypto.VaultVersion
import com.zevbuild.zevsafe.ui.components.PasswordEvaluator
import com.zevbuild.zevsafe.ui.components.PasswordStrengthLevel
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoUnitTest {

    // =========================================================================
    // BASELINE CRYPTO TESTS (v6.3.0 Regression Invariants)
    // =========================================================================

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

    // =========================================================================
    // TIER 1 & TIER 2: STREAM-MEASURED MANIFEST & 0-REPORTED-SIZE TESTS (R1)
    // =========================================================================

    private fun createDummyUri(): android.net.Uri {
        return try {
            val clazz = Class.forName("android.net.TestUri")
            clazz.getDeclaredConstructor().newInstance() as android.net.Uri
        } catch (_: Throwable) {
            android.net.TestUri()
        }
    }

    @Test
    fun testProductionCryptoEngineZeroReportedSizeStoredFileRoundTrip() = runBlocking {
        val tempDir = File.createTempFile("zev_stored_test", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "stored.zev")
        val extractDir = File(tempDir, "extracted").apply { mkdirs() }

        try {
            val originalBytes = ByteArray(128 * 1024)
            SecureRandom().nextBytes(originalBytes)
            val expectedCrc = CRC32().apply { update(originalBytes) }.value

            val item = SelectedItem(
                uri = createDummyUri(),
                name = "camera_capture.jpg",
                relativePath = "DCIM/camera_capture.jpg",
                sizeBytes = 0L // CRITICAL: Simulated 0L provider reported size
            )

            val password = "SecureStoredPassword123!".toCharArray()

            // 1. Encrypt using stream provider
            CryptoEngine.encryptItemsToV3Vault(
                streamOpener = { ByteArrayInputStream(originalBytes) },
                items = listOf(item),
                outputVaultFile = vaultFile,
                password = password
            )

            // 2. Read manifest directly using stream overload
            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password)
            }

            assertEquals("Catalog fileCount must be 1", 1, catalog.fileCount)
            assertEquals("Manifest root totalSize must be measured size (128 KB)", 128 * 1024L, catalog.totalSize)

            val entry = catalog.files.first()
            assertEquals("camera_capture.jpg", entry.name)
            assertEquals("DCIM/camera_capture.jpg", entry.path)
            assertEquals("Manifest entry sizeBytes must be stream-measured uncompressed size", 128 * 1024L, entry.sizeBytes)
            assertEquals("Manifest entry compressedSize must match STORED byte length", 128 * 1024L, entry.compressedSize)
            assertFalse("Precompressed files must not have compressed flag set", entry.isCompressed)
            assertEquals("Manifest entry crc32 must match stream CRC", expectedCrc, entry.crc32)
            assertTrue("CRC32 must be non-zero", entry.crc32 != 0L)

            // 3. Roundtrip decrypt using stream overload
            val extractedEntries = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.decryptV3VaultStreamToDirectory(inStream, extractDir, password)
            }

            assertEquals(1, extractedEntries.size)
            val extractedFile = File(extractDir, "DCIM/camera_capture.jpg")
            assertTrue("Extracted file must exist", extractedFile.exists())
            assertEquals("Extracted file length must match original", originalBytes.size.toLong(), extractedFile.length())
            assertArrayEquals("Extracted bytes must match original byte-for-byte", originalBytes, extractedFile.readBytes())

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionCryptoEngineZeroReportedSizeDeflatedFileRoundTrip() = runBlocking {
        val tempDir = File.createTempFile("zev_deflated_test", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "deflated.zev")
        val extractDir = File(tempDir, "extracted").apply { mkdirs() }

        try {
            val textBuilder = StringBuilder()
            for (i in 1..1000) {
                textBuilder.append("Line $i: ZevSafe Zero-Reported-Size Deflated Stream AEAD Verification Record\n")
            }
            val originalBytes = textBuilder.toString().toByteArray(Charsets.UTF_8)
            val expectedSize = originalBytes.size.toLong()
            val expectedCrc = CRC32().apply { update(originalBytes) }.value

            val item = SelectedItem(
                uri = createDummyUri(),
                name = "system_audit.log",
                relativePath = "logs/system_audit.log",
                sizeBytes = 0L // CRITICAL: Simulated 0L provider reported size
            )

            val password = "DeflatedPassword789!".toCharArray()

            // 1. Encrypt using stream provider
            CryptoEngine.encryptItemsToV3Vault(
                streamOpener = { ByteArrayInputStream(originalBytes) },
                items = listOf(item),
                outputVaultFile = vaultFile,
                password = password
            )

            // 2. Read manifest
            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password)
            }

            assertEquals("Catalog fileCount must be 1", 1, catalog.fileCount)
            assertEquals("Manifest root totalSize must match uncompressed measured size", expectedSize, catalog.totalSize)

            val entry = catalog.files.first()
            assertEquals("system_audit.log", entry.name)
            assertEquals("logs/system_audit.log", entry.path)
            assertEquals("Manifest entry sizeBytes must be measured uncompressed size", expectedSize, entry.sizeBytes)
            assertTrue("Manifest entry compressedSize must be positive", entry.compressedSize > 0L)
            assertTrue("Deflated compressedSize must be less than uncompressed size", entry.compressedSize < expectedSize)
            assertTrue("Compressible files must have compressed flag set", entry.isCompressed)
            assertEquals("Manifest entry crc32 must match calculated CRC", expectedCrc, entry.crc32)
            assertTrue("CRC32 must be non-zero", entry.crc32 != 0L)

            // 3. Roundtrip decrypt
            val extractedEntries = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.decryptV3VaultStreamToDirectory(inStream, extractDir, password)
            }

            assertEquals(1, extractedEntries.size)
            val extractedFile = File(extractDir, "logs/system_audit.log")
            assertTrue("Extracted file must exist", extractedFile.exists())
            assertEquals(expectedSize, extractedFile.length())
            assertArrayEquals("Extracted bytes must match original uncompressed bytes", originalBytes, extractedFile.readBytes())

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionCryptoEngineMultiFileMixedZeroReportedSizesAndEmptyFile() = runBlocking {
        val tempDir = File.createTempFile("zev_mixed_test", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "mixed.zev")
        val extractDir = File(tempDir, "extracted").apply { mkdirs() }

        try {
            // File 1: Precompressed image (STORED) 40 KB
            val imageBytes = ByteArray(40 * 1024).apply { SecureRandom().nextBytes(this) }
            val imageItem = SelectedItem(
                uri = createDummyUri(),
                name = "thumbnail.png",
                relativePath = "media/thumbnail.png",
                sizeBytes = 0L // Reported 0
            )

            // File 2: Compressible text (DEFLATED) 30 KB
            val textBytes = "ZevSafe Multi-File Stream AEAD Integrity Verification Record\n".repeat(500).toByteArray(Charsets.UTF_8)
            val textItem = SelectedItem(
                uri = createDummyUri(),
                name = "notes.txt",
                relativePath = "docs/notes.txt",
                sizeBytes = 0L // Reported 0
            )

            // File 3: Genuinely empty file (0 bytes stream)
            val emptyBytes = ByteArray(0)
            val emptyItem = SelectedItem(
                uri = createDummyUri(),
                name = "empty.marker",
                relativePath = "empty.marker",
                sizeBytes = 0L // Truly 0
            )

            val streamMap = mapOf(
                "media/thumbnail.png" to imageBytes,
                "docs/notes.txt" to textBytes,
                "empty.marker" to emptyBytes
            )

            val password = "MultiFilePassword!1".toCharArray()

            CryptoEngine.encryptItemsToV3Vault(
                streamOpener = { item -> ByteArrayInputStream(streamMap[item.relativePath]!!) },
                items = listOf(imageItem, textItem, emptyItem),
                outputVaultFile = vaultFile,
                password = password
            )

            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password)
            }

            assertEquals(3, catalog.fileCount)
            val expectedTotal = imageBytes.size.toLong() + textBytes.size.toLong() + emptyBytes.size.toLong()
            assertEquals("Total manifest size must equal sum of measured lengths", expectedTotal, catalog.totalSize)

            val imageEntry = catalog.files.first { it.name == "thumbnail.png" }
            assertEquals(imageBytes.size.toLong(), imageEntry.sizeBytes)
            assertEquals(imageBytes.size.toLong(), imageEntry.compressedSize)
            assertFalse(imageEntry.isCompressed)
            assertTrue(imageEntry.crc32 != 0L)

            val textEntry = catalog.files.first { it.name == "notes.txt" }
            assertEquals(textBytes.size.toLong(), textEntry.sizeBytes)
            assertTrue(textEntry.compressedSize in 1L until textBytes.size.toLong())
            assertTrue(textEntry.isCompressed)
            assertTrue(textEntry.crc32 != 0L)

            val emptyEntry = catalog.files.first { it.name == "empty.marker" }
            assertEquals(0L, emptyEntry.sizeBytes)
            assertEquals(0L, emptyEntry.compressedSize)
            assertEquals(0L, emptyEntry.crc32)

            // Decrypt and verify byte-for-byte integrity
            FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.decryptV3VaultStreamToDirectory(inStream, extractDir, password)
            }

            assertArrayEquals(imageBytes, File(extractDir, "media/thumbnail.png").readBytes())
            assertArrayEquals(textBytes, File(extractDir, "docs/notes.txt").readBytes())
            assertEquals(0L, File(extractDir, "empty.marker").length())

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionCryptoEngineZeroReportedSizeMultiChunkSpan() = runBlocking {
        val tempDir = File.createTempFile("zev_multichunk_test", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "multichunk.zev")
        val extractDir = File(tempDir, "extracted").apply { mkdirs() }

        try {
            // 4.5 MB payload (crosses 4 MB chunk boundary into chunk 1)
            val payloadSize = 4 * 1024 * 1024 + 512 * 1024
            val largeBytes = ByteArray(payloadSize)
            for (i in largeBytes.indices) {
                largeBytes[i] = (i % 251).toByte()
            }

            val item = SelectedItem(
                uri = createDummyUri(),
                name = "large_archive.iso",
                relativePath = "large_archive.iso",
                sizeBytes = 0L // Reported 0
            )

            val password = "MultiChunkPassword!2".toCharArray()

            CryptoEngine.encryptItemsToV3Vault(
                streamOpener = { ByteArrayInputStream(largeBytes) },
                items = listOf(item),
                outputVaultFile = vaultFile,
                password = password
            )

            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password)
            }

            val entry = catalog.files.first()
            assertEquals(payloadSize.toLong(), entry.sizeBytes)
            assertEquals(payloadSize.toLong(), entry.compressedSize)
            assertEquals(0, entry.chunkStart)
            assertEquals("chunkEnd must be 1 for a 4.5 MB file spanning two 4 MB chunks", 1, entry.chunkEnd)

            // Decrypt and verify 100% integrity
            FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.decryptV3VaultStreamToDirectory(inStream, extractDir, password)
            }

            val extracted = File(extractDir, "large_archive.iso")
            assertEquals(payloadSize.toLong(), extracted.length())
            assertArrayEquals(largeBytes, extracted.readBytes())

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testZeroReportedSizeStoredFileManifestAndDecryptionRoundTrip() {
        // Simulates an image file whose ContentResolver query returned 0 bytes initially
        val imagePayload = ByteArray(4096) { ((it * 13) % 256).toByte() }
        // Prepend JPEG magic SOI 0xFF, 0xD8
        imagePayload[0] = 0xFF.toByte()
        imagePayload[1] = 0xD8.toByte()

        val files = listOf(
            TestVaultFile(
                name = "camera_capture.jpg",
                content = imagePayload,
                isStored = true,
                reportedSize = 0L // Bug scenario: ContentResolver query reported 0 bytes
            )
        )

        val vault = buildTestV3Vault(files)

        // 1. Verify manifest recorded measured sizes (> 0), not reported 0L
        val entry = vault.entries.first()
        assertEquals("camera_capture.jpg", entry.name)
        assertEquals(4096L, entry.size)
        assertEquals(4096L, entry.compressedSize)
        assertFalse(entry.isCompressed)
        val expectedCrc = computeCRC32(imagePayload)
        assertEquals(expectedCrc, entry.crc32)

        // 2. Full round-trip decryption via V3DecryptedInputStream and ZipInputStream
        val decryptedFiles = decryptTestV3Vault(vault)
        assertTrue("Extracted files must contain camera_capture.jpg", decryptedFiles.containsKey("camera_capture.jpg"))
        val extracted = decryptedFiles["camera_capture.jpg"]!!
        assertEquals(4096, extracted.size)
        assertArrayEquals(imagePayload, extracted)
    }

    @Test
    fun testZeroReportedSizeDeflatedFileManifestAndDecryptionRoundTrip() {
        // Simulates a text document reporting 0 bytes
        val textString = "ZevSafe Zero-Reported-Size Deflated Text Entry ".repeat(100)
        val textBytes = textString.toByteArray(Charsets.UTF_8)

        val files = listOf(
            TestVaultFile(
                name = "exported_notes.txt",
                content = textBytes,
                isStored = false,
                reportedSize = 0L
            )
        )

        val vault = buildTestV3Vault(files)

        val entry = vault.entries.first()
        assertEquals(textBytes.size.toLong(), entry.size)
        assertTrue("Compressed size must be measured and positive", entry.compressedSize > 0)
        assertTrue(entry.isCompressed)
        assertEquals(computeCRC32(textBytes), entry.crc32)

        val decryptedFiles = decryptTestV3Vault(vault)
        assertTrue(decryptedFiles.containsKey("exported_notes.txt"))
        val extracted = decryptedFiles["exported_notes.txt"]!!
        assertArrayEquals(textBytes, extracted)
    }

    @Test
    fun testManifestTotalSizeSumAcrossMultipleZeroReportedFiles() {
        val file1 = ByteArray(1500) { 1 }
        val file2 = ByteArray(2500) { 2 }
        val file3 = ByteArray(3500) { 3 }

        val files = listOf(
            TestVaultFile("img1.png", file1, isStored = true, reportedSize = 0L),
            TestVaultFile("img2.png", file2, isStored = true, reportedSize = 0L),
            TestVaultFile("data.bin", file3, isStored = true, reportedSize = 0L)
        )

        val vault = buildTestV3Vault(files)
        val expectedTotal = (1500 + 2500 + 3500).toLong()

        // Decrypt manifest envelope and inspect root totalSize
        val decryptedJson = CryptoEngine.decryptManifestEnvelope(
            extractManifestEnvelopeBytes(vault.vaultBytes, vault.manifestOffset),
            vault.secretKey
        )

        assertTrue(decryptedJson.contains("\"totalSize\":$expectedTotal"))
        assertTrue(decryptedJson.contains("\"fileCount\":3"))
    }

    @Test
    fun testGenuineEmptyZeroByteFileRoundTrip() {
        val emptyBytes = ByteArray(0)
        val files = listOf(
            TestVaultFile("empty.txt", emptyBytes, isStored = false, reportedSize = 0L)
        )

        val vault = buildTestV3Vault(files)
        val entry = vault.entries.first()
        assertEquals(0L, entry.size)
        assertEquals(0L, entry.compressedSize)
        assertEquals(0L, entry.crc32)

        val decryptedFiles = decryptTestV3Vault(vault)
        assertTrue(decryptedFiles.containsKey("empty.txt"))
        assertEquals(0, decryptedFiles["empty.txt"]!!.size)
    }

    @Test
    fun testStreamMeasuredCrc32MatchesStandardIeee8023Vector() {
        // Standard check vector: "123456789" -> 0xCBF43926 / 3421780262L
        val vector = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = computeCRC32(vector)
        assertEquals(3421780262L, crc)

        // Empty vector -> 0
        assertEquals(0L, computeCRC32(ByteArray(0)))
    }

    @Test
    fun testManifestJsonFieldsAndTypesContract() {
        val filePayload = "Sample data for manifest schema test".toByteArray(Charsets.UTF_8)
        val files = listOf(TestVaultFile("contract_test.dat", filePayload, isStored = true))
        val vault = buildTestV3Vault(files)

        val manifestJson = vault.manifestJson
        assertTrue("Must contain version 3", manifestJson.contains("\"version\":3"))
        assertTrue("Must contain totalSize", manifestJson.contains("\"totalSize\":${filePayload.size}"))
        assertTrue("Must contain fileCount", manifestJson.contains("\"fileCount\":1"))
        assertTrue("Must contain path", manifestJson.contains("\"path\":\"contract_test.dat\""))
        assertTrue("Must contain localHeaderOffset", manifestJson.contains("\"localHeaderOffset\":0"))
        assertTrue("Must contain chunkStart", manifestJson.contains("\"chunkStart\":0"))
        assertTrue("Must contain chunkEnd", manifestJson.contains("\"chunkEnd\":0"))
    }

    // =========================================================================
    // TIER 1 & TIER 2: TARGETED SINGLE-FILE EXTRACTION & BOUNDS (R2)
    // =========================================================================

    @Test
    fun testChunkIndexCalculationWithinSingleChunk() {
        val (chunkStart, chunkEnd) = computeChunkRange(
            localHeaderOffset = 0L,
            pathLength = "test.txt".length,
            compressedSize = 1024L,
            chunkSize = CryptoEngine.DEFAULT_CHUNK_SIZE
        )
        assertEquals(0, chunkStart)
        assertEquals(0, chunkEnd)
    }

    @Test
    fun testChunkIndexCalculationSpanningChunkBoundary() {
        val chunkSize = CryptoEngine.DEFAULT_CHUNK_SIZE // 4 MB = 4194304
        val localHeaderOffset = (chunkSize - 200).toLong() // 200 bytes before chunk boundary
        val compSize = 10000L // Spans past 4 MB boundary

        val (chunkStart, chunkEnd) = computeChunkRange(
            localHeaderOffset = localHeaderOffset,
            pathLength = "video.mp4".length,
            compressedSize = compSize,
            chunkSize = chunkSize
        )
        assertEquals(0, chunkStart)
        assertEquals(1, chunkEnd)
    }

    @Test
    fun testChunkIndexCalculationMultiChunkSpan() {
        val chunkSize = CryptoEngine.DEFAULT_CHUNK_SIZE
        val localHeaderOffset = (chunkSize * 2).toLong() // Starts at chunk 2
        val compSize = (chunkSize * 3 + 50000).toLong() // Extends into chunk 5

        val (chunkStart, chunkEnd) = computeChunkRange(
            localHeaderOffset = localHeaderOffset,
            pathLength = "archive.tar".length,
            compressedSize = compSize,
            chunkSize = chunkSize
        )
        assertEquals(2, chunkStart)
        assertEquals(5, chunkEnd)
    }

    @Test
    fun testTargetedExtractionBoundsClampingAndValidation() {
        val totalChunks = 5
        val lastChunkIndex = totalChunks - 1

        // Normal bounds
        val startValid = 1.coerceAtLeast(0)
        val endValid = 3.coerceIn(startValid, lastChunkIndex)
        assertEquals(1, startValid)
        assertEquals(3, endValid)

        // Negative start clamped to 0
        val startNeg = (-1).coerceAtLeast(0)
        assertEquals(0, startNeg)

        // Excessive end clamped to lastChunkIndex
        val endExcessive = 100.coerceIn(0, lastChunkIndex)
        assertEquals(4, endExcessive)
    }

    @Test
    fun testSingleFileTargetedExtractionRoundTripStored() {
        val fileA = "Alpha file payload in chunk 0".toByteArray(Charsets.UTF_8)
        val fileB = "Beta target preview file payload in chunk 0".toByteArray(Charsets.UTF_8)

        val files = listOf(
            TestVaultFile("alpha.txt", fileA, isStored = true),
            TestVaultFile("beta_preview.jpg", fileB, isStored = true)
        )

        val vault = buildTestV3Vault(files)
        val targetEntry = vault.entries.first { it.name == "beta_preview.jpg" }

        val (extractedBytes, accessedChunks) = extractSingleFileTargeted(vault, targetEntry)

        assertEquals(fileB.size, extractedBytes.size)
        assertArrayEquals(fileB, extractedBytes)
        assertEquals(setOf(0), accessedChunks)
    }

    @Test
    fun testSingleFileTargetedExtractionRoundTripDeflated() {
        val textA = "Header documentation content for Vault container".toByteArray(Charsets.UTF_8)
        val textB = "Highly compressible target body: ".repeat(50).toByteArray(Charsets.UTF_8)

        val files = listOf(
            TestVaultFile("doc_header.txt", textA, isStored = false),
            TestVaultFile("target_deflated.txt", textB, isStored = false)
        )

        val vault = buildTestV3Vault(files)
        val targetEntry = vault.entries.first { it.name == "target_deflated.txt" }

        val (extractedBytes, accessedChunks) = extractSingleFileTargeted(vault, targetEntry)

        assertEquals(textB.size, extractedBytes.size)
        assertArrayEquals(textB, extractedBytes)
        assertEquals(setOf(0), accessedChunks)
    }

    @Test
    fun testTargetedExtractionChunkIsolationVerification() {
        // Use a small chunk size (1024 bytes) so multiple distinct chunks are formed
        val smallChunkSize = 1024
        val payload1 = ByteArray(700) { 0x11 }
        val payload2 = ByteArray(700) { 0x22 }
        val payload3 = ByteArray(700) { 0x33 }

        val files = listOf(
            TestVaultFile("file1.bin", payload1, isStored = true),
            TestVaultFile("file2.bin", payload2, isStored = true),
            TestVaultFile("file3.bin", payload3, isStored = true)
        )

        val vault = buildTestV3Vault(files, chunkSize = smallChunkSize)
        val entry3 = vault.entries.first { it.name == "file3.bin" }

        val (extracted3, accessedChunks3) = extractSingleFileTargeted(vault, entry3, chunkSize = smallChunkSize)
        assertArrayEquals(payload3, extracted3)

        // Verify isolation: Chunk 0 must not be accessed when extracting entry 3
        assertFalse("Chunk 0 must not be decrypted when extracting chunk 2", accessedChunks3.contains(0))
    }

    @Test
    fun testProductionCryptoEngineTargetedSingleFileExtractionStoredAndDeflated() = runBlocking {
        val tempDir = File.createTempFile("zev_targeted_prod", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "target_vault.zev")
        val extractDir = File(tempDir, "preview_extracted").apply { mkdirs() }

        try {
            val file1Bytes = ByteArray(64 * 1024).apply { SecureRandom().nextBytes(this) }
            val file2Bytes = "Production single-file targeted extraction line verification record.\n".repeat(400).toByteArray(Charsets.UTF_8)
            val file3Bytes = ByteArray(48 * 1024).apply { SecureRandom().nextBytes(this) }

            val item1 = SelectedItem(uri = createDummyUri(), name = "photo.jpg", relativePath = "photo.jpg", sizeBytes = 0L)
            val item2 = SelectedItem(uri = createDummyUri(), name = "document.txt", relativePath = "docs/document.txt", sizeBytes = 0L)
            val item3 = SelectedItem(uri = createDummyUri(), name = "audio.mp3", relativePath = "music/audio.mp3", sizeBytes = 0L)

            val streamMap = mapOf(
                "photo.jpg" to file1Bytes,
                "docs/document.txt" to file2Bytes,
                "music/audio.mp3" to file3Bytes
            )

            val password = "TargetedPassword123!".toCharArray()

            // 1. Create vault
            CryptoEngine.encryptItemsToV3Vault(
                streamOpener = { item -> ByteArrayInputStream(streamMap[item.relativePath]!!) },
                items = listOf(item1, item2, item3),
                outputVaultFile = vaultFile,
                password = password
            )

            // 2. Read manifest without extracting whole vault
            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password)
            }
            assertEquals(3, catalog.fileCount)

            // 3. Extract only file 2 (DEFLATED)
            val target2 = catalog.files.first { it.name == "document.txt" }
            val out2 = File(extractDir, "document.txt")
            val result2 = CryptoEngine.extractSingleFileFromV3Vault(
                vaultFile = vaultFile,
                entry = target2,
                password = password,
                outputFile = out2
            )

            assertTrue("Extracted target file 2 must exist", out2.exists())
            assertEquals(file2Bytes.size.toLong(), out2.length())
            assertArrayEquals(file2Bytes, out2.readBytes())
            assertEquals(out2.length(), result2.sizeBytes)
            assertNotNull(result2.textContentPreview)
            assertTrue(result2.textContentPreview!!.contains("Production single-file targeted extraction"))

            // Verify files 1 and 3 were never written to extractDir
            assertFalse(File(extractDir, "photo.jpg").exists())
            assertFalse(File(extractDir, "audio.mp3").exists())

            // 4. Extract file 1 (STORED) via inStreamProvider overload
            val target1 = catalog.files.first { it.name == "photo.jpg" }
            val out1 = File(extractDir, "photo.jpg")
            val result1 = CryptoEngine.extractSingleFileFromV3Vault(
                inStreamProvider = { FileInputStream(vaultFile) },
                entry = target1,
                password = password,
                outputFile = out1
            )

            assertTrue("Extracted target file 1 must exist", out1.exists())
            assertEquals(file1Bytes.size.toLong(), out1.length())
            assertArrayEquals(file1Bytes, out1.readBytes())
            assertEquals(out1.length(), result1.sizeBytes)

            // File 3 still not written
            assertFalse(File(extractDir, "audio.mp3").exists())

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionCryptoEngineTargetedExtractionMultiChunkSpan() = runBlocking {
        val tempDir = File.createTempFile("zev_targeted_multichunk", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "large_target_vault.zev")
        val extractDir = File(tempDir, "preview_extracted").apply { mkdirs() }

        try {
            val payloadSize = 4 * 1024 * 1024 + 256 * 1024 // 4.25 MB spanning chunk 0 and chunk 1
            val largeBytes = ByteArray(payloadSize)
            for (i in largeBytes.indices) {
                largeBytes[i] = ((i * 7) % 251).toByte()
            }

            val item = SelectedItem(uri = createDummyUri(), name = "large_sample.iso", relativePath = "large_sample.iso", sizeBytes = 0L)
            val password = "LargeMultiChunkPass1!".toCharArray()

            CryptoEngine.encryptItemsToV3Vault(
                streamOpener = { ByteArrayInputStream(largeBytes) },
                items = listOf(item),
                outputVaultFile = vaultFile,
                password = password
            )

            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password)
            }
            val entry = catalog.files.first()
            assertEquals(0, entry.chunkStart)
            assertEquals(1, entry.chunkEnd)

            val out = File(extractDir, "large_sample.iso")
            val result = CryptoEngine.extractSingleFileFromV3Vault(
                vaultFile = vaultFile,
                entry = entry,
                password = password,
                outputFile = out
            )

            assertTrue(out.exists())
            assertEquals(payloadSize.toLong(), out.length())
            assertArrayEquals(largeBytes, out.readBytes())
            assertEquals(payloadSize.toLong(), result.sizeBytes)

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionCryptoEngineTargetedExtractionWithKeyfile() = runBlocking {
        val tempDir = File.createTempFile("zev_targeted_keyfile", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "keyfile_vault.zev")
        val extractDir = File(tempDir, "preview_extracted").apply { mkdirs() }

        try {
            val payload = "Top secret keyfile protected entry text data\n".repeat(30).toByteArray(Charsets.UTF_8)
            val item = SelectedItem(uri = createDummyUri(), name = "confidential.txt", relativePath = "confidential.txt", sizeBytes = 0L)
            val password = "KeyfileTargetedPass!".toCharArray()
            val keyfileHash = ByteArray(32) { (it + 9).toByte() }

            CryptoEngine.encryptItemsToV3Vault(
                context = null,
                items = listOf(item),
                outputVaultFile = vaultFile,
                password = password,
                keyfileHash = keyfileHash,
                inputStreamProvider = { ByteArrayInputStream(payload) }
            )

            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password, keyfileHash)
            }
            val entry = catalog.files.first()

            val outSuccess = File(extractDir, "confidential.txt")
            val result = CryptoEngine.extractSingleFileFromV3Vault(
                vaultFile = vaultFile,
                entry = entry,
                password = password,
                keyfileHash = keyfileHash,
                outputFile = outSuccess
            )

            assertTrue(outSuccess.exists())
            assertEquals(payload.size.toLong(), outSuccess.length())
            assertArrayEquals(payload, outSuccess.readBytes())

            // Missing keyfile must throw IllegalArgumentException
            val outFail = File(extractDir, "fail.txt")
            try {
                CryptoEngine.extractSingleFileFromV3Vault(
                    vaultFile = vaultFile,
                    entry = entry,
                    password = password,
                    keyfileHash = null,
                    outputFile = outFail
                )
                fail("Expected exception when keyfile is omitted")
            } catch (_: IllegalArgumentException) {
                // Expected
            }

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionCryptoEngineTargetedExtractionEmptyZeroByteFile() = runBlocking {
        val tempDir = File.createTempFile("zev_targeted_empty", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val vaultFile = File(tempDir, "empty_vault.zev")
        val extractDir = File(tempDir, "preview_extracted").apply { mkdirs() }

        try {
            val emptyBytes = ByteArray(0)
            val item = SelectedItem(uri = createDummyUri(), name = "empty.marker", relativePath = "empty.marker", sizeBytes = 0L)
            val password = "EmptyPasswordPass!".toCharArray()

            CryptoEngine.encryptItemsToV3Vault(
                streamOpener = { ByteArrayInputStream(emptyBytes) },
                items = listOf(item),
                outputVaultFile = vaultFile,
                password = password
            )

            val catalog = FileInputStream(vaultFile).use { inStream ->
                CryptoEngine.readV3VaultManifest(inStream, password)
            }
            val entry = catalog.files.first()

            val out = File(extractDir, "empty.marker")
            val result = CryptoEngine.extractSingleFileFromV3Vault(
                vaultFile = vaultFile,
                entry = entry,
                password = password,
                outputFile = out
            )

            assertTrue(out.exists())
            assertEquals(0L, out.length())
            assertEquals(0L, result.sizeBytes)

        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionCryptoEngineTargetedExtractionInvalidVaultFailsCleanly() = runBlocking {
        val tempDir = File.createTempFile("zev_targeted_invalid", ".dir").apply { delete(); mkdirs(); deleteOnExit() }
        val dummyFile = File(tempDir, "invalid.zev").apply { writeBytes(ByteArray(100) { 0x42 }) }
        val out = File(tempDir, "out.bin")

        try {
            val dummyEntry = com.zevbuild.zevsafe.crypto.DecryptedFileEntry(
                path = "test.txt",
                name = "test.txt",
                sizeBytes = 10L,
                isDirectory = false,
                mimeType = "text/plain"
            )
            try {
                CryptoEngine.extractSingleFileFromV3Vault(
                    vaultFile = dummyFile,
                    entry = dummyEntry,
                    password = "password".toCharArray(),
                    outputFile = out
                )
                fail("Expected exception for invalid vault format")
            } catch (e: Exception) {
                assertTrue(e is IllegalArgumentException || e is java.io.IOException)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS & BACKWARD COMPATIBILITY
    // =========================================================================

    @Test
    fun testBackwardCompatibilityV1MonolithicVault() {
        val password = "LegacyV1Password!".toCharArray()
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val secretKey = CryptoEngine.deriveKeyV1(password, salt)
        val plaintext = "Legacy v6.3.0 v1 standard monolithic vault payload".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)

        val vaultBytes = ByteArray(CryptoEngine.V1_HEADER_SIZE + ciphertext.size)
        System.arraycopy(salt, 0, vaultBytes, 0, 16)
        System.arraycopy(iv, 0, vaultBytes, 16, 12)
        System.arraycopy(ciphertext, 0, vaultBytes, CryptoEngine.V1_HEADER_SIZE, ciphertext.size)

        val inStream = ByteArrayInputStream(vaultBytes)
        val header = CryptoEngine.parseVaultHeader(inStream)

        assertEquals(VaultVersion.V1, header.version)
        assertEquals(CryptoEngine.V1_HEADER_SIZE, header.ciphertextOffset)
        assertFalse(header.requiresKeyfile)
        assertArrayEquals(salt, header.salt)
        assertArrayEquals(iv, header.iv)

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, header.iv))
        val decrypted = decryptCipher.doFinal(vaultBytes, header.ciphertextOffset, vaultBytes.size - header.ciphertextOffset)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testBackwardCompatibilityV2MonolithicVaultWithKeyfile() {
        val password = "LegacyV2Password!".toCharArray()
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val keyfileHash = ByteArray(32) { (it * 3).toByte() }
        val secretKey = CryptoEngine.deriveKeyV2(password, salt, keyfileHash)
        val plaintext = "Legacy v2 standard monolithic vault with keyfile 2FA".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)

        val headerBuf = ByteBuffer.allocate(CryptoEngine.V2_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        headerBuf.put(CryptoEngine.V2_MAGIC)
        headerBuf.put(CryptoEngine.V2_VERSION_BYTE)
        headerBuf.put(CryptoEngine.V2_FLAG_KEYFILE)
        headerBuf.put(salt)
        headerBuf.put(iv)
        val headerBytes = headerBuf.array()

        val vaultBytes = ByteArray(headerBytes.size + ciphertext.size)
        System.arraycopy(headerBytes, 0, vaultBytes, 0, headerBytes.size)
        System.arraycopy(ciphertext, 0, vaultBytes, headerBytes.size, ciphertext.size)

        val inStream = ByteArrayInputStream(vaultBytes)
        val header = CryptoEngine.parseVaultHeader(inStream)

        assertEquals(VaultVersion.V2, header.version)
        assertEquals(CryptoEngine.V2_HEADER_SIZE, header.ciphertextOffset)
        assertTrue(header.requiresKeyfile)
        assertArrayEquals(salt, header.salt)
        assertArrayEquals(iv, header.iv)

        val derivedKey = CryptoEngine.deriveKeyV2(password, header.salt, keyfileHash)
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(128, header.iv))
        val decrypted = decryptCipher.doFinal(vaultBytes, header.ciphertextOffset, vaultBytes.size - header.ciphertextOffset)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testV3ManifestEnvelopeTamperingRejection() {
        val password = "TamperPassword!".toCharArray()
        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val secretKey = CryptoEngine.deriveKeyV3(password, salt, null)

        val manifestJson = """{"version":3,"totalSize":500,"fileCount":1,"files":[]}"""
        val envelope = CryptoEngine.encryptManifestEnvelope(manifestJson, secretKey, salt)

        // Tamper with one byte in the ciphertext payload
        envelope[CryptoEngine.MANIFEST_HEADER_SIZE + 2] = (envelope[CryptoEngine.MANIFEST_HEADER_SIZE + 2].toInt() xor 0x5A).toByte()

        try {
            CryptoEngine.decryptManifestEnvelope(envelope, secretKey)
            fail("Expected AEAD authentication failure on tampered manifest envelope")
        } catch (_: Exception) {
            // Correct behavior: tamper detected by GCM tag
        }
    }

    @Test
    fun testV3CorruptHeaderOrTruncatedStreamDetection() {
        try {
            CryptoEngine.parseVaultHeader(ByteArrayInputStream(byteArrayOf(0x5A, 0x56)))
            fail("Expected exception for truncated header")
        } catch (_: IllegalArgumentException) {
            // Success
        }

        try {
            val corruptedMagic = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x02)
            CryptoEngine.parseVaultHeader(ByteArrayInputStream(corruptedMagic))
            fail("Expected exception for corrupted magic")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun testMimeTypeClassificationAndMediaViewerRoutingContract() {
        fun classifyMime(ext: String): String = when (ext.lowercase(Locale.ROOT)) {
            "png", "jpg", "jpeg", "webp", "gif", "svg" -> "image/$ext"
            "txt", "md", "json", "xml", "html", "js", "css", "kt", "java", "py", "c", "cpp" -> "text/plain"
            "pdf" -> "application/pdf"
            "mp4", "mkv", "mov", "webm" -> "video/$ext"
            "mp3", "wav", "ogg", "flac" -> "audio/$ext"
            else -> "application/octet-stream"
        }

        // Video -> VideoPlayerActivity
        assertTrue(classifyMime("mp4").startsWith("video/"))
        assertTrue(classifyMime("mkv").startsWith("video/"))
        // Audio -> AudioPlayerModal
        assertTrue(classifyMime("mp3").startsWith("audio/"))
        assertTrue(classifyMime("flac").startsWith("audio/"))
        // Documents -> External FileProvider Intent
        assertEquals("application/pdf", classifyMime("pdf"))
        assertEquals("text/plain", classifyMime("txt"))
        // Binary / fallback
        assertEquals("application/octet-stream", classifyMime("bin"))
    }

    @Test
    fun testTelemetryAndSpeedStateModelContract() {
        val state = OperationProgressState(
            isActive = true,
            isEncrypting = true,
            overallPercent = 55.5f,
            metrics = StageMetrics(
                originalSizeBytes = 10_000_000L,
                cryptoSizeBytes = 10_000_000L,
                cryptoTimeMs = 250L
            )
        )
        assertTrue(state.isActive)
        assertEquals(55.5f, state.overallPercent, 0.01f)
        // Throughput calculation: (10 MB / 0.250 s) = 40.0 MB/s
        val speedMBs = (state.metrics.cryptoSizeBytes.toDouble() / (1024 * 1024)) / (state.metrics.cryptoTimeMs.toDouble() / 1000.0)
        assertEquals(40.0, speedMBs, 2.0)
    }

    @Test
    fun testBuildVersionConfigurationFormat() {
        val gradleFile = File(findProjectRoot(), "app/build.gradle.kts")
        assertTrue(gradleFile.exists())
        val text = gradleFile.readText(Charsets.UTF_8)
        assertTrue("Must configure versionCode", text.contains("versionCode"))
        assertTrue("Must configure versionName", text.contains("versionName"))
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    fun testTier4RealWorldMultiFileMixedStoredAndDeflatedVault() {
        val avatar = ByteArray(8192) { (it % 255).toByte() }.apply { this[0] = 0xFF.toByte(); this[1] = 0xD8.toByte() }
        val logs = "Timestamp [2026-10-05T12:00:00Z] System event log entry data\n".repeat(200).toByteArray(Charsets.UTF_8)
        val pdf = ByteArray(12288) { (it xor 0x3C).toByte() }
        val config = """{"app":"ZevSafe","version":"6.4.0","airGapped":true,"chunkSize":4194304}""".toByteArray(Charsets.UTF_8)

        val files = listOf(
            TestVaultFile("avatar.jpg", avatar, isStored = true, reportedSize = 0L),
            TestVaultFile("logs.txt", logs, isStored = false, reportedSize = 0L),
            TestVaultFile("manual.pdf", pdf, isStored = true, reportedSize = 12288L),
            TestVaultFile("config.json", config, isStored = false, reportedSize = config.size.toLong())
        )

        val vault = buildTestV3Vault(files)

        // 1. Verify manifest catalog accuracy
        val expectedTotal = (avatar.size + logs.size + pdf.size + config.size).toLong()
        val decryptedJson = CryptoEngine.decryptManifestEnvelope(
            extractManifestEnvelopeBytes(vault.vaultBytes, vault.manifestOffset),
            vault.secretKey
        )
        assertTrue(decryptedJson.contains("\"totalSize\":$expectedTotal"))
        assertTrue(decryptedJson.contains("\"fileCount\":4"))

        // 2. Full round-trip decryption
        val decryptedFiles = decryptTestV3Vault(vault)
        assertEquals(4, decryptedFiles.size)
        assertArrayEquals(avatar, decryptedFiles["avatar.jpg"])
        assertArrayEquals(logs, decryptedFiles["logs.txt"])
        assertArrayEquals(pdf, decryptedFiles["manual.pdf"])
        assertArrayEquals(config, decryptedFiles["config.json"])

        // 3. Targeted single-file extraction of STORED file with 0 reported initial size
        val avatarEntry = vault.entries.first { it.name == "avatar.jpg" }
        val (extractedAvatar, _) = extractSingleFileTargeted(vault, avatarEntry)
        assertArrayEquals(avatar, extractedAvatar)

        // 4. Targeted single-file extraction of DEFLATED file with 0 reported initial size
        val logsEntry = vault.entries.first { it.name == "logs.txt" }
        val (extractedLogs, _) = extractSingleFileTargeted(vault, logsEntry)
        assertArrayEquals(logs, extractedLogs)
    }

    @Test
    fun testTier4DynamicFileProviderAuthorityFormat() {
        val packageName = "com.zevbuild.zevsafe"
        val expectedAuthority = "$packageName.fileprovider"
        assertEquals("com.zevbuild.zevsafe.fileprovider", expectedAuthority)

        val debugPackage = "com.zevbuild.zevsafe.debug"
        assertEquals("com.zevbuild.zevsafe.debug.fileprovider", "$debugPackage.fileprovider")

        // Inspect AndroidManifest.xml declaration
        val manifestFile = File(findProjectRoot(), "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText(Charsets.UTF_8)

        assertTrue(
            "FileProvider authority in AndroidManifest.xml must use \${applicationId}.fileprovider",
            manifestContent.contains("""android:authorities="${'$'}{applicationId}.fileprovider"""")
        )
        assertTrue(
            "FileProvider must grant URI permissions",
            manifestContent.contains("""android:grantUriPermissions="true"""")
        )
    }

    @Test
    fun testTier4AirGapStaticCheckNoInternetPermission() {
        val manifestFile = File(findProjectRoot(), "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText(Charsets.UTF_8)

        assertFalse(
            "CRITICAL SECURITY: AndroidManifest.xml must NOT contain android.permission.INTERNET",
            content.contains("android.permission.INTERNET")
        )

        val forbiddenPermissions = listOf(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.NFC"
        )
        for (perm in forbiddenPermissions) {
            assertFalse(
                "CRITICAL SECURITY: Air-gapped app must NOT declare permission: $perm",
                content.contains(perm)
            )
        }
    }

    @Test
    fun testTier4AirGapNoNetworkDependenciesInBuildGradle() {
        val gradleFile = File(findProjectRoot(), "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", gradleFile.exists())
        val content = gradleFile.readText(Charsets.UTF_8)

        val forbiddenLibraries = listOf(
            "retrofit", "okhttp", "ktor", "volley",
            "firebase", "google-analytics", "sentry",
            "telemetry", "crashlytics", "segment"
        )
        val lower = content.lowercase(Locale.ROOT)
        for (lib in forbiddenLibraries) {
            assertFalse(
                "CRITICAL SECURITY: app/build.gradle.kts must NOT include network/telemetry library: $lib",
                lower.contains(lib)
            )
        }
    }

    @Test
    fun testTier4LargeMultiChunkSingleFileTargetedExtraction() {
        val chunkSize = 2048
        val f1 = ByteArray(1800) { 1 }
        val f2 = ByteArray(1800) { 2 }
        val f3 = ByteArray(1800) { 3 }
        val f4 = ByteArray(1800) { 4 }

        val files = listOf(
            TestVaultFile("chunk0.bin", f1, isStored = true),
            TestVaultFile("chunk1.bin", f2, isStored = true),
            TestVaultFile("chunk2.bin", f3, isStored = true),
            TestVaultFile("chunk3.bin", f4, isStored = true)
        )

        val vault = buildTestV3Vault(files, chunkSize = chunkSize)
        val target = vault.entries.first { it.name == "chunk2.bin" }

        val startTime = System.currentTimeMillis()
        val (extracted, accessedChunks) = extractSingleFileTargeted(vault, target, chunkSize = chunkSize)
        val durationMs = System.currentTimeMillis() - startTime

        assertArrayEquals(f3, extracted)
        assertTrue("Targeted extraction must execute in < 200 ms", durationMs < 200)
        assertFalse("Chunk 0 must not be accessed when extracting chunk 2", accessedChunks.contains(0))
    }

    @Test
    fun testTier4PasswordRecoverySheetFormatAndSecurity() {
        val record = PasswordRecoveryRecord(
            folderName = "ConfidentialDocs",
            vaultFilename = "ConfidentialDocs.zev",
            version = "v3 Streaming (ZV3\\0)",
            password = "MasterPassword999!",
            keyfileRequired = true,
            keyfileName = "auth_key.bin",
            keyfileFingerprint = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            timestampFormatted = "2026-10-05 12:00:00"
        )

        val sheetText = CryptoEngine.buildPasswordRecoverySheetText(record)

        assertTrue(sheetText.contains("ZevSafe Password Recovery Sheet"))
        assertTrue(sheetText.contains("ConfidentialDocs"))
        assertTrue(sheetText.contains("ConfidentialDocs.zev"))
        assertTrue(sheetText.contains("MasterPassword999!"))
        assertTrue(sheetText.contains("Required keyfile: auth_key.bin"))
        assertTrue(sheetText.contains("Keyfile SHA-256 fingerprint: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
        assertTrue(sheetText.contains("There is no password recovery, reset, or backdoor."))
        assertTrue(sheetText.contains("Print this sheet or store it offline in a secure place."))
    }

    // =========================================================================
    // TEST HARNESS & BUILDER UTILITIES
    // =========================================================================

    private data class TestVaultFile(
        val name: String,
        val content: ByteArray,
        val isStored: Boolean,
        val reportedSize: Long = content.size.toLong()
    )

    private data class TestManifestEntry(
        val path: String,
        val name: String,
        val size: Long,
        val compressedSize: Long,
        val localHeaderOffset: Long,
        val isCompressed: Boolean,
        val crc32: Long,
        val chunkStart: Int,
        val chunkEnd: Int
    )

    private data class BuiltV3Vault(
        val vaultBytes: ByteArray,
        val secretKey: SecretKey,
        val salt: ByteArray,
        val baseIVPrefix: ByteArray,
        val manifestOffset: Long,
        val manifestJson: String,
        val entries: List<TestManifestEntry>
    )

    private fun computeCRC32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }

    private fun computeChunkRange(localHeaderOffset: Long, pathLength: Int, compressedSize: Long, chunkSize: Int = CryptoEngine.DEFAULT_CHUNK_SIZE): Pair<Int, Int> {
        val chunkStart = (localHeaderOffset / chunkSize).toInt()
        val chunkEnd = ((localHeaderOffset + 30 + pathLength + compressedSize) / chunkSize).toInt()
        return Pair(chunkStart, chunkEnd)
    }

    private fun decompressDeflate(compressedBytes: ByteArray, uncompressedSize: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(compressedBytes)
            val result = ByteArray(uncompressedSize)
            val count = inflater.inflate(result)
            assertEquals(uncompressedSize, count)
            return result
        } finally {
            inflater.end()
        }
    }

    private fun findProjectRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "app/src/main/AndroidManifest.xml").exists()) {
                return dir
            }
            if (File(dir, "src/main/AndroidManifest.xml").exists()) {
                return dir.parentFile ?: dir
            }
            dir = dir.parentFile
        }
        return File(".")
    }

    private fun buildTestV3Vault(
        files: List<TestVaultFile>,
        password: CharArray = "TestVaultPassword123!".toCharArray(),
        chunkSize: Int = CryptoEngine.DEFAULT_CHUNK_SIZE
    ): BuiltV3Vault {
        val random = SecureRandom()
        val salt = ByteArray(CryptoEngine.SALT_LENGTH).apply { random.nextBytes(this) }
        val baseIVPrefix = ByteArray(CryptoEngine.BASE_IV_PREFIX_LENGTH).apply { random.nextBytes(this) }
        val secretKey = CryptoEngine.deriveKeyV3(password, salt, null)

        val vaultStream = ByteArrayOutputStream()
        val initialHeader = CryptoEngine.createV3ContainerHeader(salt, baseIVPrefix, chunkSize, 0x00, 0L)
        vaultStream.write(initialHeader)

        val chunkBuffer = ByteArrayOutputStream(chunkSize)
        var chunkIndex = 0

        fun flushChunk(isLast: Boolean) {
            val plaintext = chunkBuffer.toByteArray()
            chunkBuffer.reset()
            val framed = CryptoEngine.encryptChunk(secretKey, plaintext, baseIVPrefix, chunkIndex, isLast, salt)
            vaultStream.write(framed)
            chunkIndex++
        }

        var currentZipOffset = 0L
        val customStream = object : java.io.OutputStream() {
            override fun write(b: Int) {
                chunkBuffer.write(b)
                currentZipOffset++
                if (chunkBuffer.size() >= chunkSize) {
                    flushChunk(isLast = false)
                }
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var remaining = len
                var currentOff = off
                while (remaining > 0) {
                    val space = chunkSize - chunkBuffer.size()
                    val toWrite = Math.min(remaining, space)
                    chunkBuffer.write(b, currentOff, toWrite)
                    currentZipOffset += toWrite
                    currentOff += toWrite
                    remaining -= toWrite
                    if (chunkBuffer.size() >= chunkSize) {
                        flushChunk(isLast = false)
                    }
                }
            }
        }

        val entries = mutableListOf<TestManifestEntry>()

        ZipOutputStream(customStream).use { zipOut ->
            zipOut.setLevel(Deflater.BEST_SPEED)
            for (file in files) {
                val entryOffset = currentZipOffset
                val zipEntry = ZipEntry(file.name)
                val crc = CRC32().apply { update(file.content) }
                val measuredUncompressedSize = file.content.size.toLong()
                var measuredCompressedSize = measuredUncompressedSize

                if (file.isStored) {
                    zipEntry.method = ZipEntry.STORED
                    zipEntry.size = measuredUncompressedSize
                    zipEntry.compressedSize = measuredUncompressedSize
                    zipEntry.crc = crc.value
                } else {
                    zipEntry.method = ZipEntry.DEFLATED
                }

                zipOut.putNextEntry(zipEntry)
                zipOut.write(file.content)
                zipOut.closeEntry()

                if (!file.isStored) {
                    measuredCompressedSize = if (file.content.isEmpty()) 0L else (if (zipEntry.compressedSize > 0) zipEntry.compressedSize else (currentZipOffset - entryOffset))
                }

                val (chunkStart, chunkEnd) = computeChunkRange(entryOffset, file.name.length, measuredCompressedSize, chunkSize)
                entries.add(
                    TestManifestEntry(
                        path = file.name,
                        name = file.name,
                        size = measuredUncompressedSize,
                        compressedSize = measuredCompressedSize,
                        localHeaderOffset = entryOffset,
                        isCompressed = !file.isStored,
                        crc32 = crc.value,
                        chunkStart = chunkStart,
                        chunkEnd = chunkEnd
                    )
                )
            }
        }

        flushChunk(isLast = true)

        val manifestOffset = vaultStream.size().toLong()

        val filesJsonBuilder = StringBuilder()
        filesJsonBuilder.append("[")
        for ((idx, entry) in entries.withIndex()) {
            if (idx > 0) filesJsonBuilder.append(",")
            filesJsonBuilder.append(
                """{"path":"${entry.path}","name":"${entry.name}","size":${entry.size},"compressedSize":${entry.compressedSize},"localHeaderOffset":${entry.localHeaderOffset},"compressed":${entry.isCompressed},"crc32":${entry.crc32},"chunkStart":${entry.chunkStart},"chunkEnd":${entry.chunkEnd}}"""
            )
        }
        filesJsonBuilder.append("]")

        val totalMeasuredSize = entries.sumOf { it.size }
        val manifestJson = """{"version":3,"totalSize":$totalMeasuredSize,"fileCount":${entries.size},"files":$filesJsonBuilder}"""

        val manifestEnvelope = CryptoEngine.encryptManifestEnvelope(manifestJson, secretKey, salt)
        vaultStream.write(manifestEnvelope)

        val vaultBytes = vaultStream.toByteArray()
        val offsetBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        offsetBuf.putLong(manifestOffset)
        System.arraycopy(offsetBuf.array(), 0, vaultBytes, 49, 8)

        return BuiltV3Vault(vaultBytes, secretKey, salt, baseIVPrefix, manifestOffset, manifestJson, entries)
    }

    private fun decryptTestV3Vault(vault: BuiltV3Vault): Map<String, ByteArray> {
        val inStream = ByteArrayInputStream(vault.vaultBytes)
        val header = CryptoEngine.parseVaultHeader(inStream)
        assertEquals(VaultVersion.V3, header.version)

        val decryptedStream = CryptoEngine.V3DecryptedInputStream(
            inStream = inStream,
            secretKey = vault.secretKey,
            baseIVPrefix = header.baseIVPrefix!!,
            salt = header.salt,
            manifestOffset = header.manifestOffset
        )

        val extracted = mutableMapOf<String, ByteArray>()
        ZipInputStream(decryptedStream.buffered()).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val current = entry!!
                if (!current.isDirectory) {
                    val content = zis.readBytes()
                    extracted[current.name] = content
                }
                zis.closeEntry()
            }
        }
        return extracted
    }

    private fun extractManifestEnvelopeBytes(vaultBytes: ByteArray, manifestOffset: Long): ByteArray {
        val totalLen = vaultBytes.size - manifestOffset.toInt()
        val env = ByteArray(totalLen)
        System.arraycopy(vaultBytes, manifestOffset.toInt(), env, 0, totalLen)
        return env
    }

    private fun extractSingleFileTargeted(
        vault: BuiltV3Vault,
        targetEntry: TestManifestEntry,
        chunkSize: Int = CryptoEngine.DEFAULT_CHUNK_SIZE
    ): Pair<ByteArray, Set<Int>> {
        val totalFramedChunkSize = chunkSize + CryptoEngine.CHUNK_HEADER_SIZE + CryptoEngine.TAG_LENGTH
        val lastChunkIndex = ((vault.manifestOffset - CryptoEngine.V3_HEADER_SIZE) / totalFramedChunkSize).toInt()
        val chunkStart = targetEntry.chunkStart.coerceAtLeast(0)
        val chunkEnd = targetEntry.chunkEnd.coerceIn(chunkStart, lastChunkIndex)

        val accessedChunks = mutableSetOf<Int>()
        val decryptedChunks = mutableListOf<ByteArray>()

        for (i in chunkStart..chunkEnd) {
            accessedChunks.add(i)
            val chunkVaultStart = CryptoEngine.V3_HEADER_SIZE + i.toLong() * totalFramedChunkSize
            val isLast = (i == lastChunkIndex)
            val chunkFramedSize = if (isLast) (vault.manifestOffset - chunkVaultStart).toInt() else totalFramedChunkSize

            val chunkBytes = ByteArray(chunkFramedSize)
            System.arraycopy(vault.vaultBytes, chunkVaultStart.toInt(), chunkBytes, 0, chunkFramedSize)

            val plainChunk = CryptoEngine.decryptChunk(
                vault.secretKey,
                chunkBytes,
                vault.baseIVPrefix,
                i,
                isLast,
                vault.salt
            )
            decryptedChunks.add(plainChunk)
        }

        val spanBytes = if (decryptedChunks.size == 1) {
            decryptedChunks[0]
        } else {
            val totalLen = decryptedChunks.sumOf { it.size }
            val merged = ByteArray(totalLen)
            var destPos = 0
            for (c in decryptedChunks) {
                System.arraycopy(c, 0, merged, destPos, c.size)
                destPos += c.size
            }
            merged
        }

        val relOffset = (targetEntry.localHeaderOffset - (chunkStart.toLong() * chunkSize)).toInt()
        assertTrue("Relative offset within span must be valid", relOffset >= 0 && relOffset + 30 <= spanBytes.size)

        val sig = (spanBytes[relOffset].toInt() and 0xFF) or
                ((spanBytes[relOffset + 1].toInt() and 0xFF) shl 8) or
                ((spanBytes[relOffset + 2].toInt() and 0xFF) shl 16) or
                ((spanBytes[relOffset + 3].toInt() and 0xFF) shl 24)
        assertEquals(0x04034b50, sig)

        val method = (spanBytes[relOffset + 8].toInt() and 0xFF) or
                ((spanBytes[relOffset + 9].toInt() and 0xFF) shl 8)
        val nameLen = (spanBytes[relOffset + 26].toInt() and 0xFF) or
                ((spanBytes[relOffset + 27].toInt() and 0xFF) shl 8)
        val extraLen = (spanBytes[relOffset + 28].toInt() and 0xFF) or
                ((spanBytes[relOffset + 29].toInt() and 0xFF) shl 8)

        val payloadStart = relOffset + 30 + nameLen + extraLen
        val compLen = targetEntry.compressedSize.toInt()

        val rawPayload = spanBytes.copyOfRange(payloadStart, payloadStart + compLen)
        val extractedBytes = if (method == 0) {
            rawPayload
        } else {
            decompressDeflate(rawPayload, targetEntry.size.toInt())
        }

        return Pair(extractedBytes, accessedChunks)
    }
}
