# Milestone 1 Handoff Report: Manifest Size Bug Fix in `CryptoEngine.kt` (R1)

**Author**: M1 Explorer 1 (Crypto Engine Fix Specialist)  
**Date**: 2026-10-05T12:56:00Z  
**Target Repository**: `app-zevsafe` (`c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe`)  
**Scope**: Requirement R1 — Stream-Measured Manifest Sizing in `CryptoEngine.kt`

---

## 1. Observation

### 1.1 Root Cause in `CryptoEngine.kt`
- **File**: `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt`
- **Lines 458–462**:
  ```kotlin
  val totalSourceBytes = items.sumOf { it.sizeBytes }
  var processedSourceBytes = 0L

  val catalogEntries = JSONArray()
  var currentZipOffset = 0L
  ```
  `totalSourceBytes` relies entirely on `item.sizeBytes` reported by Android `ContentResolver` / Storage Access Framework (SAF).

- **Lines 519–542 (Precompressed / STORED items)**:
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
  - `actualSize` is declared as a local variable inside `if (isPrecompressed)` (`var actualSize = 0L` at line 528).
  - The pre-pass measures the exact stream byte count and calculates `crc.value`.

- **Lines 546–557 (Writing data to `ZipOutputStream`)**:
  ```kotlin
  zipOut.putNextEntry(zipEntry)

  context.contentResolver.openInputStream(item.uri)?.use { inStream ->
      var read: Int
      while (inStream.read(ioBuffer).also { read = it } != -1) {
          zipOut.write(ioBuffer, 0, read)
          processedSourceBytes += read
          if (totalSourceBytes > 0) {
              val pct = (processedSourceBytes.toFloat() / totalSourceBytes) * 100f
              onProgress(pct.coerceIn(0f, 98f), processedSourceBytes, totalSourceBytes)
          }
      }
  }
  zipOut.closeEntry()
  ```
  - For DEFLATED files (`isPrecompressed == false`), `crc` is **never updated** during write; `crc.value` remains `0L`.
  - For DEFLATED files, uncompressed bytes are never measured from the stream, and `zipEntry.compressedSize` is never retrieved after `zipOut.closeEntry()`.

- **Lines 559–568 (Manifest JSON serialization)**:
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
  - Line 562: writes `"size": item.sizeBytes`. `actualSize` from line 528 is completely discarded!
  - Line 563: writes `"compressedSize": if (isPrecompressed) item.sizeBytes else item.sizeBytes`, evaluating to `item.sizeBytes` in both branches.
  - If `item.sizeBytes` was `0L` (a frequent occurrence with Android `ContentResolver` queries on virtual files, cloud documents, or media providers), `"size": 0` and `"compressedSize": 0` are written to the encrypted manifest JSON envelope.
  - For DEFLATED files, line 566 writes `"crc32": 0` because `crc.update` was never invoked.

- **Lines 579–584 (Root Manifest JSON)**:
  ```kotlin
  val catalogRoot = JSONObject().apply {
      put("version", 3)
      put("totalSize", totalSourceBytes)
      put("fileCount", items.size)
      put("files", catalogEntries)
  }
  ```
  - Line 581 writes `"totalSize": totalSourceBytes`. If items reported 0 bytes, the root manifest records `"totalSize": 0`.

### 1.2 Impact on Decryption & Cross-Platform Unpacking
- **File**: `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt` (lines 406–428, `readV3VaultManifest`):
  ```kotlin
  val size = item.optLong("size", item.optLong("uncompressedSize", 0L))
  val compSize = item.optLong("compressedSize", size)
  ...
  chunkStart = (lho / DEFAULT_CHUNK_SIZE).toInt(),
  chunkEnd = ((lho + 30 + path.length + compSize) / DEFAULT_CHUNK_SIZE).toInt()
  ```
  - When `compSize == 0`, `chunkEnd` evaluates to `(lho + 30 + path.length) / DEFAULT_CHUNK_SIZE`. If the file spans multiple 4 MB chunks, `chunkEnd` truncates prematurely, omitting payload chunks.
  - In the UI, the vault and files display as `0 B`.
- **File**: `zevsafe/js/stream-unpacker.js` (lines 1100–1135, Web ZevSafe unpacker):
  ```javascript
  let payloadLength = normalized.compressedSize;
  ...
  const payloadEnd = Math.min(spanBytes.byteLength, payloadStart + payloadLength);
  const rawPayload = spanBytes.subarray(payloadStart, payloadEnd);
  ...
  if (normalized.crc32 !== undefined && normalized.crc32 !== null) {
      const computedCrc = crc32(extractedBytes);
      if ((computedCrc >>> 0) !== (normalized.crc32 >>> 0)) {
          throw createOperationError(`CRC-32 checksum mismatch...`);
      }
  }
  ```
  - If `compressedSize == 0`, `rawPayload` is 0 bytes (`subarray(payloadStart, payloadStart)`). The extracted file is empty, length validation fails, and CRC-32 checksum mismatch aborts decryption.

