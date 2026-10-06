# Handoff Report: Milestone 1 (R1) Crypto Unit Test Specialist

**Author**: M1 Explorer 2 (Crypto Unit Test Specialist)  
**Date**: 2026-10-05T18:25:00Z  
**Target Repository**: `app-zevsafe` (`c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe`)  
**Scope**: Unit test architecture, helper mocks/streams, and exact test specifications for R1 (0-byte reported file manifest size recording and roundtrip decryption integrity).

---

## 1. Observation

### 1.1 Existing Test Suite in `CryptoUnitTest.kt`
- **File**: `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt` (205 lines)
- Existing tests:
  1. `testV1KeyDerivationAndEncryption` (lines 22–41)
  2. `testV2KeyDerivationWithKeyfileXOR` (lines 43–63)
  3. `testV3HeaderMagicDetection` (lines 65–76)
  4. `testV3ChunkIVComputation` (lines 78–97)
  5. `testV3ChunkAADComputation` (lines 99–121)
  6. `testV3ChunkEncryptAndDecryptRoundTrip` (lines 123–135)
  7. `testV3ChunkTamperingDetection` (lines 137–157)
  8. `testV3ContainerHeaderSerializationAndParsing` (lines 159–181)
  9. `testV3ManifestEnvelopeEncryptionAndDecryption` (lines 183–194)
  10. `testPasswordEvaluator` (lines 196–203)
- **Direct Observation**:
  - All 10 existing tests run in pure JVM without invoking any Android platform APIs.
  - There are **zero** unit tests exercising `encryptItemsToV3Vault`, `readV3VaultManifest`, or `decryptV3VaultToDirectory`.
  - There are **zero** unit tests verifying end-to-end vault packing, ZIP catalog construction, or roundtrip archive unpacking.

### 1.2 Build & Test Dependencies in `app/build.gradle.kts`
- **File**: `app/build.gradle.kts`
- Lines 58–78:
  ```kotlin
  dependencies {
      ...
      testImplementation(libs.junit)
  }
  ```
- **Direct Observation**:
  - Only `junit:junit:4.13.2` is configured for unit testing.
  - Neither `Robolectric` nor `Mockito` (`mockito-core` / `mockito-kotlin`) is present in `build.gradle.kts` or `gradle/libs.versions.toml`.
  - `testOptions { unitTests.isReturnDefaultValues = true }` is currently absent from `android { ... }`.
  - Any direct invocation of Android framework stub classes (e.g., `android.net.Uri`, `android.content.Context`, `android.content.ContentResolver`) in JVM unit tests will throw:
    `java.lang.RuntimeException: Method ... not mocked. See https://developer.android.com/r/studio-ui/mock-classes.html`.

### 1.3 Production Crypto Pipeline in `CryptoEngine.kt`
- **File**: `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt`
- **Lines 520–542**:
  ```kotlin
  val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
  val isPrecompressed = PRE_COMPRESSED_EXTENSIONS.contains(ext)

  val entryOffset = currentZipOffset
  val zipEntry = ZipEntry(item.relativePath)
  val crc = CRC32()

  if (isPrecompressed) {
      zipEntry.method = ZipEntry.STORED
      var actualSize = 0L

      // Calculate CRC32 and measure exact byte length for STORED entry
      context.contentResolver.openInputStream(item.uri)?.use { inStream ->
          var read: Int
          while (inStream.read(ioBuffer).also { read = it } != -1) {
              crc.update(ioBuffer, 0, read)
              actualSize += read
          }
      } ?: throw java.io.IOException("Cannot open input stream for ${item.name}")

      zipEntry.size = actualSize
      zipEntry.compressedSize = actualSize
      zipEntry.crc = crc.value
  }
  ```
- **Lines 559–568**:
  ```kotlin
  val entryJson = JSONObject().apply {
      put("path", item.relativePath)
      put("name", item.name)
      put("size", item.sizeBytes)
      put("compressedSize", if (isPrecompressed) item.sizeBytes else item.sizeBytes)
      put("localHeaderOffset", entryOffset)
      put("compressed", !isPrecompressed)
      put("crc32", crc.value)
  }
  catalogEntries.put(entryJson)
  ```
- **Lines 579–584**:
  ```kotlin
  val catalogRoot = JSONObject().apply {
      put("version", 3)
      put("totalSize", totalSourceBytes)
      put("fileCount", items.size)
      put("files", catalogEntries)
  }
  ```
