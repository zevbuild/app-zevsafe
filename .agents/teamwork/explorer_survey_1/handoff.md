# Survey Report: Crypto & Extraction Specialist (R1 & R2)

**Author**: Survey Explorer 1 (Crypto & Extraction Specialist)  
**Date**: 2026-10-05T12:45:00Z  
**Target Repository**: `app-zevsafe` (`c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe`)  
**Scope**: Requirements R1 (Manifest Size Bug in `CryptoEngine.kt`) & R2 (Fast Single-File Extraction in `VaultViewModel.kt`)

---

## 1. Observation

### 1.1 R1: Manifest Size Population in `CryptoEngine.kt`
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
  - `actualSize` is declared as a local variable inside the `if (isPrecompressed)` block (`var actualSize = 0L` at line 528).
  - The stream is read completely, accurately measuring `actualSize` and computing the IEEE 802.3 `crc`.
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
  - In `entryJson`, line 562 records `"size": item.sizeBytes`. The measured `actualSize` is out of scope and completely ignored.
  - Line 563 records `"compressedSize": if (isPrecompressed) item.sizeBytes else item.sizeBytes`, which evaluates to `item.sizeBytes` in both branches.
  - If `item.sizeBytes` was reported as `0L` (common for Android `ContentResolver` queries on dynamic streams, media store URIs, or virtual files), `"size": 0` and `"compressedSize": 0` are written to the encrypted manifest JSON envelope.
  - For non-precompressed files (`isPrecompressed == false`), lines 530–537 are skipped. `crc` is never updated during streaming (lines 546–556), so line 566 writes `put("crc32", 0)`.
- **Lines 579–584**:
  ```kotlin
  val catalogRoot = JSONObject().apply {
      put("version", 3)
      put("totalSize", totalSourceBytes)
      put("fileCount", items.size)
      put("files", catalogEntries)
  }
  ```
  - `totalSourceBytes` is initialized at line 458 as `items.sumOf { it.sizeBytes }`. When items report 0 bytes, `catalogRoot` writes `"totalSize": 0`.
- **Lines 403–431 (`readV3VaultManifest`)**:
  ```kotlin
  val size = item.optLong("size", item.optLong("uncompressedSize", 0L))
  val compSize = item.optLong("compressedSize", size)
  val lho = item.optLong("localHeaderOffset", item.optLong("offset", 0L))
  val isComp = item.optBoolean("compressed", false)
  val crc = item.optLong("crc32", 0L)
  ...
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
  ```
  - If `size` and `compSize` are 0:
    - Files display as "0 B" in the UI.
    - `chunkEnd` becomes `((lho + 30 + path.length + 0) / DEFAULT_CHUNK_SIZE).toInt()`. If the file spans multiple chunks, `chunkEnd` only covers the local header and misses subsequent chunks.
    - In `StreamUnpacker.js` (lines 1100, 1118): `payloadLength = normalized.compressedSize` (0 bytes). Slice is empty, CRC fails, and length validation throws `OperationError`.

---

### 1.2 R2: Single-File Extraction in `VaultViewModel.kt`
- **File**: `app/src/main/java/com/zevbuild/zevsafe/viewmodel/VaultViewModel.kt`
- **Lines 829–871**:
  ```kotlin
  fun extractSingleFileIfNeeded(
      context: Context,
      entry: DecryptedFileEntry,
      onReady: (DecryptedFileEntry) -> Unit
  ) {
      if (entry.localFileUri != null && File(entry.localFileUri.path ?: "").exists()) {
          onReady(entry)
          return
      }
      val currentResult = _decryptionResult.value ?: return
      val vaultUri = _selectedDecryptVaultUri.value ?: return
      val password = _decryptPassword.value
      val keyfile = _decryptKeyfile.value
      val safeVaultBase = sanitizeFilename(currentResult.vaultName.substringBeforeLast('.'))

      viewModelScope.launch(Dispatchers.IO) {
          try {
              addLog("Extracting \"${entry.name}\" for preview...", LogType.INFO)
              val keyfileHash = if (keyfile != null) {
                  val (hashBytes, _) = CryptoEngine.hashKeyfile(context, keyfile.uri)
                  hashBytes
              } else null
              val extractedDir = File(context.filesDir, "extracted_${safeVaultBase}_${System.currentTimeMillis()}").apply { mkdirs() }
              val extractedEntries = CryptoEngine.decryptV3VaultToDirectory(
                  context = context,
                  vaultUri = vaultUri,
                  outputDir = extractedDir,
                  password = password.toCharArray(),
                  keyfileHash = keyfileHash
              ) { _, _ -> }

              _decryptionResult.value = currentResult.copy(files = extractedEntries)
              val matched = extractedEntries.firstOrNull { it.path == entry.path || it.name == entry.name }
              if (matched != null) {
                  withContext(Dispatchers.Main) {
                      onReady(matched)
                  }
              }
          } catch (e: Exception) {
              addLog("❌ Failed to extract \"${entry.name}\": ${e.message}", LogType.ERROR)
          }
      }
  }
  ```