---

## 2. Logic Chain

1. **Premise**: In Android SAF (`DocumentFile`, `ContentResolver.query(OpenableColumns.SIZE)`), size queries can return `null` or `0L` for virtual files, camera intents, streamed content, or dynamic document providers.
2. **Observation**: `CryptoEngine.encryptItemsToV3Vault` receives `SelectedItem(sizeBytes = 0L)`.
3. **Observation**: For `isPrecompressed = true`, lines 531–537 correctly read the stream, compute `crc.value`, and compute `actualSize`.
4. **Observation**: `actualSize` is locally scoped inside `if (isPrecompressed)` and never passed to `entryJson` (line 562 references `item.sizeBytes`).
5. **Observation**: For `isPrecompressed = false` (DEFLATED files), lines 530–537 are skipped. During writing, `crc.update` is never called, and uncompressed/compressed sizes are not tracked.
6. **Deduction**: The manifest JSON contains `"size": 0`, `"compressedSize": 0`, and `"crc32": 0`.
7. **Observation**: In `readV3VaultManifest`, `chunkEnd` is computed as `((lho + 30 + path.length + compSize) / DEFAULT_CHUNK_SIZE)`. If `compSize == 0`, `chunkEnd` fails to encompass the payload chunk range.
8. **Observation**: In `StreamUnpacker.js` and targeted chunk extractors, extracting 0 bytes when the stream expected actual payload causes CRC-32 checksum failures and empty extracted files.
9. **Observation**: In `catalogRoot`, `"totalSize": totalSourceBytes` records 0 instead of the real archive data size.
10. **Conclusion**: To fix R1, `measuredUncompressedSize` and `measuredCompressedSize` must be captured in the outer item loop for all items (STORED and DEFLATED), and written directly into `entryJson` (`"size"`, `"uncompressedSize"`, and `"compressedSize"`), along with stream-computed `"crc32"`, and `catalogRoot.totalSize` must sum measured uncompressed sizes across all items.

---

## 3. Caveats

1. **True Zero-Byte Files**: If a file truly contains 0 bytes on disk, `measuredUncompressedSize` will legitimately be 0, `measuredCompressedSize` will be 0, and `crc.value` will be 0. The logic must handle this without falling back to negative or undefined values.
2. **Java `ZipOutputStream` Requirements**:
   - For `ZipEntry.STORED`: `ZipOutputStream.putNextEntry()` throws `ZipException` unless `size`, `compressedSize`, and `crc` are pre-set. A stream pre-pass is strictly mandatory for STORED files.
   - For `ZipEntry.DEFLATED`: `size`, `compressedSize`, and `crc` must NOT be pre-set. They are written via a Data Descriptor on `closeEntry()`, and `zipEntry.compressedSize` is populated by `ZipOutputStream` after `closeEntry()`.
3. **V1 and V2 Compatibility**: V1 and V2 vaults are monolithic AES-GCM containers that do not have streaming chunk headers or tail manifest envelopes. Modifying `encryptItemsToV3Vault` does not alter V1/V2 vault structures; backward compatibility for decrypting V1/V2 vaults remains 100% intact.
4. **JVM Test Environment**: `CryptoUnitTest.kt` runs as a pure JVM test. To enable unit testing of `encryptItemsToV3Vault` without requiring Android `Context` or Robolectric, an optional `inputStreamProvider: ((SelectedItem) -> InputStream?)? = null` parameter allows test harnesses to supply mock streams directly.

---

## 4. Conclusion & Actionable Implementation Plan

### 4.1 Write Ownership
- **Primary Production File**: `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt` (Owned by M1 & M2)
- **Unit Test File**: `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt` (Owned by M1 & E2E)

### 4.2 Exact Implementation Plan for M1 Worker

#### Step 1: Update `CryptoEngine.encryptItemsToV3Vault` signature
Add an optional `inputStreamProvider` parameter with default `null`:
```kotlin
suspend fun encryptItemsToV3Vault(
    context: Context?,
    items: List<SelectedItem>,
    outputVaultFile: File,
    password: CharArray,
    keyfileHash: ByteArray?,
    inputStreamProvider: ((SelectedItem) -> InputStream?)? = null,
    onProgress: (Float, Long, Long) -> Unit
) = withContext(Dispatchers.IO) {
```
*Note: Existing call sites in `VaultViewModel.kt` do not specify `inputStreamProvider` and continue to work identically without changes.*

#### Step 2: Stream-measured sizing in the file iteration loop
Replace lines 458–595 in `CryptoEngine.kt` with the following implementation:

```kotlin
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

                        // Measure exact length and CRC32 in pre-pass (mandatory for ZipEntry.STORED)
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
                        val zipReportedCompSize = zipEntry.compressedSize
                        measuredCompressedSize = if (zipReportedCompSize > 0L) {
                            zipReportedCompSize
                        } else {
                            val delta = currentZipOffset - entryOffset
                            val headerEstimate = 30L + item.relativePath.toByteArray(Charsets.UTF_8).size
                            val estimatedPayload = delta - headerEstimate
                            if (estimatedPayload > 0L) estimatedPayload else writtenUncompressedBytes
                        }
                    }

                    val finalSize = if (measuredUncompressedSize > 0L) {
                        measuredUncompressedSize
                    } else if (item.sizeBytes > 0L) {
                        item.sizeBytes
                    } else {
                        0L
                    }

                    val finalCompSize = if (measuredCompressedSize > 0L) {
                        measuredCompressedSize
                    } else {
                        finalSize
                    }

                    totalMeasuredBytes += finalSize

                    val entryJson = JSONObject().apply {
                        put("path", item.relativePath)
                        put("name", item.name)
                        put("size", finalSize)
                        put("uncompressedSize", finalSize)
                        put("compressedSize", finalCompSize)
                        put("localHeaderOffset", entryOffset)
                        put("compressed", !isPrecompressed)
                        put("crc32", crc.value)
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
```

---

## 5. Verification Method

### 5.1 Unit Tests for Independent Verification
Add the following unit tests to `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`:

```kotlin
    @Test
    fun testZeroReportedSizeManifestRecording() = kotlinx.coroutines.runBlocking {
        val tempVault = java.io.File.createTempFile("test_zero_size_vault", ".zev").apply { deleteOnExit() }
        val testPayload = ByteArray(128 * 1024) { (it % 251).toByte() } // 128 KB precompressed image data
        val expectedCrc = java.util.zip.CRC32().apply { update(testPayload) }.value

        val dummyUri = object : android.net.Uri() {
            override fun isHierarchical() = false
            override fun isRelative() = false
            override fun getScheme() = "test"
            override fun getSchemeSpecificPart() = "test"
            override fun getAuthority() = "test"
            override fun getUserInfo() = null
            override fun getHost() = "test"
            override fun getPort() = -1
            override fun getPath() = "/test.jpg"
            override fun getQuery() = null
            override fun getFragment() = null
            override fun getPathSegments() = emptyList<String>()
            override fun getLastPathSegment() = "test.jpg"
            override fun buildUpon() = null
            override fun toString() = "test://test.jpg"
        }

        val itemWithZeroSize = SelectedItem(
            uri = dummyUri,
            name = "photo.jpg",
            relativePath = "photo.jpg",
            sizeBytes = 0L // Simulating provider returning 0L
        )

        val password = "TestPassword123!".toCharArray()

        CryptoEngine.encryptItemsToV3Vault(
            context = null,
            items = listOf(itemWithZeroSize),
            outputVaultFile = tempVault,
            password = password,
            keyfileHash = null,
            inputStreamProvider = { java.io.ByteArrayInputStream(testPayload) }
        ) { _, _, _ -> }

        // Read back header and manifest
        val header = java.io.FileInputStream(tempVault).use { CryptoEngine.parseVaultHeader(it) }
        assertEquals(VaultVersion.V3, header.version)

        val secretKey = CryptoEngine.deriveKeyV3(password, header.salt, null)
        val raf = java.io.RandomAccessFile(tempVault, "r")
        val manifestLength = (tempVault.length() - header.manifestOffset).toInt()
        val manifestBytes = ByteArray(manifestLength)
        raf.seek(header.manifestOffset)
        raf.readFully(manifestBytes)
        raf.close()

        val manifestJsonStr = CryptoEngine.decryptManifestEnvelope(manifestBytes, secretKey)
        val root = org.json.JSONObject(manifestJsonStr)

        assertEquals(128 * 1024L, root.getLong("totalSize"))
        assertEquals(1, root.getInt("fileCount"))

        val fileEntry = root.getJSONArray("files").getJSONObject(0)
        assertEquals("photo.jpg", fileEntry.getString("name"))
        assertEquals(128 * 1024L, fileEntry.getLong("size"))
        assertEquals(128 * 1024L, fileEntry.getLong("compressedSize"))
        assertEquals(expectedCrc, fileEntry.getLong("crc32"))
        assertFalse(fileEntry.getBoolean("compressed"))
    }
```

### 5.2 Build & Test Verification Commands
Run unit test suite:
```bash
./gradlew test
```
Expected result: `BUILD SUCCESSFUL`, 100% tests passing.

### 5.3 Invalidation Conditions
- Any implementation where `catalogEntries` writes `0L` when `item.sizeBytes == 0L` on a non-empty stream invalidates R1.
- Any implementation where `catalogRoot.totalSize` records `0L` when files are present in the vault invalidates R1.
- Any implementation where `crc32` is recorded as `0` for DEFLATED files invalidates cross-platform integrity verification with Web ZevSafe.
- Any implementation allocating $> 150\text{ MB}$ RAM violates the streaming memory bound constraint.