- **Direct Observation**:
  - In `encryptItemsToV3Vault`, `context` is used **exclusively** for `context.contentResolver.openInputStream(item.uri)`.
  - In `readV3VaultManifest`, `context` is used **exclusively** for `context.contentResolver.openInputStream(vaultUri)`.
  - In `decryptV3VaultToDirectory`, `context` is used **exclusively** for `context.contentResolver.openInputStream(vaultUri)`.
  - If `item.sizeBytes == 0L`, `catalogEntries` writes `"size": 0`, `"compressedSize": 0`, and `catalogRoot` writes `"totalSize": 0`.
  - For non-precompressed files (`isPrecompressed == false`), lines 527–542 are skipped, so `crc` is never updated and `"crc32": 0` is written.

### 1.4 Data Model Contract in `CryptoModels.kt`
- **File**: `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoModels.kt`
- Lines 18–24:
  ```kotlin
  data class SelectedItem(
      val uri: Uri,
      val name: String,
      val relativePath: String,
      val sizeBytes: Long,
      val isDirectory: Boolean = false
  )
  ```
- Lines 119–133:
  ```kotlin
  data class DecryptedFileEntry(
      val path: String,
      val name: String,
      val sizeBytes: Long,
      val isDirectory: Boolean,
      val mimeType: String,
      val localFileUri: Uri? = null,
      val textContentPreview: String? = null,
      val localHeaderOffset: Long = 0L,
      val compressedSize: Long = 0L,
      val isCompressed: Boolean = false,
      val crc32: Long = 0L,
      val chunkStart: Int = 0,
      val chunkEnd: Int = 0
  )
  ```

---

## 2. Logic Chain

1. **Premise**: When an Android application uses Storage Access Framework (`DocumentFile`, `ContentResolver.query(OpenableColumns.SIZE)`), virtual files, media store URIs, or camera output streams frequently report `0L` or `null` for file size prior to reading the stream.
2. **Observation**: In `encryptItemsToV3Vault`, `item.sizeBytes` is `0L`.
3. **Observation**: Although `actualSize` and `crc` are accurately computed for pre-compressed files in the stream reading loop (lines 531–537), `actualSize` is a local variable scoped only within `if (isPrecompressed)`.
4. **Observation**: Lines 562–563 write `"size": item.sizeBytes` (0) and `"compressedSize": item.sizeBytes` (0).
5. **Observation**: For compressible files (`.txt`, `.json`), `crc` is never updated at all, writing `"crc32": 0`, `"size": 0`, `"compressedSize": 0`.
6. **Observation**: In `readV3VaultManifest`, `chunkEnd = ((lho + 30 + path.length + compSize) / DEFAULT_CHUNK_SIZE)`. When `compSize == 0`, `chunkEnd` fails to span beyond chunk 0.
7. **Observation**: Acceptance criteria R1 states:
   > "Encrypt a test file whose ContentResolver-reported size is 0 bytes, then decrypt it — the extracted file must be byte-identical to the original."
   > "Verify that manifest JSON records the actual stream-measured uncompressed and compressed sizes (> 0)."
8. **Deduction (Test Design)**: To prove the bug is resolved and will never regress, the test suite must:
   - Provide a simulated file whose `SelectedItem.sizeBytes == 0L`, while its input stream delivers real data (> 0 bytes).
   - Test **Pre-compressed (STORED)** formats (e.g. `.jpg`) and verify:
     - `manifest.files[0].sizeBytes == streamByteLength > 0`
     - `manifest.files[0].compressedSize == streamByteLength > 0`
     - `manifest.files[0].crc32 == streamCRC32 != 0L`
     - `manifest.totalSize == streamByteLength > 0`
   - Test **Compressible (DEFLATED)** formats (e.g. `.txt`) and verify:
     - `manifest.files[0].sizeBytes == streamByteLength > 0`
     - `manifest.files[0].compressedSize > 0 && manifest.files[0].compressedSize <= streamByteLength`
     - `manifest.files[0].crc32 == streamCRC32 != 0L`
     - `manifest.files[0].isCompressed == true`
   - Execute full roundtrip decryption via `decryptV3VaultToDirectory` (or stream decryptor) and assert byte-for-byte identity against the original input bytes via `assertArrayEquals`.