- **File**: `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoModels.kt`
- **Lines 120–133**:
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
  - When a vault is opened via "Instant Explorer" (`loadInstantCatalog`, line 585), `readV3VaultManifest` populates `_decryptionResult` where every entry has `localFileUri = null`.
  - When the user taps a file in `VaultBrowserScreen` (line 205), `extractSingleFileIfNeeded` decrypts the **entire vault from scratch** into a new directory `extracted_${safeVaultBase}_${timestamp}`.
  - For a 100 MB or 1 GB vault, tapping a 20 KB image causes multi-second delay, full archive decompression, and excessive disk write churn.

---

### 1.3 Existing Unit Tests
- **File**: `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`
  - Tests v1/v2 key derivation, v3 magic header detection, chunk IV computation, chunk AAD computation, chunk encrypt/decrypt round trip, chunk tampering detection, header serialization, manifest envelope encryption, and password evaluator.
  - **No test** exists for `encryptItemsToV3Vault`, 0-byte reported items, manifest creation from streams, or `extractSingleFileFromV3Vault`.
- **File**: `app/build.gradle.kts`
  - `testImplementation(libs.junit)` (JUnit 4). Standard JVM unit testing.

---

## 2. Logic Chain

### 2.1 R1 Logic Chain (Manifest Size Bug)
1. **Premise**: In Android SAF (`DocumentFile`, `ContentResolver.query(OpenableColumns.SIZE)`), size queries can return `null` or `0L` for virtual files, camera intents, or streamed inputs.
2. **Observation**: `CryptoEngine.encryptItemsToV3Vault` receives `SelectedItem(sizeBytes = 0L)`.
3. **Observation**: For `isPrecompressed = true`, lines 531–537 perform a stream pre-pass and accurately calculate `actualSize` and `crc.value`.
4. **Observation**: `actualSize` is scoped inside `if (isPrecompressed)` and not assigned to `entryJson` (line 562 uses `item.sizeBytes`).
5. **Deduction**: The manifest JSON contains `"size": 0` and `"compressedSize": 0`.
6. **Observation**: In `readV3VaultManifest`, `chunkEnd` is computed as `((lho + 30 + path.length + compSize) / DEFAULT_CHUNK_SIZE)`. If `compSize == 0`, `chunkEnd` fails to encompass the payload chunk range.
7. **Observation**: In `StreamUnpacker.js` and standard length validators, extracting 0 bytes when the stream expected actual payload causes CRC-32 checksum failures and length mismatch errors.
8. **Conclusion**: To resolve R1, `actualSize` and `actualCompressedSize` must be captured in the outer item loop for all items (STORED and DEFLATED), and written directly into `entryJson` (`"size"` and `"compressedSize"`), with `totalSize` reflecting the sum of measured sizes.

### 2.2 R2 Logic Chain (Targeted Single-File Extraction)
1. **Premise**: Re-decrypting an entire vault to preview a single file is $O(N)$ in vault size and disk write operations, violating responsiveness expectations (< 10 s for 50 MB vault, per acceptance criteria).
2. **Observation**: Each `DecryptedFileEntry` from `readV3VaultManifest` already contains `localHeaderOffset`, `compressedSize`, `isCompressed`, `crc32`, `chunkStart`, and `chunkEnd`.
3. **Observation**: V3 container chunks are encrypted individually with AES-256-GCM using deterministic chunk indices:
   $$\text{Vault Chunk Start} = 57 + i \times (4{,}194{,}304 + 20)$$
