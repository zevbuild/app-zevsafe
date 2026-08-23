package com.example.zevsafe.crypto

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    val V2_MAGIC = byteArrayOf(0x5A, 0x56, 0x32, 0x00) // "ZV2\0"
    const val V2_VERSION_BYTE: Byte = 0x02
    const val V2_FLAG_KEYFILE: Byte = 0x01
    const val V2_HEADER_SIZE = 50 // 4 (magic) + 1 (ver) + 1 (flags) + 32 (salt) + 12 (iv)

    const val V1_SALT_SIZE = 16
    const val V1_IV_SIZE = 12
    const val V1_HEADER_SIZE = 28 // 16 (salt) + 12 (iv)

    const val GCM_IV_SIZE = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val BUFFER_SIZE = 64 * 1024

    private val PRE_COMPRESSED_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "avif",
        "mp4", "mov", "mkv", "avi", "webm", "mp3", "aac", "flac", "ogg", "wav",
        "zip", "7z", "rar", "gz", "tar", "bz2", "xz", "pdf", "iso"
    )

    fun hashKeyfile(context: Context, uri: Uri): Pair<ByteArray, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        } ?: throw IllegalArgumentException("Cannot read keyfile")

        val hashBytes = digest.digest()
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        return Pair(hashBytes, hexString)
    }

    fun deriveKeyV1(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, 100000, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = skf.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun deriveKeyV2(password: CharArray, salt: ByteArray, keyfileHash: ByteArray?): SecretKey {
        val spec = PBEKeySpec(password, salt, 600000, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val derivedKeyBytes = skf.generateSecret(spec).encoded

        val finalKeyBytes = if (keyfileHash != null && keyfileHash.size == 32) {
            val mixed = ByteArray(32)
            for (i in 0 until 32) {
                mixed[i] = (derivedKeyBytes[i].toInt() xor keyfileHash[i].toInt()).toByte()
            }
            mixed
        } else {
            derivedKeyBytes
        }
        return SecretKeySpec(finalKeyBytes, "AES")
    }

    fun isV2Header(firstBytes: ByteArray): Boolean {
        if (firstBytes.size < 4) return false
        return firstBytes[0] == V2_MAGIC[0] &&
                firstBytes[1] == V2_MAGIC[1] &&
                firstBytes[2] == V2_MAGIC[2] &&
                firstBytes[3] == V2_MAGIC[3]
    }

    fun parseVaultHeader(inputStream: InputStream): VaultHeader {
        val magic = ByteArray(4)
        val readMagic = inputStream.read(magic)
        if (readMagic != 4) {
            throw IllegalArgumentException("Corrupted or incomplete vault file")
        }

        if (isV2Header(magic)) {
            val versionByte = inputStream.read()
            val flagsByte = inputStream.read()
            if (versionByte == -1 || flagsByte == -1) {
                throw IllegalArgumentException("Incomplete v2 vault header")
            }
            val requiresKeyfile = (flagsByte and V2_FLAG_KEYFILE.toInt()) != 0

            val salt = ByteArray(32)
            if (inputStream.read(salt) != 32) {
                throw IllegalArgumentException("Incomplete salt in v2 header")
            }

            val iv = ByteArray(12)
            if (inputStream.read(iv) != 12) {
                throw IllegalArgumentException("Incomplete IV in v2 header")
            }

            return VaultHeader(
                version = VaultVersion.V2,
                flags = flagsByte,
                requiresKeyfile = requiresKeyfile,
                salt = salt,
                iv = iv,
                ciphertextOffset = V2_HEADER_SIZE
            )
        } else {
            // v1 header: first 4 bytes were the start of 16-byte salt
            val remainingSalt = ByteArray(12)
            if (inputStream.read(remainingSalt) != 12) {
                throw IllegalArgumentException("Incomplete v1 salt")
            }
            val fullSalt = ByteArray(16)
            System.arraycopy(magic, 0, fullSalt, 0, 4)
            System.arraycopy(remainingSalt, 0, fullSalt, 4, 12)

            val iv = ByteArray(12)
            if (inputStream.read(iv) != 12) {
                throw IllegalArgumentException("Incomplete v1 IV")
            }

            return VaultHeader(
                version = VaultVersion.V1,
                flags = 0,
                requiresKeyfile = false,
                salt = fullSalt,
                iv = iv,
                ciphertextOffset = V1_HEADER_SIZE
            )
        }
    }

    suspend fun createZipArchive(
        context: Context,
        items: List<SelectedItem>,
        outputZipFile: File,
        onProgress: (Float, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val totalBytes = items.sumOf { it.sizeBytes }
        var processedBytes = 0L

        // Check pre-compressed content percentage
        var mediaBytes = 0L
        for (item in items) {
            val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (PRE_COMPRESSED_EXTENSIONS.contains(ext)) {
                mediaBytes += item.sizeBytes
            }
        }
        val isMostlyPrecompressed = totalBytes > 0 && (mediaBytes.toDouble() / totalBytes) >= 0.60
        val compressionLevel = if (isMostlyPrecompressed) Deflater.NO_COMPRESSION else Deflater.BEST_SPEED

        ZipOutputStream(FileOutputStream(outputZipFile).buffered()).use { zipOut ->
            zipOut.setLevel(compressionLevel)
            val buffer = ByteArray(BUFFER_SIZE)

            for (item in items) {
                if (item.isDirectory) {
                    val dirEntry = ZipEntry(if (item.relativePath.endsWith("/")) item.relativePath else "${item.relativePath}/")
                    zipOut.putNextEntry(dirEntry)
                    zipOut.closeEntry()
                    continue
                }

                val zipEntry = ZipEntry(item.relativePath)
                zipOut.putNextEntry(zipEntry)

                context.contentResolver.openInputStream(item.uri)?.use { inStream ->
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        zipOut.write(buffer, 0, read)
                        processedBytes += read
                        if (totalBytes > 0) {
                            val pct = (processedBytes.toFloat() / totalBytes) * 100f
                            onProgress(pct.coerceIn(0f, 100f), totalBytes)
                        }
                    }
                } ?: throw IllegalArgumentException("Cannot read source file: ${item.name}")

                zipOut.closeEntry()
            }
        }
        onProgress(100f, totalBytes)
    }

    suspend fun encryptZipToVault(
        zipFile: File,
        outputVaultFile: File,
        password: CharArray,
        useV2: Boolean,
        keyfileHash: ByteArray?,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val random = SecureRandom()
        val saltSize = if (useV2) 32 else 16
        val salt = ByteArray(saltSize)
        random.nextBytes(salt)

        val iv = ByteArray(GCM_IV_SIZE)
        random.nextBytes(iv)

        val secretKey = if (useV2) {
            deriveKeyV2(password, salt, keyfileHash)
        } else {
            deriveKeyV1(password, salt)
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val totalZipBytes = zipFile.length()
        var encryptedBytes = 0L

        FileOutputStream(outputVaultFile).buffered().use { outStream ->
            if (useV2) {
                outStream.write(V2_MAGIC)
                outStream.write(V2_VERSION_BYTE.toInt())
                val flags = if (keyfileHash != null) V2_FLAG_KEYFILE.toInt() else 0x00
                outStream.write(flags)
                outStream.write(salt)
                outStream.write(iv)
            } else {
                outStream.write(salt)
                outStream.write(iv)
            }

            CipherOutputStream(outStream, cipher).use { cos ->
                FileInputStream(zipFile).buffered().use { fis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        cos.write(buffer, 0, read)
                        encryptedBytes += read
                        if (totalZipBytes > 0) {
                            val pct = (encryptedBytes.toFloat() / totalZipBytes) * 100f
                            onProgress(pct.coerceIn(0f, 100f))
                        }
                    }
                }
            }
        }
        onProgress(100f)
    }

    suspend fun decryptVaultToZip(
        context: Context,
        vaultUri: Uri,
        outputZipFile: File,
        password: CharArray,
        keyfileHash: ByteArray?,
        onHeaderParsed: (VaultHeader) -> Unit,
        onProgress: (Float) -> Unit
    ): VaultHeader = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(vaultUri)
            ?: throw IllegalArgumentException("Cannot open vault file")

        inputStream.buffered().use { inStream ->
            val header = parseVaultHeader(inStream)
            onHeaderParsed(header)

            if (header.requiresKeyfile && keyfileHash == null) {
                throw IllegalStateException("This v2 vault requires a keyfile to decrypt.")
            }

            val secretKey = if (header.version == VaultVersion.V2) {
                deriveKeyV2(password, header.salt, keyfileHash)
            } else {
                deriveKeyV1(password, header.salt)
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, header.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            FileOutputStream(outputZipFile).buffered().use { fos ->
                CipherInputStream(inStream, cipher).use { cis ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    var totalDecrypted = 0L
                    while (cis.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        totalDecrypted += read
                        // Streaming progress notification
                        onProgress(50f)
                    }
                }
            }
            onProgress(100f)
            return@withContext header
        }
    }

    suspend fun extractZipArchive(
        zipFile: File,
        outputDir: File,
        context: Context
    ): List<DecryptedFileEntry> = withContext(Dispatchers.IO) {
        if (!outputDir.exists()) outputDir.mkdirs()
        val entries = mutableListOf<DecryptedFileEntry>()

        ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val currentEntry = entry ?: continue
                val targetFile = File(outputDir, currentEntry.name)

                // Prevent zip slip vulnerability
                if (!targetFile.canonicalPath.startsWith(outputDir.canonicalPath)) {
                    throw SecurityException("Zip traversal detected: ${currentEntry.name}")
                }

                if (currentEntry.isDirectory) {
                    targetFile.mkdirs()
                    entries.add(
                        DecryptedFileEntry(
                            path = currentEntry.name,
                            name = targetFile.name,
                            sizeBytes = 0,
                            isDirectory = true,
                            mimeType = "inode/directory"
                        )
                    )
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).buffered().use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                        }
                    }

                    val ext = targetFile.extension.lowercase(Locale.ROOT)
                    val mimeType = when (ext) {
                        "png", "jpg", "jpeg", "webp", "gif", "svg" -> "image/$ext"
                        "txt", "md", "json", "xml", "html", "js", "css", "kt", "java", "py", "c", "cpp" -> "text/plain"
                        "pdf" -> "application/pdf"
                        "mp4", "mkv", "mov", "webm" -> "video/$ext"
                        "mp3", "wav", "ogg", "flac" -> "audio/$ext"
                        else -> "application/octet-stream"
                    }

                    var textPreview: String? = null
                    if (mimeType.startsWith("text/") || ext in setOf("txt", "md", "json", "xml", "log", "csv")) {
                        try {
                            if (targetFile.length() < 256 * 1024) {
                                textPreview = targetFile.readText(Charsets.UTF_8).take(2000)
                            }
                        } catch (_: Exception) {}
                    }

                    entries.add(
                        DecryptedFileEntry(
                            path = currentEntry.name,
                            name = targetFile.name,
                            sizeBytes = targetFile.length(),
                            isDirectory = false,
                            mimeType = mimeType,
                            localFileUri = Uri.fromFile(targetFile),
                            textContentPreview = textPreview
                        )
                    )
                }
                zis.closeEntry()
            }
        }
        return@withContext entries
    }

    fun buildPasswordRecoverySheetText(record: PasswordRecoveryRecord): String {
        val keyfileSection = if (record.keyfileRequired) {
            "Required keyfile: ${record.keyfileName.ifEmpty { "Selected keyfile" }}\nKeyfile SHA-256 fingerprint: ${record.keyfileFingerprint.ifEmpty { "Unavailable" }}\n"
        } else {
            "Required keyfile: None\n"
        }

        return """
ZevSafe Password Recovery Sheet
================================

WARNING
If you lose this password or required keyfile, decryption will not be possible. Encrypted files or folders may be permanently inaccessible.
There is no password recovery, reset, or backdoor.

Vault Details
-------------
Encrypted folder/project: ${record.folderName}
Vault file: ${record.vaultFilename}
Vault format: ${record.version}
Created: ${record.timestampFormatted.ifEmpty { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) }}

Password / Encryption Key
-------------------------
${record.password}

Second Factor
-------------
${keyfileSection.trimEnd()}

Storage Instructions
--------------------
Print this sheet or store it offline in a secure place.
Keep it separate from the encrypted vault file.
Anyone with this password and required keyfile can decrypt the vault.
        """.trimIndent()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }
}