9. **Deduction (JVM Test Execution & Mocks)**:
   - Because `CryptoUnitTest.kt` is a pure JVM test and AGP's `android.jar` throws `RuntimeException("Method ... not mocked")`, tests cannot invoke `Uri.parse()` or `context.contentResolver.openInputStream()` directly without a mock or decoupling abstraction.
   - **Recommended Architecture (Pattern A)**: Provide a stream-based overload in `CryptoEngine`:
     ```kotlin
     suspend fun encryptItemsToV3Vault(
         streamOpener: (SelectedItem) -> InputStream,
         items: List<SelectedItem>,
         outputVaultFile: File,
         password: CharArray,
         keyfileHash: ByteArray? = null,
         onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }
     )
     ```
     and overload:
     ```kotlin
     suspend fun readV3VaultManifest(
         vaultStream: InputStream,
         password: CharArray,
         keyfileHash: ByteArray? = null
     ): VaultCatalog
     ```
     and overload:
     ```kotlin
     suspend fun decryptV3VaultToDirectory(
         vaultStream: InputStream,
         outputDir: File,
         password: CharArray,
         keyfileHash: ByteArray? = null,
         onProgress: (Float, String) -> Unit = { _, _ -> }
     ): List<DecryptedFileEntry>
     ```
     The existing `(context, items, ...)` and `(context, vaultUri, ...)` methods delegate directly to these stream overloads.
   - **Alternative Architecture (Pattern B)**: Add Mockito (`testImplementation("org.mockito:mockito-core:5.12.0")`) and configure `unitTests.isReturnDefaultValues = true` in `build.gradle.kts`. Mock `Context`, `ContentResolver`, and `Uri`.

---

## 3. Caveats

1. **JVM Memory Limits in Unit Tests**: While STREAM AEAD is designed to stream multi-gigabyte vaults in under 150 MB RAM on Android devices, unit tests in `CryptoUnitTest.kt` should use fixture payloads between 16 KB and 4.5 MB. This ensures tests run in under 500 ms in CI while still exercising chunk boundaries (the default chunk size is 4 MB).
2. **Coroutines Execution**: All three functions (`encryptItemsToV3Vault`, `readV3VaultManifest`, `decryptV3VaultToDirectory`) are `suspend` functions using `withContext(Dispatchers.IO)`. In JUnit 4 test methods, invocations must be enclosed in `kotlinx.coroutines.runBlocking { ... }`.
3. **ZIP STORED Specification Constraint**: In standard Java `ZipOutputStream`, if an entry uses `ZipEntry.STORED`, `zipEntry.size`, `zipEntry.compressedSize`, and `zipEntry.crc` must be set **before** calling `zipOut.putNextEntry(zipEntry)`. Otherwise `ZipException` is thrown. The pre-measurement stream pass for pre-compressed files is mandatory for both ZIP compliance and manifest accuracy.
4. **No External Network Dependencies**: Per project security rules, no new networking dependencies or mock servers may be introduced. All tests must be 100% offline and in-memory or using local temporary files (`File.createTempFile`).

---

## 4. Conclusion & Actionable Test Blueprint

### 4.1 Production Code Prerequisite for Testability (`CryptoEngine.kt`)

To enable seamless JVM unit testing without brittle mocking frameworks, the worker should overload `encryptItemsToV3Vault`, `readV3VaultManifest`, and `decryptV3VaultToDirectory` in `CryptoEngine.kt` as follows:

```kotlin
// In CryptoEngine.kt:

/**
 * Core stream-based encryption function. Enables direct JVM unit testing and custom stream routing.
 */
suspend fun encryptItemsToV3Vault(
    streamOpener: (SelectedItem) -> InputStream,
    items: List<SelectedItem>,
    outputVaultFile: File,
    password: CharArray,
    keyfileHash: ByteArray? = null,
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

                if (isPrecompressed) {
                    zipEntry.method = ZipEntry.STORED

                    // Pre-measure exact byte length and CRC32 for STORED entry
                    streamOpener(item).use { inStream ->
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
                streamOpener(item).use { inStream ->
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
                        }
                    }
                }
                zipOut.closeEntry()

                if (!isPrecompressed) {
                    measuredUncompressedSize = writtenUncompressedBytes
                    val zipReportedCompSize = zipEntry.compressedSize
                    measuredCompressedSize = if (zipReportedCompSize > 0) {
                        zipReportedCompSize
                    } else {
                        (currentZipOffset - entryOffset)
                    }
                }

                val finalSize = if (measuredUncompressedSize > 0L) measuredUncompressedSize else item.sizeBytes
                val finalCompSize = if (measuredCompressedSize > 0L) measuredCompressedSize else finalSize
                totalMeasuredBytes += finalSize

                val entryJson = JSONObject().apply {
                    put("path", item.relativePath)
                    put("name", item.name)
                    put("size", finalSize)
                    put("compressedSize", finalCompSize)
                    put("localHeaderOffset", entryOffset)
                    put("compressed", !isPrecompressed)
                    put("crc32", crc.value)
                }
                catalogEntries.put(entryJson)
            }
        }

        flushChunk(isLast = true)
        val manifestOffset = raf.filePointer

        val catalogRoot = JSONObject().apply {
            put("version", 3)
            put("totalSize", if (totalMeasuredBytes > 0L) totalMeasuredBytes else totalSourceBytes)
            put("fileCount", items.size)
            put("files", catalogEntries)
        }
        val manifestEnvelope = encryptManifestEnvelope(catalogRoot.toString(), secretKey, salt)
        raf.write(manifestEnvelope)

        raf.seek(49)
        val offsetBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        offsetBuf.putLong(manifestOffset)
        raf.write(offsetBuf.array())
    }

    onProgress(100f, totalSourceBytes, totalSourceBytes)
}

/**
 * Android Context convenience overload.
 */
suspend fun encryptItemsToV3Vault(
    context: Context,
    items: List<SelectedItem>,
    outputVaultFile: File,
    password: CharArray,
    keyfileHash: ByteArray? = null,
    onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }
) = encryptItemsToV3Vault(
    streamOpener = { item ->
        context.contentResolver.openInputStream(item.uri)
            ?: throw java.io.IOException("Cannot open input stream for ${item.name}")
    },
    items = items,
    outputVaultFile = outputVaultFile,
    password = password,
    keyfileHash = keyfileHash,
    onProgress = onProgress
)

/**
 * Core InputStream-based manifest parser.
 */
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

    var skipped = V3_HEADER_SIZE.toLong()
    while (skipped < manifestOffset) {
        val skipStep = inStream.skip(manifestOffset - skipped)
        if (skipStep <= 0) break
        skipped += skipStep
    }

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
                chunkStart = (lho / DEFAULT_CHUNK_SIZE).toInt(),
                chunkEnd = ((lho + 30 + path.length + compSize) / DEFAULT_CHUNK_SIZE).toInt()
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

/**
 * Android Context convenience overload.
 */
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

/**
 * Core InputStream-based decryption to directory.
 */
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

                entries.add(
                    DecryptedFileEntry(
                        path = entry.name,
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

    onProgress(100f, "Extraction complete")
    return@withContext entries
}

/**
 * Android Context convenience overload.
 */
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
```

---

### 4.2 Exact Unit Tests for `CryptoUnitTest.kt`

The worker should add the following 4 unit tests and helpers to `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`:

```kotlin
// In app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt:

import com.zevbuild.zevsafe.crypto.SelectedItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.CRC32

// ── R1 Test 1: Pre-compressed file (STORED) with sizeBytes = 0L ───────────────
@Test
fun testZeroReportedSizePrecompressedStoredManifestAndDecryption() = runBlocking {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "zevsafe_test_stored_${System.currentTimeMillis()}").apply { mkdirs() }
    val vaultFile = File(tempDir, "test_stored.zev")
    val extractDir = File(tempDir, "extracted").apply { mkdirs() }

    try {
        // 1. Generate 128 KB of pseudo-random bytes simulating an image file (e.g. JPG)
        val originalBytes = ByteArray(128 * 1024)
        SecureRandom().nextBytes(originalBytes)

        val crcCalculator = CRC32().apply { update(originalBytes) }
        val expectedCrc = crcCalculator.value

        // 2. Construct item with simulated 0-byte reported size
        val item = SelectedItem(
            uri = android.net.Uri.EMPTY, // or mock URI
            name = "camera_capture.jpg",
            relativePath = "DCIM/camera_capture.jpg",
            sizeBytes = 0L // CRITICAL: Simulated 0L provider size
        )

        val password = "SecureStoredPassword123!".toCharArray()

        // 3. Encrypt using stream provider supplying the 128 KB payload
        CryptoEngine.encryptItemsToV3Vault(
            streamOpener = { ByteArrayInputStream(originalBytes) },
            items = listOf(item),
            outputVaultFile = vaultFile,
            password = password
        )

        // 4. Verify Manifest JSON records stream-measured sizes (> 0)
        val catalog = FileInputStream(vaultFile).use { inStream ->
            CryptoEngine.readV3VaultManifest(inStream, password)
        }

        assertEquals("Catalog file count must be 1", 1, catalog.fileCount)
        assertEquals("Manifest root totalSize must be measured size (128 KB)", 128 * 1024L, catalog.totalSize)

        val entry = catalog.files.first()
        assertEquals("camera_capture.jpg", entry.name)
        assertEquals("DCIM/camera_capture.jpg", entry.path)
        assertEquals("Manifest entry sizeBytes must be measured uncompressed size", 128 * 1024L, entry.sizeBytes)
        assertEquals("Manifest entry compressedSize must match STORED byte length", 128 * 1024L, entry.compressedSize)
        assertFalse("Pre-compressed files must not have compressed flag set", entry.isCompressed)
        assertEquals("Manifest entry crc32 must match IEEE 802.3 stream CRC", expectedCrc, entry.crc32)
        assertTrue("CRC32 must be non-zero", entry.crc32 != 0L)

        // 5. Verify roundtrip decryption recovers exact original bytes
        val extractedEntries = FileInputStream(vaultFile).use { inStream ->
            CryptoEngine.decryptV3VaultStreamToDirectory(inStream, extractDir, password)
        }

        assertEquals(1, extractedEntries.size)
        val extractedFile = File(extractDir, "DCIM/camera_capture.jpg")
        assertTrue("Extracted file must exist on disk", extractedFile.exists())
        assertEquals("Extracted file length must match original", originalBytes.size.toLong(), extractedFile.length())
        assertArrayEquals("Extracted bytes must be 100% byte-identical to original bytes", originalBytes, extractedFile.readBytes())

    } finally {
        tempDir.deleteRecursively()
    }
}

// ── R1 Test 2: Compressible file (DEFLATED) with sizeBytes = 0L ────────────────
@Test
fun testZeroReportedSizeCompressibleDeflatedManifestAndDecryption() = runBlocking {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "zevsafe_test_deflated_${System.currentTimeMillis()}").apply { mkdirs() }
    val vaultFile = File(tempDir, "test_deflated.zev")
    val extractDir = File(tempDir, "extracted").apply { mkdirs() }

    try {
        // 1. Generate 64 KB of compressible repeating text
        val textBuilder = StringBuilder()
        for (i in 1..1000) {
            textBuilder.append("Line $i: ZevSafe Zero-Knowledge Stream AEAD Manifest Size Test Data Chunk\n")
        }
        val originalBytes = textBuilder.toString().toByteArray(Charsets.UTF_8)
        val expectedSize = originalBytes.size.toLong()

        val crcCalculator = CRC32().apply { update(originalBytes) }
        val expectedCrc = crcCalculator.value

        // 2. Construct item with simulated 0-byte reported size
        val item = SelectedItem(
            uri = android.net.Uri.EMPTY,
            name = "system_audit.log",
            relativePath = "logs/system_audit.log",
            sizeBytes = 0L // CRITICAL: Simulated 0L provider size
        )

        val password = "DeflatedPassword789!".toCharArray()

        // 3. Encrypt using stream provider
        CryptoEngine.encryptItemsToV3Vault(
            streamOpener = { ByteArrayInputStream(originalBytes) },
            items = listOf(item),
            outputVaultFile = vaultFile,
            password = password
        )

        // 4. Verify Manifest JSON
        val catalog = FileInputStream(vaultFile).use { inStream ->
            CryptoEngine.readV3VaultManifest(inStream, password)
        }

        assertEquals("Catalog file count must be 1", 1, catalog.fileCount)
        assertEquals("Manifest root totalSize must match uncompressed measured size", expectedSize, catalog.totalSize)

        val entry = catalog.files.first()
        assertEquals("system_audit.log", entry.name)
        assertEquals("logs/system_audit.log", entry.path)
        assertEquals("Manifest entry sizeBytes must be measured uncompressed size", expectedSize, entry.sizeBytes)
        assertTrue("Manifest entry compressedSize must be non-zero", entry.compressedSize > 0L)
        assertTrue("Deflated compressedSize must be less than uncompressed size", entry.compressedSize < expectedSize)
        assertTrue("Compressible files must have compressed flag set", entry.isCompressed)
        assertEquals("Manifest entry crc32 must match calculated CRC", expectedCrc, entry.crc32)
        assertTrue("CRC32 must be non-zero", entry.crc32 != 0L)

        // 5. Verify roundtrip decryption
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

// ── R1 Test 3: Mixed multi-file vault with zero-byte reported sizes ────────────
@Test
fun testMultiFileVaultMixedZeroReportedSizesManifestAndDecryption() = runBlocking {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "zevsafe_test_multi_${System.currentTimeMillis()}").apply { mkdirs() }
    val vaultFile = File(tempDir, "test_multi.zev")
    val extractDir = File(tempDir, "extracted").apply { mkdirs() }

    try {
        // File 1: Pre-compressed image (STORED) 40 KB
        val imageBytes = ByteArray(40 * 1024).apply { SecureRandom().nextBytes(this) }
        val imageItem = SelectedItem(
            uri = android.net.Uri.EMPTY,
            name = "thumbnail.png",
            relativePath = "media/thumbnail.png",
            sizeBytes = 0L // Reported 0
        )

        // File 2: Compressible text (DEFLATED) 30 KB
        val textBytes = "ZevSafe Multi-File Stream AEAD Integrity Verification Record\n".repeat(500).toByteArray(Charsets.UTF_8)
        val textItem = SelectedItem(
            uri = android.net.Uri.EMPTY,
            name = "notes.txt",
            relativePath = "docs/notes.txt",
            sizeBytes = 0L // Reported 0
        )

        // File 3: Genuinely empty file (0 bytes stream)
        val emptyBytes = ByteArray(0)
        val emptyItem = SelectedItem(
            uri = android.net.Uri.EMPTY,
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

        // Encrypt
        CryptoEngine.encryptItemsToV3Vault(
            streamOpener = { item -> ByteArrayInputStream(streamMap[item.relativePath]!!) },
            items = listOf(imageItem, textItem, emptyItem),
            outputVaultFile = vaultFile,
            password = password
        )

        // Verify Manifest
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

        val textEntry = catalog.files.first { it.name == "notes.txt" }
        assertEquals(textBytes.size.toLong(), textEntry.sizeBytes)
        assertTrue(textEntry.compressedSize in 1L until textBytes.size.toLong())
        assertTrue(textEntry.isCompressed)

        val emptyEntry = catalog.files.first { it.name == "empty.marker" }
        assertEquals(0L, emptyEntry.sizeBytes)
        assertEquals(0L, emptyEntry.compressedSize)

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

// ── R1 Test 4: Multi-chunk boundary file (> 4 MB) with sizeBytes = 0L ───────────
@Test
fun testZeroReportedSizeMultiChunkSpanManifestAndDecryption() = runBlocking {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "zevsafe_test_multichunk_${System.currentTimeMillis()}").apply { mkdirs() }
    val vaultFile = File(tempDir, "test_multichunk.zev")
    val extractDir = File(tempDir, "extracted").apply { mkdirs() }

    try {
        // 4.5 MB payload (crosses 4 MB chunk boundary into chunk 1)
        val payloadSize = 4 * 1024 * 1024 + 512 * 1024
        val largeBytes = ByteArray(payloadSize)
        // Fill with pattern
        for (i in largeBytes.indices) {
            largeBytes[i] = (i % 251).toByte()
        }

        val item = SelectedItem(
            uri = android.net.Uri.EMPTY,
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
```

---

## 5. Verification Method

### 5.1 Test Execution Command
To independently verify the test suite:
```powershell
.\gradlew.bat test --tests "com.zevbuild.zevsafe.CryptoUnitTest"
```
Or on Linux/macOS:
```bash
./gradlew test --tests "com.zevbuild.zevsafe.CryptoUnitTest"
```

### 5.2 Expected Output
- `BUILD SUCCESSFUL`
- All 14 tests passing (10 existing + 4 new Milestone 1 tests).
- 0 failures, 0 errors.

### 5.3 Invalidation Conditions
- Any test assertion returning `0L` for `sizeBytes`, `compressedSize`, or `totalSize` when the input stream contained non-empty bytes invalidates R1.
- Any test where `assertArrayEquals(originalBytes, extractedFile.readBytes())` fails or throws `ZipException` invalidates decryption roundtrip integrity.
- Any implementation requiring `android.content.Context` that cannot execute in pure JVM JUnit 4 invalidates test reproducibility.