4. **Deduction**: Any single file residing in chunks `chunkStart..chunkEnd` can be extracted by reading and decrypting **only** those chunks ($O(1)$ chunks for files $< 4\text{ MB}$, typically $\le 2$ chunks).
5. **Deduction**: Using Android's `FileChannel.position(chunkVaultStart)`, chunk bytes can be read with zero seek overhead without touching preceding or succeeding chunks.
6. **Deduction**: After decrypting chunks `chunkStart..chunkEnd`, parsing the ZIP Local File Header at `relPos = entry.localHeaderOffset - (chunkStart \times 4{,}194{,}304)` yields the exact payload offset. If `isCompressed`, `java.util.zip.Inflater(true)` decompresses the raw DEFLATE bytes into memory; if STORED, bytes are copied directly.
7. **Observation**: RAM utilization for 1–3 chunks is $4\text{ MB} \times 3 = 12\text{ MB}$, well within the $< 150\text{ MB}$ memory bound.
8. **Conclusion**: Adding `CryptoEngine.extractSingleFileFromV3Vault` and invoking it inside `VaultViewModel.extractSingleFileIfNeeded` eliminates the full vault decrypt for preview, reducing preview latency from seconds/minutes to $< 100\text{ ms}$. A session extraction fallback ensures legacy v1/v2 vaults decrypt at most once per session.

---

## 3. Caveats
- **V1/V2 Standard Vaults**: Standard v1 and v2 vaults do not use chunked AEAD (`ZV3\0`); they are monolithic AES-GCM archives. Targeted chunk extraction cannot be applied to v1/v2 vaults. For v1/v2, session caching must be used (decrypt once to session directory, reuse for all subsequent previews).
- **Files Crossing Chunk Boundaries**: ZIP local headers or file payloads may cross a 4 MB boundary. By decrypting the contiguous chunk slice `chunkStart..chunkEnd`, the entire span is present in memory. For previewing typical media/docs ($< 20\text{ MB}$), buffering the span is well within the 150 MB RAM budget.
- **Content Providers with Non-Seekable Descriptors**: In rare cases where a content provider does not support `openFileDescriptor` or `FileChannel.position`, the implementation should fall back to sequential stream skipping (`InputStream.skip()`) or the session extraction fallback.

---

## 4. Conclusion & Actionable Code Blueprint

### 4.1 R1 Code Blueprint (`CryptoEngine.kt`)

In `CryptoEngine.encryptItemsToV3Vault`:
Replace lines 526–569 with the following pattern:

```kotlin
// Inside for (item in items) loop in encryptItemsToV3Vault:
val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
val isPrecompressed = PRE_COMPRESSED_EXTENSIONS.contains(ext)

val entryOffset = currentZipOffset
val zipEntry = ZipEntry(item.relativePath)
val crc = CRC32()
var measuredUncompressedSize = 0L
var measuredCompressedSize = 0L

if (isPrecompressed) {
    zipEntry.method = ZipEntry.STORED

    // Measure exact length and CRC32 for STORED entry
    context.contentResolver.openInputStream(item.uri)?.use { inStream ->
        var read: Int
        while (inStream.read(ioBuffer).also { read = it } != -1) {
            crc.update(ioBuffer, 0, read)
            measuredUncompressedSize += read
        }
    } ?: throw java.io.IOException("Cannot open input stream for ${item.name}")

    zipEntry.size = measuredUncompressedSize
    zipEntry.compressedSize = measuredUncompressedSize
    zipEntry.crc = crc.value
    measuredCompressedSize = measuredUncompressedSize
}

zipOut.putNextEntry(zipEntry)

var writtenUncompressedBytes = 0L
context.contentResolver.openInputStream(item.uri)?.use { inStream ->
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
```

Also, update `catalogRoot` at line 581:
Track `totalMeasuredBytes += finalSize` across items, and write:
```kotlin
put("totalSize", if (totalMeasuredBytes > 0L) totalMeasuredBytes else totalSourceBytes)
```

---

### 4.2 R2 Code Blueprint: New `extractSingleFileFromV3Vault` in `CryptoEngine.kt`

