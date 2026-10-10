package com.zevbuild.zevsafe.crypto

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    val V3_MAGIC = byteArrayOf(0x5A, 0x56, 0x33, 0x00) // "ZV3\0"
    val V2_MAGIC = byteArrayOf(0x5A, 0x56, 0x32, 0x00) // "ZV2\0"
    const val V3_VERSION_BYTE: Byte = 0x03
    const val V2_VERSION_BYTE: Byte = 0x02
    const val V3_FLAG_KEYFILE: Byte = 0x01
    const val V2_FLAG_KEYFILE: Byte = 0x01

    const val V3_HEADER_SIZE = 57 // 4 magic + 1 ver + 1 flags + 4 chunkSize + 32 salt + 7 ivPrefix + 8 manifestOffset
    const val V2_HEADER_SIZE = 50 // 4 magic + 1 ver + 1 flags + 32 salt + 12 iv
    const val V1_HEADER_SIZE = 28 // 16 salt + 12 iv

    const val DEFAULT_CHUNK_SIZE = 4 * 1024 * 1024 // 4 MB
    const val CHUNK_HEADER_SIZE = 4
    const val TAG_LENGTH = 16
    const val GCM_TAG_LENGTH_BITS = 128
    const val AAD_LENGTH = 42
    const val BASE_IV_PREFIX_LENGTH = 7
    const val GCM_IV_SIZE = 12
    const val BUFFER_SIZE = 64 * 1024
    const val SALT_LENGTH = 32

    const val MANIFEST_SALT_LENGTH = 32
    const val MANIFEST_IV_LENGTH = 12
    const val MANIFEST_LEN_SIZE = 4
    const val MANIFEST_HEADER_SIZE = MANIFEST_SALT_LENGTH + MANIFEST_IV_LENGTH + MANIFEST_LEN_SIZE // 48 bytes

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
        return deriveKeyV3(password, salt, keyfileHash)
    }

    fun deriveKeyV3(password: CharArray, salt: ByteArray, keyfileHash: ByteArray?): SecretKey {
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

    fun isV3Header(firstBytes: ByteArray): Boolean {
        if (firstBytes.size < 4) return false
        return firstBytes[0] == V3_MAGIC[0] &&
                firstBytes[1] == V3_MAGIC[1] &&
                firstBytes[2] == V3_MAGIC[2] &&
                firstBytes[3] == V3_MAGIC[3]
    }

    fun isV2Header(firstBytes: ByteArray): Boolean {
        if (firstBytes.size < 4) return false
        return firstBytes[0] == V2_MAGIC[0] &&
                firstBytes[1] == V2_MAGIC[1] &&
                firstBytes[2] == V2_MAGIC[2] &&
                firstBytes[3] == V2_MAGIC[3]
    }

    fun computeChunkIV(baseIVPrefix: ByteArray, chunkIndex: Int, isLast: Boolean): ByteArray {
        val buffer = ByteBuffer.allocate(GCM_IV_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(baseIVPrefix, 0, BASE_IV_PREFIX_LENGTH)
        buffer.putInt(chunkIndex)
        buffer.put((if (isLast) 0x01 else 0x00).toByte())
        return buffer.array()
    }

    fun computeChunkAAD(magic: ByteArray, version: Byte, salt: ByteArray, chunkIndex: Int, isLast: Boolean): ByteArray {
        val buffer = ByteBuffer.allocate(AAD_LENGTH).order(ByteOrder.BIG_ENDIAN)
        buffer.put(magic, 0, 4)
        buffer.put(version)
        buffer.put(salt, 0, 32)
        buffer.putInt(chunkIndex)
        buffer.put((if (isLast) 0x01 else 0x00).toByte())
        return buffer.array()
    }

    fun createV3ContainerHeader(
        salt: ByteArray,
        baseIVPrefix: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        flags: Byte = 0,
        manifestOffset: Long = 0L
    ): ByteArray {
        val buffer = ByteBuffer.allocate(V3_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(V3_MAGIC)
        buffer.put(V3_VERSION_BYTE)
        buffer.put(flags)
        buffer.putInt(chunkSize)
        buffer.put(salt, 0, 32)
        buffer.put(baseIVPrefix, 0, BASE_IV_PREFIX_LENGTH)
        buffer.putLong(manifestOffset)
        return buffer.array()
    }

    fun parseVaultHeader(inputStream: InputStream): VaultHeader {
        val magic = ByteArray(4)
        val readMagic = inputStream.read(magic)
        if (readMagic != 4) {
            throw IllegalArgumentException("File is too small to be a valid vault")
        }

        if (isV3Header(magic)) {
            val ver = inputStream.read()
            val flags = inputStream.read()
            if (ver == -1 || flags == -1) throw IllegalArgumentException("Incomplete v3 header")

            val chunkBytes = ByteArray(4)
            if (inputStream.read(chunkBytes) != 4) throw IllegalArgumentException("Incomplete chunk size in v3 header")
            val chunkSize = ByteBuffer.wrap(chunkBytes).order(ByteOrder.BIG_ENDIAN).int

            val salt = ByteArray(32)
            if (inputStream.read(salt) != 32) throw IllegalArgumentException("Incomplete salt in v3 header")

            val baseIV = ByteArray(7)
            if (inputStream.read(baseIV) != 7) throw IllegalArgumentException("Incomplete base IV prefix in v3 header")

            val offsetBytes = ByteArray(8)
            if (inputStream.read(offsetBytes) != 8) throw IllegalArgumentException("Incomplete manifest offset in v3 header")
            val manifestOffset = ByteBuffer.wrap(offsetBytes).order(ByteOrder.BIG_ENDIAN).long

            val requiresKeyfile = (flags and V3_FLAG_KEYFILE.toInt()) != 0

            return VaultHeader(
                version = VaultVersion.V3,
                flags = flags,
                requiresKeyfile = requiresKeyfile,
                salt = salt,
                iv = baseIV,
                ciphertextOffset = V3_HEADER_SIZE,
                chunkSize = chunkSize,
                baseIVPrefix = baseIV,
                manifestOffset = manifestOffset
            )
        } else if (isV2Header(magic)) {
            val versionByte = inputStream.read()
            val flagsByte = inputStream.read()
            if (versionByte == -1 || flagsByte == -1) throw IllegalArgumentException("Incomplete v2 vault header")
            val requiresKeyfile = (flagsByte and V2_FLAG_KEYFILE.toInt()) != 0

            val salt = ByteArray(32)
            if (inputStream.read(salt) != 32) throw IllegalArgumentException("Incomplete salt in v2 header")

            val iv = ByteArray(12)
            if (inputStream.read(iv) != 12) throw IllegalArgumentException("Incomplete IV in v2 header")

            return VaultHeader(
                version = VaultVersion.V2,
                flags = flagsByte,
                requiresKeyfile = requiresKeyfile,
                salt = salt,
                iv = iv,
                ciphertextOffset = V2_HEADER_SIZE
            )
        } else {
            // v1 legacy header (28 bytes: 16B salt + 12B IV)
            val remainingSalt = ByteArray(12)
            if (inputStream.read(remainingSalt) != 12) throw IllegalArgumentException("Incomplete v1 salt")
            val fullSalt = ByteArray(16)
            System.arraycopy(magic, 0, fullSalt, 0, 4)
            System.arraycopy(remainingSalt, 0, fullSalt, 4, 12)

            val iv = ByteArray(12)
            if (inputStream.read(iv) != 12) throw IllegalArgumentException("Incomplete v1 IV")

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

    fun encryptChunk(
        key: SecretKey,
        plaintext: ByteArray,
        baseIVPrefix: ByteArray,
        chunkIndex: Int,
        isLast: Boolean,
        salt: ByteArray
    ): ByteArray {
        val iv = computeChunkIV(baseIVPrefix, chunkIndex, isLast)
        val aad = computeChunkAAD(V3_MAGIC, V3_VERSION_BYTE, salt, chunkIndex, isLast)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        cipher.updateAAD(aad)

        val ciphertextWithTag = cipher.doFinal(plaintext)
        val output = ByteBuffer.allocate(CHUNK_HEADER_SIZE + ciphertextWithTag.size).order(ByteOrder.BIG_ENDIAN)
        output.putInt(plaintext.size)
        output.put(ciphertextWithTag)
        return output.array()
    }

    fun decryptChunk(
        key: SecretKey,
        chunkBytes: ByteArray,
        baseIVPrefix: ByteArray,
        chunkIndex: Int,
        isLast: Boolean,
        salt: ByteArray
    ): ByteArray {
        if (chunkBytes.size < CHUNK_HEADER_SIZE + TAG_LENGTH) {
            throw IllegalArgumentException("Invalid chunk buffer length")
        }
        val buffer = ByteBuffer.wrap(chunkBytes).order(ByteOrder.BIG_ENDIAN)
        val declaredLen = buffer.int
        val ciphertextWithTag = ByteArray(chunkBytes.size - CHUNK_HEADER_SIZE)
        buffer.get(ciphertextWithTag)

        val iv = computeChunkIV(baseIVPrefix, chunkIndex, isLast)
        val aad = computeChunkAAD(V3_MAGIC, V3_VERSION_BYTE, salt, chunkIndex, isLast)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.updateAAD(aad)

        val plaintext = cipher.doFinal(ciphertextWithTag)
        if (plaintext.size != declaredLen) {
            throw IllegalStateException("Decrypted length does not match declared length")
        }
        return plaintext
    }

    fun encryptManifestEnvelope(catalogJson: String, secretKey: SecretKey, salt: ByteArray): ByteArray {
        val random = SecureRandom()
        val iv = ByteArray(MANIFEST_IV_LENGTH)
        random.nextBytes(iv)

        val jsonBytes = catalogJson.toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertextWithTag = cipher.doFinal(jsonBytes)
        val envelope = ByteBuffer.allocate(MANIFEST_HEADER_SIZE + ciphertextWithTag.size).order(ByteOrder.BIG_ENDIAN)
        envelope.put(salt, 0, MANIFEST_SALT_LENGTH)
        envelope.put(iv, 0, MANIFEST_IV_LENGTH)
        envelope.putInt(jsonBytes.size)
        envelope.put(ciphertextWithTag)
        return envelope.array()
    }

    fun decryptManifestEnvelope(envelopeBytes: ByteArray, secretKey: SecretKey): String {
        if (envelopeBytes.size < MANIFEST_HEADER_SIZE + TAG_LENGTH) {
            throw IllegalArgumentException("Invalid manifest envelope buffer")
        }
        val buffer = ByteBuffer.wrap(envelopeBytes).order(ByteOrder.BIG_ENDIAN)
        val salt = ByteArray(MANIFEST_SALT_LENGTH)
        buffer.get(salt)
        val iv = ByteArray(MANIFEST_IV_LENGTH)
        buffer.get(iv)
        val declaredLen = buffer.int

        val ciphertextWithTag = ByteArray(envelopeBytes.size - MANIFEST_HEADER_SIZE)
        buffer.get(ciphertextWithTag)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decrypted = cipher.doFinal(ciphertextWithTag)
        if (decrypted.size != declaredLen) {
            throw IllegalStateException("Manifest decrypted size mismatch")
        }
        return String(decrypted, Charsets.UTF_8)
    }

    suspend fun readV3VaultManifest(
        inStream: InputStream,
        password: CharArray,
        keyfileHash: ByteArray? = null
    ): VaultCatalog = withContext(Dispatchers.IO) {
        val header = parseVaultHeader(inStream)
        if (header.version != VaultVersion.V3) {
            throw IllegalArgumentException("Vault is not a v3 streaming container")
        }
        if (header.requiresKeyfile && keyfileHash == null) {
            throw IllegalArgumentException("This v3 vault requires a keyfile")
        }

        val secretKey = deriveKeyV3(password, header.salt, keyfileHash)
        val manifestOffset = header.manifestOffset
        if (manifestOffset <= V3_HEADER_SIZE) {
            throw IllegalArgumentException("Invalid manifest offset in v3 vault")
        }

        // Skip to manifest offset
        var skipped = V3_HEADER_SIZE.toLong()
        while (skipped < manifestOffset) {
            val skipStep = inStream.skip(manifestOffset - skipped)
            if (skipStep <= 0) break
            skipped += skipStep
        }

        // Read the manifest envelope header (48 bytes)
        val envelopeHeaderBytes = ByteArray(MANIFEST_HEADER_SIZE)
        val readHdr = inStream.read(envelopeHeaderBytes)
        if (readHdr != MANIFEST_HEADER_SIZE) {
            throw IllegalArgumentException("Incomplete manifest envelope header")
        }

        val buf = ByteBuffer.wrap(envelopeHeaderBytes).order(ByteOrder.BIG_ENDIAN)
        buf.position(MANIFEST_SALT_LENGTH + MANIFEST_IV_LENGTH)
        val declaredLen = buf.int
        val totalEnvelopeSize = MANIFEST_HEADER_SIZE + declaredLen + TAG_LENGTH

        val fullEnvelope = ByteArray(totalEnvelopeSize)
        System.arraycopy(envelopeHeaderBytes, 0, fullEnvelope, 0, MANIFEST_HEADER_SIZE)

        var offset = MANIFEST_HEADER_SIZE
        val payloadBuffer = ByteArray(BUFFER_SIZE)
        while (offset < totalEnvelopeSize) {
            val toRead = Math.min(payloadBuffer.size, totalEnvelopeSize - offset)
            val read = inStream.read(payloadBuffer, 0, toRead)
            if (read == -1) break
            System.arraycopy(payloadBuffer, 0, fullEnvelope, offset, read)
            offset += read
        }

        val jsonString = decryptManifestEnvelope(fullEnvelope, secretKey)
        val root = JSONObject(jsonString)
        val filesJson = root.getJSONArray("files")
        val entries = mutableListOf<DecryptedFileEntry>()

        for (i in 0 until filesJson.length()) {
            val item = filesJson.getJSONObject(i)
            val path = item.optString("path", item.optString("name", "unnamed"))
            val size = item.optLong("size", item.optLong("uncompressedSize", 0L))
            val compSize = item.optLong("compressedSize", size)
            val lho = item.optLong("localHeaderOffset", item.optLong("offset", 0L))
            val isComp = item.optBoolean("compressed", false)
            val crc = item.optLong("crc32", 0L)
            val chunkStart = item.optInt("chunkStart", (lho / DEFAULT_CHUNK_SIZE).toInt())
            val chunkEnd = item.optInt("chunkEnd", ((lho + 30 + path.length + compSize) / DEFAULT_CHUNK_SIZE).toInt())

            val name = path.substringAfterLast('/')
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mimeType = resolveMimeType(ext)

            entries.add(
                DecryptedFileEntry(
                    path = path,
                    name = name,
                    sizeBytes = size,
                    isDirectory = path.endsWith("/"),
                    mimeType = mimeType,
                    localHeaderOffset = lho,
                    compressedSize = compSize,
                    isCompressed = isComp,
                    crc32 = crc,
                    chunkStart = chunkStart,
                    chunkEnd = chunkEnd
                )
            )
        }

        return@withContext VaultCatalog(
            version = 3,
            totalSize = root.optLong("totalSize", 0L),
            fileCount = entries.size,
            files = entries
        )
    }

    suspend fun readV3VaultManifest(
        context: Context,
        vaultUri: Uri,
        password: CharArray,
        keyfileHash: ByteArray? = null
    ): VaultCatalog = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(vaultUri)?.use { inStream ->
            readV3VaultManifest(inStream, password, keyfileHash)
        } ?: throw IllegalArgumentException("Cannot open vault file")
    }

    suspend fun encryptItemsToV3Vault(
        context: Context?,
        items: List<SelectedItem>,
        outputVaultFile: File,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        inputStreamProvider: ((SelectedItem) -> InputStream?)? = null,
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).apply { random.nextBytes(this) }
        val baseIVPrefix = ByteArray(BASE_IV_PREFIX_LENGTH).apply { random.nextBytes(this) }
        val flags: Byte = if (keyfileHash != null) V3_FLAG_KEYFILE else 0x00

        val secretKey = deriveKeyV3(password, salt, keyfileHash)
        val initialHeader = createV3ContainerHeader(salt, baseIVPrefix, DEFAULT_CHUNK_SIZE, flags, 0L)

        val totalSourceBytes = items.sumOf { it.sizeBytes }
        var processedSourceBytes = 0L
        var totalMeasuredBytes = 0L

        val catalogEntries = JSONArray()
        var currentZipOffset = 0L

        RandomAccessFile(outputVaultFile, "rw").use { raf ->
            raf.setLength(0L)
            raf.write(initialHeader)

            val chunkBuffer = ByteArrayOutputStream(DEFAULT_CHUNK_SIZE)
            var chunkIndex = 0

            fun flushChunk(isLast: Boolean) {
                val plaintext = chunkBuffer.toByteArray()
                chunkBuffer.reset()
                val framed = encryptChunk(secretKey, plaintext, baseIVPrefix, chunkIndex, isLast, salt)
                raf.write(framed)
                chunkIndex++
            }

            // Custom OutputStream that feeds into chunkBuffer and flushes when >= DEFAULT_CHUNK_SIZE
            val customStream = object : java.io.OutputStream() {
                override fun write(b: Int) {
                    chunkBuffer.write(b)
                    currentZipOffset++
                    if (chunkBuffer.size() >= DEFAULT_CHUNK_SIZE) {
                        flushChunk(isLast = false)
                    }
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    var remaining = len
                    var currentOff = off
                    while (remaining > 0) {
                        val space = DEFAULT_CHUNK_SIZE - chunkBuffer.size()
                        val toWrite = Math.min(remaining, space)
                        chunkBuffer.write(b, currentOff, toWrite)
                        currentZipOffset += toWrite
                        currentOff += toWrite
                        remaining -= toWrite
                        if (chunkBuffer.size() >= DEFAULT_CHUNK_SIZE) {
                            flushChunk(isLast = false)
                        }
                    }
                }
            }

            ZipOutputStream(customStream).use { zipOut ->
                zipOut.setLevel(Deflater.BEST_SPEED)
                val ioBuffer = ByteArray(BUFFER_SIZE)

                for (item in items) {
                    if (item.isDirectory) {
                        val dirPath = if (item.relativePath.endsWith("/")) item.relativePath else "${item.relativePath}/"
                        val dirEntry = ZipEntry(dirPath)
                        zipOut.putNextEntry(dirEntry)
                        zipOut.closeEntry()
                        continue
                    }

                    val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val isPrecompressed = PRE_COMPRESSED_EXTENSIONS.contains(ext)

                    val entryOffset = currentZipOffset
                    val zipEntry = ZipEntry(item.relativePath)
                    val crc = CRC32()
                    var measuredUncompressedSize = 0L
                    var measuredCompressedSize = 0L

                    val openStream: () -> InputStream = {
                        inputStreamProvider?.invoke(item)
                            ?: context?.contentResolver?.openInputStream(item.uri)
                            ?: throw java.io.IOException("Cannot open input stream for ${item.name}")
                    }

                    if (isPrecompressed) {
                        zipEntry.method = ZipEntry.STORED

                        // Measure exact length and CRC32 for STORED entry
                        openStream().use { inStream ->
                            var read: Int
                            while (inStream.read(ioBuffer).also { read = it } != -1) {
                                crc.update(ioBuffer, 0, read)
                                measuredUncompressedSize += read
                            }
                        }

                        zipEntry.size = measuredUncompressedSize
                        zipEntry.compressedSize = measuredUncompressedSize
                        zipEntry.crc = crc.value
                        measuredCompressedSize = measuredUncompressedSize
                    }

                    zipOut.putNextEntry(zipEntry)

                    var writtenUncompressedBytes = 0L
                    openStream().use { inStream ->
                        var read: Int
                        while (inStream.read(ioBuffer).also { read = it } != -1) {
                            if (!isPrecompressed) {
                                crc.update(ioBuffer, 0, read)
                            }
                            zipOut.write(ioBuffer, 0, read)
                            writtenUncompressedBytes += read
                            processedSourceBytes += read
                            if (totalSourceBytes > 0) {
                                val pct = (processedSourceBytes.toFloat() / totalSourceBytes) * 100f
                                onProgress(pct.coerceIn(0f, 98f), processedSourceBytes, totalSourceBytes)
                            } else {
                                val mb = processedSourceBytes.toFloat() / (1024f * 1024f)
                                val simulatedPct = (mb * 10f).coerceIn(0f, 95f)
                                onProgress(simulatedPct, processedSourceBytes, processedSourceBytes)
                            }
                        }
                    }
                    zipOut.closeEntry()

                    if (!isPrecompressed) {
                        measuredUncompressedSize = writtenUncompressedBytes
                        if (writtenUncompressedBytes == 0L) {
                            measuredCompressedSize = 0L
                        } else {
                            val zipReportedCompSize = zipEntry.compressedSize
                            measuredCompressedSize = if (zipReportedCompSize > 0L) {
                                zipReportedCompSize
                            } else {
                                val headerEstimate = 30L + item.relativePath.toByteArray(Charsets.UTF_8).size + 16L
                                val delta = currentZipOffset - entryOffset
                                val estimatedPayload = delta - headerEstimate
                                if (estimatedPayload > 0L) estimatedPayload else writtenUncompressedBytes
                            }
                        }
                    }

                    val finalSize = measuredUncompressedSize
                    val finalCompSize = measuredCompressedSize
                    totalMeasuredBytes += finalSize

                    val chunkStart = (entryOffset / DEFAULT_CHUNK_SIZE).toInt()
                    val chunkEnd = ((entryOffset + 30 + item.relativePath.length + finalCompSize) / DEFAULT_CHUNK_SIZE).toInt()

                    val entryJson = JSONObject().apply {
                        put("path", item.relativePath)
                        put("name", item.name)
                        put("size", finalSize)
                        put("uncompressedSize", finalSize)
                        put("compressedSize", finalCompSize)
                        put("localHeaderOffset", entryOffset)
                        put("compressed", !isPrecompressed)
                        put("crc32", crc.value)
                        put("chunkStart", chunkStart)
                        put("chunkEnd", chunkEnd)
                    }
                    catalogEntries.put(entryJson)
                }
            }

            // Flush remaining data as final chunk (isLast = true)
            flushChunk(isLast = true)

            // Current file pointer is manifestOffset
            val manifestOffset = raf.filePointer

            // Write Manifest Envelope
            val catalogRoot = JSONObject().apply {
                put("version", 3)
                put("totalSize", if (totalMeasuredBytes > 0L) totalMeasuredBytes else totalSourceBytes)
                put("fileCount", catalogEntries.length())
                put("files", catalogEntries)
            }
            val manifestEnvelope = encryptManifestEnvelope(catalogRoot.toString(), secretKey, salt)
            raf.write(manifestEnvelope)

            // Seek back to offset 49 and update manifestOffset (8 bytes uint64 BE)
            raf.seek(49)
            val offsetBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            offsetBuf.putLong(manifestOffset)
            raf.write(offsetBuf.array())
        }

        val finalTotal = if (totalMeasuredBytes > 0L) totalMeasuredBytes else totalSourceBytes
        onProgress(100f, finalTotal, finalTotal)
    }

    suspend fun encryptItemsToV3Vault(
        streamOpener: (SelectedItem) -> InputStream,
        items: List<SelectedItem>,
        outputVaultFile: File,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }
    ) = encryptItemsToV3Vault(
        context = null,
        items = items,
        outputVaultFile = outputVaultFile,
        password = password,
        keyfileHash = keyfileHash,
        inputStreamProvider = streamOpener,
        onProgress = onProgress
    )

    suspend fun decryptV3VaultStreamToDirectory(
        inStream: InputStream,
        outputDir: File,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<DecryptedFileEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<DecryptedFileEntry>()

        val header = parseVaultHeader(inStream)
        if (header.version != VaultVersion.V3) {
            throw IllegalArgumentException("Expected v3 vault format")
        }
        if (header.requiresKeyfile && keyfileHash == null) {
            throw IllegalArgumentException("Keyfile required for this vault")
        }

        val secretKey = deriveKeyV3(password, header.salt, keyfileHash)
        val baseIV = header.baseIVPrefix ?: throw IllegalArgumentException("Missing base IV")
        val manifestOffset = header.manifestOffset

        val decryptedStream = V3DecryptedInputStream(
            inStream = inStream,
            secretKey = secretKey,
            baseIVPrefix = baseIV,
            salt = header.salt,
            manifestOffset = manifestOffset
        ) { pct ->
            onProgress(pct, "Decrypting and extracting files (${pct.toInt()}%)...")
        }

        ZipInputStream(decryptedStream.buffered()).use { zis ->
            var zipEntry: ZipEntry?
            while (zis.nextEntry.also { zipEntry = it } != null) {
                val entry = zipEntry!!
                val targetFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).buffered().use { fos ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var r: Int
                        while (zis.read(buf).also { r = it } != -1) {
                            fos.write(buf, 0, r)
                        }
                    }

                    val ext = targetFile.extension.lowercase(Locale.ROOT)
                    val mimeType = resolveMimeType(ext)
                    var textPreview: String? = null
                    if (mimeType.startsWith("text/") && targetFile.length() < 256 * 1024) {
                        textPreview = targetFile.readText(Charsets.UTF_8).take(2000)
                    }

                    val fileUri = try {
                        Uri.fromFile(targetFile)
                    } catch (_: Throwable) {
                        null
                    }

                    entries.add(
                        DecryptedFileEntry(
                            path = entry.name,
                            name = targetFile.name,
                            sizeBytes = targetFile.length(),
                            isDirectory = false,
                            mimeType = mimeType,
                            localFileUri = fileUri,
                            textContentPreview = textPreview
                        )
                    )
                }
                zis.closeEntry()
            }
        }

        onProgress(100f, "Extraction complete")
        return@withContext entries
    }

    suspend fun decryptV3VaultToDirectory(
        inStream: InputStream,
        outputDir: File,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<DecryptedFileEntry> = decryptV3VaultStreamToDirectory(inStream, outputDir, password, keyfileHash, onProgress)

    suspend fun decryptV3VaultToDirectory(
        context: Context,
        vaultUri: Uri,
        outputDir: File,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<DecryptedFileEntry> = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(vaultUri)?.use { inStream ->
            decryptV3VaultStreamToDirectory(inStream, outputDir, password, keyfileHash, onProgress)
        } ?: throw IllegalArgumentException("Cannot open vault file")
    }

    suspend fun extractSingleFileFromV3Vault(
        context: Context,
        vaultUri: Uri,
        entry: DecryptedFileEntry,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        outputFile: File
    ): DecryptedFileEntry = withContext(Dispatchers.IO) {
        val pfd = try {
            context.contentResolver.openFileDescriptor(vaultUri, "r")
        } catch (_: Throwable) {
            null
        }

        if (pfd != null) {
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    extractSingleFileFromChannel(
                        channel = channel,
                        entry = entry,
                        password = password,
                        keyfileHash = keyfileHash,
                        outputFile = outputFile
                    )
                }
            }
        } else {
            extractSingleFileFromStreamProvider(
                inStreamProvider = {
                    context.contentResolver.openInputStream(vaultUri)
                        ?: throw java.io.IOException("Cannot open input stream for $vaultUri")
                },
                entry = entry,
                password = password,
                keyfileHash = keyfileHash,
                outputFile = outputFile
            )
        }
    }

    suspend fun extractSingleFileFromV3Vault(
        vaultFile: File,
        entry: DecryptedFileEntry,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        outputFile: File
    ): DecryptedFileEntry = withContext(Dispatchers.IO) {
        FileInputStream(vaultFile).channel.use { channel ->
            extractSingleFileFromChannel(
                channel = channel,
                entry = entry,
                password = password,
                keyfileHash = keyfileHash,
                outputFile = outputFile
            )
        }
    }

    suspend fun extractSingleFileFromV3Vault(
        inStreamProvider: () -> InputStream,
        entry: DecryptedFileEntry,
        password: CharArray,
        keyfileHash: ByteArray? = null,
        outputFile: File
    ): DecryptedFileEntry = withContext(Dispatchers.IO) {
        extractSingleFileFromStreamProvider(
            inStreamProvider = inStreamProvider,
            entry = entry,
            password = password,
            keyfileHash = keyfileHash,
            outputFile = outputFile
        )
    }

    private fun extractSingleFileFromChannel(
        channel: FileChannel,
        entry: DecryptedFileEntry,
        password: CharArray,
        keyfileHash: ByteArray?,
        outputFile: File
    ): DecryptedFileEntry {
        channel.position(0L)
        val headerBuf = ByteBuffer.allocate(V3_HEADER_SIZE)
        while (headerBuf.hasRemaining()) {
            val r = channel.read(headerBuf)
            if (r == -1) break
        }
        if (headerBuf.hasRemaining()) {
            throw java.io.IOException("Incomplete v3 header in vault")
        }
        val header = parseVaultHeader(ByteArrayInputStream(headerBuf.array()))

        val chunkSize = header.chunkSize
        val totalFramedChunkSize = chunkSize + CHUNK_HEADER_SIZE + TAG_LENGTH
        val lastChunkIndex = ((header.manifestOffset - V3_HEADER_SIZE - 1) / totalFramedChunkSize).toInt()

        return extractSingleFileTargetedInternal(
            header = header,
            entry = entry,
            password = password,
            keyfileHash = keyfileHash,
            outputFile = outputFile,
            readChunkBytesFn = { targetChunkIndex ->
                val chunkVaultStart = V3_HEADER_SIZE + targetChunkIndex.toLong() * totalFramedChunkSize
                val isLast = (targetChunkIndex == lastChunkIndex) || (chunkVaultStart + totalFramedChunkSize >= header.manifestOffset)
                val chunkFramedSize = if (isLast) (header.manifestOffset - chunkVaultStart).toInt() else totalFramedChunkSize
                if (chunkFramedSize < CHUNK_HEADER_SIZE + TAG_LENGTH) {
                    throw java.io.IOException("Corrupt chunk size $chunkFramedSize at chunk $targetChunkIndex")
                }
                channel.position(chunkVaultStart)
                val chunkBytes = ByteArray(chunkFramedSize)
                val buf = ByteBuffer.wrap(chunkBytes)
                while (buf.hasRemaining()) {
                    val r = channel.read(buf)
                    if (r == -1) break
                }
                if (buf.hasRemaining()) {
                    throw java.io.IOException("Incomplete chunk read at chunk $targetChunkIndex")
                }
                chunkBytes
            }
        )
    }

    private fun extractSingleFileFromStreamProvider(
        inStreamProvider: () -> InputStream,
        entry: DecryptedFileEntry,
        password: CharArray,
        keyfileHash: ByteArray?,
        outputFile: File
    ): DecryptedFileEntry {
        val header = inStreamProvider().use { inStream ->
            parseVaultHeader(inStream)
        }
        val chunkSize = header.chunkSize
        val totalFramedChunkSize = chunkSize + CHUNK_HEADER_SIZE + TAG_LENGTH
        val lastChunkIndex = ((header.manifestOffset - V3_HEADER_SIZE - 1) / totalFramedChunkSize).toInt()

        return extractSingleFileTargetedInternal(
            header = header,
            entry = entry,
            password = password,
            keyfileHash = keyfileHash,
            outputFile = outputFile,
            readChunkBytesFn = { targetChunkIndex ->
                val chunkVaultStart = V3_HEADER_SIZE + targetChunkIndex.toLong() * totalFramedChunkSize
                val isLast = (targetChunkIndex == lastChunkIndex) || (chunkVaultStart + totalFramedChunkSize >= header.manifestOffset)
                val chunkFramedSize = if (isLast) (header.manifestOffset - chunkVaultStart).toInt() else totalFramedChunkSize
                if (chunkFramedSize < CHUNK_HEADER_SIZE + TAG_LENGTH) {
                    throw java.io.IOException("Corrupt chunk size $chunkFramedSize at chunk $targetChunkIndex")
                }
                inStreamProvider().use { inStream ->
                    var currentPos = 0L
                    while (currentPos < chunkVaultStart) {
                        val toSkip = chunkVaultStart - currentPos
                        val skipped = inStream.skip(toSkip)
                        if (skipped <= 0) {
                            val discard = ByteArray(Math.min(toSkip, BUFFER_SIZE.toLong()).toInt())
                            val r = inStream.read(discard)
                            if (r == -1) break
                            currentPos += r
                        } else {
                            currentPos += skipped
                        }
                    }
                    val chunkBytes = ByteArray(chunkFramedSize)
                    var readTotal = 0
                    while (readTotal < chunkFramedSize) {
                        val r = inStream.read(chunkBytes, readTotal, chunkFramedSize - readTotal)
                        if (r == -1) break
                        readTotal += r
                    }
                    if (readTotal != chunkFramedSize) {
                        throw java.io.IOException("Incomplete chunk read at chunk $targetChunkIndex")
                    }
                    chunkBytes
                }
            }
        )
    }

    private fun extractSingleFileTargetedInternal(
        header: VaultHeader,
        entry: DecryptedFileEntry,
        password: CharArray,
        keyfileHash: ByteArray?,
        outputFile: File,
        readChunkBytesFn: (chunkIndex: Int) -> ByteArray
    ): DecryptedFileEntry {
        if (header.version != VaultVersion.V3) {
            throw IllegalArgumentException("Targeted single-file extraction requires a v3 streaming vault")
        }
        if (header.requiresKeyfile && keyfileHash == null) {
            throw IllegalArgumentException("This v3 vault requires a keyfile for decryption")
        }

        val secretKey = deriveKeyV3(password, header.salt, keyfileHash)
        val baseIV = header.baseIVPrefix ?: throw IllegalArgumentException("Missing base IV prefix in v3 vault header")
        val manifestOffset = header.manifestOffset
        val chunkSize = header.chunkSize
        val totalFramedChunkSize = chunkSize + CHUNK_HEADER_SIZE + TAG_LENGTH

        val lastChunkIndex = ((manifestOffset - V3_HEADER_SIZE - 1) / totalFramedChunkSize).toInt()
        val effCompSize = if (entry.compressedSize > 0L) entry.compressedSize else entry.sizeBytes
        val calculatedChunkStart = (entry.localHeaderOffset / chunkSize).toInt()
        val calculatedChunkEnd = ((entry.localHeaderOffset + 30 + entry.path.length + effCompSize) / chunkSize).toInt()

        val chunkStart = (if (entry.chunkStart >= 0 && entry.chunkStart.toLong() * chunkSize <= entry.localHeaderOffset) {
            if (entry.chunkStart == 0 && entry.localHeaderOffset >= chunkSize) calculatedChunkStart else entry.chunkStart
        } else calculatedChunkStart).coerceAtLeast(0)

        val chunkEnd = (if (entry.chunkEnd >= chunkStart) entry.chunkEnd else calculatedChunkEnd).coerceIn(chunkStart, lastChunkIndex)

        val decryptedChunks = mutableListOf<ByteArray>()
        for (i in chunkStart..chunkEnd) {
            val chunkVaultStart = V3_HEADER_SIZE + i.toLong() * totalFramedChunkSize
            val isLast = (i == lastChunkIndex) || (chunkVaultStart + totalFramedChunkSize >= manifestOffset)
            val chunkBytes = readChunkBytesFn(i)
            val plain = decryptChunk(secretKey, chunkBytes, baseIV, i, isLast, header.salt)
            decryptedChunks.add(plain)
        }

        var spanBytes: ByteArray = if (decryptedChunks.size == 1) {
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

        val spanBaseOffset = chunkStart.toLong() * chunkSize
        val relPos = (entry.localHeaderOffset - spanBaseOffset).toInt()
        if (relPos < 0 || relPos >= spanBytes.size) {
            throw IllegalStateException("Invalid local header offset $relPos within decrypted span of ${spanBytes.size} bytes")
        }

        var currentChunkEnd = chunkEnd
        while (relPos + 30 > spanBytes.size && currentChunkEnd < lastChunkIndex) {
            currentChunkEnd++
            val chunkVaultStart = V3_HEADER_SIZE + currentChunkEnd.toLong() * totalFramedChunkSize
            val isLast = (currentChunkEnd == lastChunkIndex) || (chunkVaultStart + totalFramedChunkSize >= manifestOffset)
            val chunkBytes = readChunkBytesFn(currentChunkEnd)
            val plain = decryptChunk(secretKey, chunkBytes, baseIV, currentChunkEnd, isLast, header.salt)
            val merged = ByteArray(spanBytes.size + plain.size)
            System.arraycopy(spanBytes, 0, merged, 0, spanBytes.size)
            System.arraycopy(plain, 0, merged, spanBytes.size, plain.size)
            spanBytes = merged
        }

        var payloadStart = relPos
        var payloadLength = entry.compressedSize.toInt()
        var isDeflated = entry.isCompressed

        if (relPos + 30 <= spanBytes.size) {
            val view = ByteBuffer.wrap(spanBytes).order(ByteOrder.LITTLE_ENDIAN)
            val sig = view.getInt(relPos)
            if (sig == 0x04034b50) { // PK\x03\x04
                val method = view.getShort(relPos + 8).toInt() and 0xFFFF
                isDeflated = (method == 8) || (method != 0 && entry.isCompressed)
                val nameLen = view.getShort(relPos + 26).toInt() and 0xFFFF
                val extraLen = view.getShort(relPos + 28).toInt() and 0xFFFF
                payloadStart = relPos + 30 + nameLen + extraLen
                val headerCompSize = view.getInt(relPos + 18).toLong() and 0xFFFFFFFFL
                if (payloadLength <= 0 && headerCompSize > 0) {
                    payloadLength = headerCompSize.toInt()
                } else if (payloadLength <= 0 && !isDeflated && entry.sizeBytes > 0) {
                    payloadLength = entry.sizeBytes.toInt()
                }
            }
        }

        while (payloadStart + payloadLength > spanBytes.size && currentChunkEnd < lastChunkIndex) {
            currentChunkEnd++
            val chunkVaultStart = V3_HEADER_SIZE + currentChunkEnd.toLong() * totalFramedChunkSize
            val isLast = (currentChunkEnd == lastChunkIndex) || (chunkVaultStart + totalFramedChunkSize >= manifestOffset)
            val chunkBytes = readChunkBytesFn(currentChunkEnd)
            val plain = decryptChunk(secretKey, chunkBytes, baseIV, currentChunkEnd, isLast, header.salt)
            val merged = ByteArray(spanBytes.size + plain.size)
            System.arraycopy(spanBytes, 0, merged, 0, spanBytes.size)
            System.arraycopy(plain, 0, merged, spanBytes.size, plain.size)
            spanBytes = merged
        }

        val rawPayload = if (payloadLength <= 0) {
            ByteArray(0)
        } else {
            val payloadEnd = Math.min(spanBytes.size, payloadStart + payloadLength)
            if (payloadStart >= payloadEnd) ByteArray(0) else spanBytes.copyOfRange(payloadStart, payloadEnd)
        }

        val uncompressedBytes = if (isDeflated && rawPayload.isNotEmpty()) {
            val inflater = Inflater(true)
            try {
                inflater.setInput(rawPayload)
                val bos = ByteArrayOutputStream(entry.sizeBytes.toInt().coerceAtLeast(1024))
                val tempBuf = ByteArray(BUFFER_SIZE)
                while (!inflater.finished()) {
                    val count = inflater.inflate(tempBuf)
                    if (count == 0 && inflater.needsInput()) break
                    bos.write(tempBuf, 0, count)
                }
                bos.toByteArray()
            } finally {
                inflater.end()
            }
        } else {
            rawPayload
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(uncompressedBytes)

        val ext = outputFile.extension.lowercase(Locale.ROOT)
        val mimeType = resolveMimeType(ext)
        var textPreview: String? = null
        if (mimeType.startsWith("text/") && outputFile.length() < 256 * 1024) {
            textPreview = try {
                outputFile.readText(Charsets.UTF_8).take(2000)
            } catch (_: Exception) {
                null
            }
        }

        val finalUri = try {
            Uri.fromFile(outputFile)
        } catch (_: Throwable) {
            null
        }

        return entry.copy(
            sizeBytes = outputFile.length(),
            localFileUri = finalUri,
            textContentPreview = textPreview
        )
    }

    class V3DecryptedInputStream(
        private val inStream: InputStream,
        private val secretKey: SecretKey,
        private val baseIVPrefix: ByteArray,
        private val salt: ByteArray,
        private val manifestOffset: Long,
        private val onChunkProgress: ((Float) -> Unit)? = null
    ) : InputStream() {
        private var currentFileOffset = V3_HEADER_SIZE.toLong()
        private var chunkIndex = 0
        private var currentChunkBuffer: ByteArray? = null
        private var currentChunkPos = 0

        override fun read(): Int {
            val b = ByteArray(1)
            val read = read(b, 0, 1)
            return if (read == -1) -1 else (b[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!ensureBuffer()) return -1

            val available = (currentChunkBuffer?.size ?: 0) - currentChunkPos
            val toRead = Math.min(len, available)
            System.arraycopy(currentChunkBuffer!!, currentChunkPos, b, off, toRead)
            currentChunkPos += toRead
            return toRead
        }

        private fun ensureBuffer(): Boolean {
            while (currentChunkBuffer == null || currentChunkPos >= currentChunkBuffer!!.size) {
                if (currentFileOffset >= manifestOffset) {
                    return false
                }
                val lenBytes = ByteArray(CHUNK_HEADER_SIZE)
                var readLen = 0
                while (readLen < CHUNK_HEADER_SIZE) {
                    val r = inStream.read(lenBytes, readLen, CHUNK_HEADER_SIZE - readLen)
                    if (r == -1) break
                    readLen += r
                }
                if (readLen != CHUNK_HEADER_SIZE) return false
                currentFileOffset += CHUNK_HEADER_SIZE

                val payloadLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int
                val encryptedChunkWithTag = ByteArray(payloadLen + TAG_LENGTH)
                var readTotal = 0
                while (readTotal < encryptedChunkWithTag.size) {
                    val r = inStream.read(encryptedChunkWithTag, readTotal, encryptedChunkWithTag.size - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                if (readTotal != encryptedChunkWithTag.size) return false
                currentFileOffset += readTotal

                val isLast = (currentFileOffset >= manifestOffset)
                val fullChunk = ByteArray(CHUNK_HEADER_SIZE + encryptedChunkWithTag.size)
                System.arraycopy(lenBytes, 0, fullChunk, 0, CHUNK_HEADER_SIZE)
                System.arraycopy(encryptedChunkWithTag, 0, fullChunk, CHUNK_HEADER_SIZE, encryptedChunkWithTag.size)

                val plaintext = decryptChunk(secretKey, fullChunk, baseIVPrefix, chunkIndex, isLast, salt)
                currentChunkBuffer = plaintext
                currentChunkPos = 0
                chunkIndex++

                val pct = ((currentFileOffset.toFloat() / manifestOffset) * 100f).coerceIn(0f, 98f)
                onChunkProgress?.invoke(pct)
            }
            return true
        }
    }

    suspend fun decryptV1OrV2Vault(
        context: Context,
        vaultUri: Uri,
        outputDir: File,
        password: CharArray,
        keyfileHash: ByteArray?,
        onProgress: (Float, String) -> Unit
    ): List<DecryptedFileEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<DecryptedFileEntry>()
        val tempZipFile = File(outputDir, "temp_decrypted.zip")

        context.contentResolver.openInputStream(vaultUri)?.use { inStream ->
            val header = parseVaultHeader(inStream)
            val secretKey = if (header.version == VaultVersion.V2) {
                deriveKeyV2(password, header.salt, keyfileHash)
            } else {
                deriveKeyV1(password, header.salt)
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, header.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            onProgress(30f, "Decrypting standard vault...")
            val ciphertext = inStream.readBytes()
            val decryptedBytes = cipher.doFinal(ciphertext)

            tempZipFile.writeBytes(decryptedBytes)

            onProgress(70f, "Unpacking archive...")
            ZipInputStream(FileInputStream(tempZipFile).buffered()).use { zis ->
                var zipEntry: ZipEntry?
                while (zis.nextEntry.also { zipEntry = it } != null) {
                    val entry = zipEntry!!
                    val targetFile = File(outputDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).buffered().use { fos ->
                            zis.copyTo(fos)
                        }
                        val ext = targetFile.extension.lowercase(Locale.ROOT)
                        val mimeType = resolveMimeType(ext)
                        entries.add(
                            DecryptedFileEntry(
                                path = entry.name,
                                name = targetFile.name,
                                sizeBytes = targetFile.length(),
                                isDirectory = false,
                                mimeType = mimeType,
                                localFileUri = Uri.fromFile(targetFile)
                            )
                        )
                    }
                    zis.closeEntry()
                }
            }
            tempZipFile.delete()
        }
        onProgress(100f, "Done")
        return@withContext entries
    }

    private fun resolveMimeType(ext: String): String {
        return when (ext) {
            "png", "jpg", "jpeg", "webp", "gif", "svg" -> "image/$ext"
            "txt", "md", "json", "xml", "html", "js", "css", "kt", "java", "py", "c", "cpp" -> "text/plain"
            "pdf" -> "application/pdf"
            "mp4", "mkv", "mov", "webm" -> "video/$ext"
            "mp3", "wav", "ogg", "flac" -> "audio/$ext"
            else -> "application/octet-stream"
        }
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