Add the following function to `CryptoEngine.kt`:

```kotlin
suspend fun extractSingleFileFromV3Vault(
    context: Context,
    vaultUri: Uri,
    entry: DecryptedFileEntry,
    password: CharArray,
    keyfileHash: ByteArray?,
    outputFile: File
): DecryptedFileEntry = withContext(Dispatchers.IO) {
    val header = context.contentResolver.openInputStream(vaultUri)?.use { parseVaultHeader(it) }
        ?: throw java.io.IOException("Cannot read vault header")
    if (header.version != VaultVersion.V3) {
        throw IllegalArgumentException("Targeted extraction requires a v3 vault")
    }
    if (header.requiresKeyfile && keyfileHash == null) {
        throw IllegalArgumentException("Keyfile required for this vault")
    }

    val secretKey = deriveKeyV3(password, header.salt, keyfileHash)
    val baseIV = header.baseIVPrefix ?: throw IllegalArgumentException("Missing base IV prefix")
    val manifestOffset = header.manifestOffset
    val chunkSize = header.chunkSize
    val totalFramedChunkSize = chunkSize + CHUNK_HEADER_SIZE + TAG_LENGTH

    val lastChunkIndex = ((manifestOffset - V3_HEADER_SIZE) / totalFramedChunkSize).toInt()
    val chunkStart = entry.chunkStart.coerceAtLeast(0)
    val chunkEnd = entry.chunkEnd.coerceIn(chunkStart, lastChunkIndex)

    val decryptedChunks = mutableListOf<ByteArray>()

    // Read chunks via FileChannel for direct zero-cost random access seek
    val pfd = context.contentResolver.openFileDescriptor(vaultUri, "r")
    if (pfd != null) {
        pfd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                for (i in chunkStart..chunkEnd) {
                    val chunkVaultStart = V3_HEADER_SIZE + i.toLong() * totalFramedChunkSize
                    val isLast = (i == lastChunkIndex)
                    val chunkFramedSize = if (isLast) (manifestOffset - chunkVaultStart).toInt() else totalFramedChunkSize

                    channel.position(chunkVaultStart)
                    val chunkBytes = ByteArray(chunkFramedSize)
                    val buf = ByteBuffer.wrap(chunkBytes)
                    var readTotal = 0
                    while (readTotal < chunkFramedSize) {
                        val r = channel.read(buf)
                        if (r == -1) break
                        readTotal += r
                    }
                    if (readTotal != chunkFramedSize) {
                        throw java.io.IOException("Incomplete chunk read at chunk $i")
                    }

                    val plainChunk = decryptChunk(secretKey, chunkBytes, baseIV, i, isLast, header.salt)
                    decryptedChunks.add(plainChunk)
                }
            }
        }
    } else {
        // Fallback for providers that don't support FileChannel
        context.contentResolver.openInputStream(vaultUri)?.use { inStream ->
            var currentPos = 0L
            for (i in chunkStart..chunkEnd) {
                val chunkVaultStart = V3_HEADER_SIZE + i.toLong() * totalFramedChunkSize
                val isLast = (i == lastChunkIndex)
                val chunkFramedSize = if (isLast) (manifestOffset - chunkVaultStart).toInt() else totalFramedChunkSize

                while (currentPos < chunkVaultStart) {
                    val skipped = inStream.skip(chunkVaultStart - currentPos)
                    if (skipped <= 0) break
                    currentPos += skipped
                }

                val chunkBytes = ByteArray(chunkFramedSize)
                var readTotal = 0
                while (readTotal < chunkFramedSize) {
                    val r = inStream.read(chunkBytes, readTotal, chunkFramedSize - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                currentPos += readTotal
                val plainChunk = decryptChunk(secretKey, chunkBytes, baseIV, i, isLast, header.salt)
                decryptedChunks.add(plainChunk)
            }
        } ?: throw java.io.IOException("Cannot open vault input stream")
    }

    // Merge chunk span
    val spanBytes: ByteArray = if (decryptedChunks.size == 1) {
        decryptedChunks[0]
    } else {
        val totalLen = decryptedChunks.sumOf { it.size }
        val merged = ByteArray(totalLen)
        var offset = 0
        for (c in decryptedChunks) {
            System.arraycopy(c, 0, merged, offset, c.size)
            offset += c.size
        }
        merged
    }

    // Locate payload in decrypted span
    val spanBaseOffset = chunkStart.toLong() * chunkSize
    val relPos = (entry.localHeaderOffset - spanBaseOffset).toInt()
    if (relPos < 0 || relPos >= spanBytes.size) {
        throw IllegalStateException("Invalid local header position in chunk span")
    }

    val view = ByteBuffer.wrap(spanBytes).order(ByteOrder.LITTLE_ENDIAN)
    var payloadStart = relPos
    var payloadLength = entry.compressedSize.toInt()

    if (relPos + 30 <= spanBytes.size) {
        val sig = view.getInt(relPos)
        if (sig == 0x04034b50) { // PK\x03\x04
            val nameLen = view.getShort(relPos + 26).toInt() and 0xFFFF
            val extraLen = view.getShort(relPos + 28).toInt() and 0xFFFF
            payloadStart = relPos + 30 + nameLen + extraLen
            val headerCompSize = view.getInt(relPos + 18).toLong() and 0xFFFFFFFFL
            if (payloadLength <= 0 && headerCompSize > 0) {
                payloadLength = headerCompSize.toInt()
            }
        }
    }

    val payloadEnd = if (payloadLength > 0) {
        Math.min(spanBytes.size, payloadStart + payloadLength)
    } else {
        spanBytes.size
    }
    val rawPayload = spanBytes.copyOfRange(payloadStart, payloadEnd)

    // Decompress if DEFLATED
    val uncompressedBytes = if (entry.isCompressed && rawPayload.isNotEmpty()) {
        val inflater = java.util.zip.Inflater(true)
        inflater.setInput(rawPayload)
        val bos = ByteArrayOutputStream(entry.sizeBytes.toInt().coerceAtLeast(1024))
        val tempBuf = ByteArray(BUFFER_SIZE)
        while (!inflater.finished()) {
            val count = inflater.inflate(tempBuf)
            if (count == 0 && inflater.needsInput()) break
            bos.write(tempBuf, 0, count)
        }
        inflater.end()
        bos.toByteArray()
    } else {
        rawPayload
    }

    outputFile.parentFile?.mkdirs()
    outputFile.writeBytes(uncompressedBytes)

    val ext = outputFile.extension.lowercase(Locale.ROOT)
    val mimeType = resolveMimeType(ext)
    var textPreview: String? = null
    if (mimeType.startsWith("text/") && outputFile.length() < 256 * 1024) {
        textPreview = outputFile.readText(Charsets.UTF_8).take(2000)
    }

    return@withContext entry.copy(
        sizeBytes = outputFile.length(),
        localFileUri = Uri.fromFile(outputFile),
        textContentPreview = textPreview
    )
}
```

---

### 4.3 R2 Code Blueprint: Updated `extractSingleFileIfNeeded` in `VaultViewModel.kt`

```kotlin
private var sessionExtractionDir: File? = null

fun extractSingleFileIfNeeded(
    context: Context,
    entry: DecryptedFileEntry,
    onReady: (DecryptedFileEntry) -> Unit
) {
    if (entry.localFileUri != null && File(entry.localFileUri.path ?: "").exists()) {
        onReady(entry)
        return
    }
    val currentResult = _decryptionResult.value ?: return
    val vaultUri = _selectedDecryptVaultUri.value ?: return
    val password = _decryptPassword.value
    val keyfile = _decryptKeyfile.value
    val safeVaultBase = sanitizeFilename(currentResult.vaultName.substringBeforeLast('.'))

    viewModelScope.launch(Dispatchers.IO) {
        try {
            addLog("Extracting \"${entry.name}\" for preview...", LogType.INFO)
            val keyfileHash = if (keyfile != null) {
                val (hashBytes, _) = CryptoEngine.hashKeyfile(context, keyfile.uri)
                hashBytes
            } else null

            val previewCacheDir = File(context.filesDir, "preview_cache_${safeVaultBase}").apply { mkdirs() }
            val targetFile = File(previewCacheDir, entry.name)

            val extractedEntry = if (currentResult.version == VaultVersion.V3) {
                try {
                    CryptoEngine.extractSingleFileFromV3Vault(
                        context = context,
                        vaultUri = vaultUri,
                        entry = entry,
                        password = password.toCharArray(),
                        keyfileHash = keyfileHash,
                        outputFile = targetFile
                    )
                } catch (targetedErr: Exception) {
                    addLog("⚠️ Targeted extraction fallback: ${targetedErr.message}", LogType.WARN)
                    fallbackExtractAll(context, vaultUri, safeVaultBase, password, keyfileHash, entry)
                }
            } else {
                fallbackExtractAll(context, vaultUri, safeVaultBase, password, keyfileHash, entry)
            }

            if (extractedEntry != null) {
                val updatedFiles = currentResult.files.map {
                    if (it.path == extractedEntry.path || it.name == extractedEntry.name) extractedEntry else it
                }
                _decryptionResult.value = currentResult.copy(files = updatedFiles)
                withContext(Dispatchers.Main) {
                    onReady(extractedEntry)
                }
            }
        } catch (e: Exception) {
            addLog("❌ Failed to extract \"${entry.name}\": ${e.message}", LogType.ERROR)
        }
    }
}

private suspend fun fallbackExtractAll(
    context: Context,
    vaultUri: Uri,
    safeVaultBase: String,
    password: String,
    keyfileHash: ByteArray?,
    targetEntry: DecryptedFileEntry
): DecryptedFileEntry? {
    val dir = sessionExtractionDir ?: File(context.filesDir, "extracted_${safeVaultBase}_session").apply { mkdirs() }.also {
        sessionExtractionDir = it
    }
    val existing = File(dir, targetEntry.name)
    if (existing.exists() && existing.length() > 0) {
        return targetEntry.copy(localFileUri = Uri.fromFile(existing))
    }
    val allEntries = CryptoEngine.decryptV3VaultToDirectory(
        context = context,
        vaultUri = vaultUri,
        outputDir = dir,
        password = password.toCharArray(),
        keyfileHash = keyfileHash
    ) { _, _ -> }
    _decryptionResult.update { it?.copy(files = allEntries) }
    return allEntries.firstOrNull { it.path == targetEntry.path || it.name == targetEntry.name }
}
```

---

## 5. Verification Method

### 5.1 Unit Tests for Independent Verification
Add the following tests to `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`:

1. **`testZeroReportedSizeManifestRecording`**:
   - Create a test file of 128 KB.
   - Construct a `SelectedItem(uri = testUri, name = "sample.jpg", relativePath = "sample.jpg", sizeBytes = 0L)`.
   - Encrypt using `encryptItemsToV3Vault`.
   - Read manifest using `readV3VaultManifest`.
   - Assert `catalog.files[0].sizeBytes == 128 * 1024L` (must NOT be 0L).
   - Assert `catalog.files[0].compressedSize == 128 * 1024L`.
   - Assert `catalog.totalSize == 128 * 1024L`.
   - Assert `catalog.files[0].crc32 != 0L`.

2. **`testTargetedSingleFileExtraction`**:
   - Create a vault with 3 files (e.g. 50 KB text file, 2 MB binary file, 100 KB image file).
   - Read manifest without extracting files (`localFileUri == null`).
   - Call `CryptoEngine.extractSingleFileFromV3Vault` targeting the second file.
   - Assert extracted file is byte-for-byte identical to the original second file.
   - Assert files 1 and 3 were never written to disk during this operation.

3. **`testV630VaultBackwardCompatibility`**:
   - Decrypt an existing v6.3.0 vault container: must succeed with 100% data integrity.

### 5.2 Build & Test Command
```bash
./gradlew test
```
Expected result: `BUILD SUCCESSFUL`, 100% tests passing.

### 5.3 Invalidation Conditions
- Any change that records `0L` in `catalogEntries` when `item.sizeBytes == 0L` invalidates R1.
- Any implementation of `extractSingleFileIfNeeded` that unconditionally invokes `decryptV3VaultToDirectory` or takes $> 2\text{ seconds}$ to preview a file in a 50 MB vault invalidates R2.
- Any allocation of $> 150\text{ MB}$ RAM during chunk extraction invalidates the memory bounding constraint.
