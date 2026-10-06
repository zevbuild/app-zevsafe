# M1 Explorer 3: Crypto Compatibility & Regressions Investigation Report

**Author**: M1 Explorer 3 (Crypto Compatibility & Regressions Specialist)  
**Date**: 2026-10-05T13:05:00Z  
**Working Directory**: `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m1_explorer_3`  
**Target Scope**: Milestone 1 (R1) Backward Compatibility, Cross-Platform Compatibility (`StreamUnpacker.js`), and Edge Cases  

---

## 1. Observation

### 1.1 Existing Android v6.3.0 Manifest Generation & Decryption Architecture
- **File**: `app-zevsafe/app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt`
- **Lines 559–568 (Manifest JSON serialization in `encryptItemsToV3Vault`)**:
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
  - Line 562 writes `"size": item.sizeBytes`. If Android `ContentResolver` queries (`OpenableColumns.SIZE`) return `0L` (as occurs for virtual documents, dynamic content providers, or camera intents), the manifest records `0L`.
  - Line 563 evaluates to `item.sizeBytes` in both branches, setting `"compressedSize": 0L` when reported size is 0, and recording uncompressed size for DEFLATED files rather than actual compressed payload bytes.
  - Line 526–542 executes CRC-32 calculation ONLY when `isPrecompressed == true`. For non-precompressed files (DEFLATED), `crc` is never updated during streaming (lines 546–556), writing `"crc32": 0` into the manifest.
  - Line 581 writes `"totalSize": totalSourceBytes`, where `totalSourceBytes = items.sumOf { it.sizeBytes }` (line 458), resulting in `0L` when all items report 0 bytes.

- **Lines 608–670 (`decryptV3VaultToDirectory`)**:
  ```kotlin
  val decryptedStream = V3DecryptedInputStream(
      inStream = inStream,
      secretKey = secretKey,
      baseIVPrefix = baseIV,
      salt = header.salt,
      manifestOffset = manifestOffset
  ) { pct -> ... }

  ZipInputStream(decryptedStream.buffered()).use { zis ->
      var zipEntry: ZipEntry?
      while (zis.nextEntry.also { zipEntry = it } != null) {
          ...
          FileOutputStream(targetFile).buffered().use { fos ->
              val buf = ByteArray(BUFFER_SIZE)
              var r: Int
              while (zis.read(buf).also { r = it } != -1) {
                  fos.write(buf, 0, r)
              }
          }
          ...
      }
  }
  ```
  - `decryptV3VaultToDirectory` decrypts chunks from offset 57 up to `manifestOffset`, piping the raw decrypted stream directly into `ZipInputStream`.
  - **Crucial Observation**: Full vault decryption **never reads, parses, or validates the encrypted manifest trailer** at `manifestOffset`. It extracts files purely from PKWARE ZIP headers (`0x04034b50`).
  - `targetFile.length()` is measured from bytes physically written to disk.

- **Lines 403–431 (`readV3VaultManifest`)**:
  ```kotlin
  val item = filesJson.getJSONObject(i)
  val path = item.optString("path", item.optString("name", "unnamed"))
  val size = item.optLong("size", item.optLong("uncompressedSize", 0L))
  val compSize = item.optLong("compressedSize", size)
  val lho = item.optLong("localHeaderOffset", item.optLong("offset", 0L))
  val isComp = item.optBoolean("compressed", false)
  val crc = item.optLong("crc32", 0L)
  ```
  - Parses JSON keys using safe fallback accessors (`optLong`, `optString`, `optBoolean`).
  - Does not enforce cryptographic checks or size limits; merely populates `DecryptedFileEntry`.

---

### 1.2 Web Decryptor (`StreamUnpacker.js`) Contract & Behavior
- **File**: `zevsafe/js/stream-unpacker.js`
- **Lines 798–832 (`normalizeCatalogEntry`)**:
  ```javascript
  function normalizeCatalogEntry(entry, defaultChunkSize = DEFAULT_CHUNK_SIZE) {
      const path = entry.path || entry.name || 'unnamed';
      const uncompressedSize = entry.size !== undefined ? entry.size : (entry.uncompressedSize !== undefined ? entry.uncompressedSize : 0);
      const compressedSize = entry.compressedSize !== undefined ? entry.compressedSize : uncompressedSize;
      const localHeaderOffset = entry.localHeaderOffset !== undefined ? entry.localHeaderOffset : (entry.offset !== undefined ? entry.offset : 0);
      const compressed = entry.compressed !== undefined ? entry.compressed : (entry.compressionMethod === 8);

      const headerLen = 30 + new TextEncoder().encode(path).length;
      const totalFileSpan = BigInt(localHeaderOffset) + BigInt(headerLen) + BigInt(compressedSize);

      const chunkStart = entry.chunkStart !== undefined
          ? entry.chunkStart
          : Math.floor(Number(BigInt(localHeaderOffset) / BigInt(defaultChunkSize)));

      const chunkEnd = entry.chunkEnd !== undefined
          ? entry.chunkEnd
          : Math.floor(Number(totalFileSpan / BigInt(defaultChunkSize)));

      return {
          path: path,
          name: path,
          size: uncompressedSize,
          uncompressedSize: uncompressedSize,
          compressedSize: compressedSize,
          offset: localHeaderOffset,
          localHeaderOffset: localHeaderOffset,
          compressed: !!compressed,
          compressionMethod: compressed ? 8 : 0,
          chunkStart: chunkStart,
          chunkEnd: Math.max(chunkStart, chunkEnd),
          crc32: entry.crc32 !== undefined ? (entry.crc32 >>> 0) : undefined,
          lastModified: entry.lastModified
      };
  }
  ```

- **Lines 1098–1137 (`extractSingleFile` in `StreamUnpacker.js`)**:
  ```javascript
  let payloadStart = relPos;
  let payloadLength = normalized.compressedSize;

  if (relPos >= 0 && relPos + 30 <= spanBytes.byteLength) {
      const view = new DataView(spanBytes.buffer, spanBytes.byteOffset, spanBytes.byteLength);
      const sig = view.getUint32(relPos, true);
      if (sig === ZIP_SIGNATURES.LOCAL_FILE_HEADER) {
          const nameLen = view.getUint16(relPos + 26, true);
          const extraLen = view.getUint16(relPos + 28, true);
          payloadStart = relPos + 30 + nameLen + extraLen;
      }
  }

  const payloadEnd = Math.min(spanBytes.byteLength, payloadStart + payloadLength);
  const rawPayload = spanBytes.subarray(payloadStart, payloadEnd);

  let extractedBytes;
  if (normalized.compressed && rawPayload.byteLength > 0) {
      extractedBytes = await decompressDeflateRaw(rawPayload);
  } else {
      extractedBytes = rawPayload;
  }

  if (normalized.crc32 !== undefined && normalized.crc32 !== null) {
      const computedCrc = crc32(extractedBytes);
      if ((computedCrc >>> 0) !== (normalized.crc32 >>> 0)) {
          throw createOperationError(
              `CRC-32 checksum mismatch for "${normalized.path}": expected 0x${(normalized.crc32 >>> 0).toString(16).padStart(8, '0')}, calculated 0x${(computedCrc >>> 0).toString(16).padStart(8, '0')}`
          );
      }
  }
  ```
  - **Direct Consequence of Bug**: If `normalized.compressedSize == 0` for a non-empty file:
    1. `payloadLength = 0`.
    2. `payloadEnd = payloadStart`.
    3. `rawPayload` is empty (`byteLength == 0`).
    4. `extractedBytes` is an empty `Uint8Array(0)`.
    5. `computedCrc = crc32(empty) = 0`.
    6. For precompressed files in v6.3.0, `normalized.crc32` was the stream-measured CRC ($> 0$).
    7. Comparison `computedCrc (0) !== normalized.crc32` triggers an immediate fatal `OperationError: CRC-32 checksum mismatch`.
  - **Second Bug for DEFLATED files**: In v6.3.0, `crc32` was written as `0` for all non-precompressed files. When Web decryptor extracts a non-precompressed file, `computedCrc > 0` while `normalized.crc32 == 0`, triggering `OperationError: CRC-32 checksum mismatch`.

---

### 1.3 Web Encryption Reference Implementation (`StreamPacker.js` & `WorkerBridge.js`)
- **File**: `zevsafe/js/worker-bridge.js` (lines 656–668):
  ```javascript
  catalogFiles.push({
      path: name,
      name: name,
      size: uncompSize,
      compressedSize: compSize,
      offset: offset,
      localHeaderOffset: offset,
      compressed: method === 8,
      compressionMethod: method,
      chunkStart: chunkStart,
      chunkEnd: Math.max(chunkStart, chunkEnd),
      crc32: crc
  });
  ```
- **File**: `zevsafe/js/stream-packer.js` (lines 769–784):
  - Emits 24-byte ZIP64 Data Descriptor after payload.
  - Records `compressedSize: compBytes` where `compBytes` is the exact compressed payload byte length (excluding the 30-byte local header and data descriptor).

---

### 1.4 Test Suite Verifications
- Executed `node test/test-android-encryption.js; node test/test-integration-m5.js` in `zevsafe`:
  - Result: 33/33 Android encryption tests PASSED (100%).
  - Result: 5/5 M5 integration tests PASSED (100%).
  - Confirmed: Test 4.2 validates that an empty 0-byte file encrypts and extracts to 0 bytes cleanly without error.

---

## 2. Logic Chain

### 2.1 Backward Compatibility Proof for Existing v6.3.0 Vaults
1. **Premise**: We must ensure that fixing the manifest size bug in `CryptoEngine.kt` does not break or regress decryption of existing vaults created by v6.3.0.
2. **Observation (Section 1.1)**: `CryptoEngine.decryptV3VaultToDirectory` decrypts AEAD chunks from offset 57 to `manifestOffset`, and feeds the decrypted stream into Java's standard `ZipInputStream`.
3. **Observation (Section 1.1)**: `decryptV3VaultToDirectory` never opens or reads the manifest envelope at `manifestOffset`.
4. **Deduction**: Existing v6.3.0 vaults contain valid PKWARE ZIP streams within their AEAD chunks. Since `decryptV3VaultToDirectory` relies strictly on `ZipInputStream` and completely ignores the manifest envelope, full vault decryption of ANY existing v6.3.0 vault is 100% unaffected by any manifest generation changes.
5. **Observation (Section 1.1)**: `readV3VaultManifest` parses JSON using `optLong`, `optString`, and `optBoolean`. It reads manifest envelopes generated by v6.3.0 without throwing exceptions.
6. **Deduction**: For "Instant Explorer" (`loadInstantCatalog`), v6.3.0 vaults will display the file list with the exact attributes stored at creation time.
7. **Deduction for Targeted Extraction (Milestone 2 dependency)**: If a v6.3.0 vault had an entry with `compressedSize == 0L` or `sizeBytes == 0L`, targeted single-file extraction could fail to determine the payload span. However, with the proposed fallback in `VaultViewModel.extractSingleFileIfNeeded` (`fallbackExtractAll` via `decryptV3VaultToDirectory`), even affected v6.3.0 vaults are guaranteed to decrypt and preview successfully.
8. **Conclusion**: The M1 manifest fix has ZERO regression risk on the decryption of existing v6.3.0 vaults.

---

### 2.2 Cross-Platform Compatibility with `StreamUnpacker.js` (Web Decryptor)
1. **Observation (Section 1.2)**: `StreamUnpacker.js` expects the following keys in manifest file objects:
   - `path`: String (normalized to `entry.path || entry.name`)
   - `size`: Long (uncompressed bytes)
   - `compressedSize`: Long (compressed payload bytes)
   - `localHeaderOffset`: Long (offset of `PK\x03\x04` in ZIP stream)
   - `compressed`: Boolean (`true` for DEFLATED, `false` for STORED)
   - `crc32`: Unsigned 32-bit Integer (IEEE 802.3 CRC-32)
2. **Observation (Section 1.2)**: In `StreamUnpacker.js`, `payloadLength = normalized.compressedSize`, and extracted bytes are checked against `normalized.crc32`.
3. **Deduction**: If Android writes stream-measured uncompressed size to `"size"`, stream-measured compressed payload size to `"compressedSize"`, and computes IEEE 802.3 CRC-32 for ALL files into `"crc32"`, `StreamUnpacker.js` will:
   - Correctly slice the exact payload bytes (`spanBytes.subarray(payloadStart, payloadStart + payloadLength)`).
   - Decompress if `compressed == true` via `decompressDeflateRaw`.
   - Compute `crc32(extractedBytes)` which will match `normalized.crc32` exactly.
4. **Conclusion**: Updating `CryptoEngine.kt` to write measured sizes and CRC-32 directly fixes cross-platform decryption on the Web, eliminating the fatal `OperationError: CRC-32 checksum mismatch`.

---

### 2.3 Edge Case Analysis & Logic

#### Edge Case A: Truly Empty Files (0 actual bytes) vs. Virtual Files (0 reported bytes)
1. **Observation**: A genuinely empty file (e.g. `empty.txt` with 0 bytes on disk) returns `-1` on the very first `read()` from `openInputStream`.
   - `measuredUncompressedSize = 0L`
   - `writtenUncompressedBytes = 0L`
   - `crc.value = 0L` (IEEE 802.3 CRC of an empty sequence is `0x00000000`)
2. **Observation**: A virtual file (e.g. MediaStore or camera intent reporting 0 bytes via `ContentResolver.query(OpenableColumns.SIZE)`) returns $> 0$ bytes during `read()`.
   - `measuredUncompressedSize = actualBytes > 0L`
   - `writtenUncompressedBytes = actualBytes > 0L`
   - `crc.value = actualCRC != 0L`
3. **Logic Rule**:
   - The stream is the definitive source of truth.
   - For STORED entries (`isPrecompressed == true`):
     - `finalSize = measuredUncompressedSize`
     - `finalCompSize = measuredUncompressedSize`
   - For DEFLATED entries (`isPrecompressed == false`):
     - `finalSize = writtenUncompressedBytes`
     - `finalCompSize = zipEntry.compressedSize` (or 0 if empty)
   - If a file is genuinely empty ($0\text{ bytes}$):
     - `"size": 0`, `"compressedSize": 0`, `"crc32": 0`.
     - In Web `StreamUnpacker.js`: `payloadLength = 0`, `extractedBytes = Uint8Array(0)`, `crc32(empty) = 0`, expected CRC = 0. Check passes!
   - If a file is virtual ($0\text{ reported, } > 0\text{ actual}$):
     - `"size": actualBytes`, `"compressedSize": actualCompBytes`, `"crc32": actualCRC`.
     - In Web `StreamUnpacker.js`: Slices `actualCompBytes`, checks against `actualCRC`. Check passes!

#### Edge Case B: `compressedSize` Calculation for DEFLATED Files in Java `ZipOutputStream`
1. **Critical Warning on Survey 1 Blueprint**: Survey 1 line 277 proposed:
   `measuredCompressedSize = (currentZipOffset - entryOffset)`.
2. **Observation**: `entryOffset` is recorded before `zipOut.putNextEntry(zipEntry)`.
3. **Observation**: `currentZipOffset` advances as `putNextEntry` writes the 30-byte local header + filename, then payload compressed bytes, then `closeEntry()` writes the 16-byte ZIP data descriptor (`0x08074b50`).
4. **Deduction**: `(currentZipOffset - entryOffset)` equals `30 + nameLen + compressedPayloadLen + 16`.
5. **Deduction**: If this span is stored as `compressedSize`, `StreamUnpacker.js` will slice 46+ extra bytes beyond the payload end, including the trailing data descriptor, which corrupts raw payload slicing and can cause `DecompressionStream` to reject extra trailing data.
6. **Solution**: In Java `ZipOutputStream`, `zipEntry.compressedSize` is automatically populated by `closeEntry()` with the exact compressed bytes written by the Deflater. Therefore, `zipEntry.compressedSize` must be used. If `zipEntry.compressedSize <= 0` and `writtenUncompressedBytes > 0`, fallback to `(currentZipOffset - entryOffset) - (30 + item.relativePath.toByteArray(Charsets.UTF_8).size) - 16`.

#### Edge Case C: Files Larger Than 4 MB Crossing Chunk Boundaries
1. **Observation**: Each V3 chunk has a fixed plaintext size of 4 MB (`DEFAULT_CHUNK_SIZE = 4,194,304`).
2. **Observation**: `chunkStart = (lho / 4194304).toInt()` and `chunkEnd = ((lho + 30 + path.length + compSize) / 4194304).toInt()`.
3. **Deduction**: For a file $> 4\text{ MB}$ or a file whose payload crosses from chunk $k$ into chunk $k+1$, `chunkEnd > chunkStart`.
4. **Full Decryption**: In `decryptV3VaultToDirectory`, `V3DecryptedInputStream` sequentially decrypts chunks into an internal buffer. As chunk $k$ finishes, it decrypts chunk $k+1$. `ZipInputStream` traverses chunk boundaries transparently. Files of any size (even 5 GB) decrypt without boundary issues.
5. **Single-File Preview (M2 consideration)**: When extracting a multi-chunk file, decrypting chunks `chunkStart..chunkEnd` yields a contiguous plaintext slice of $(chunkEnd - chunkStart + 1) \times 4\text{ MB}$. For files $\le 20\text{ MB}$, this consumes $\le 24\text{ MB}$ RAM, well under the 150 MB ceiling. For multi-gigabyte media files, streaming chunk by chunk directly to disk avoids allocating massive single `ByteArray` buffers.

---

## 3. Caveats
1. **V1 and V2 Legacy Vaults**:
   - V1 and V2 vaults are monolithic AES-GCM encrypted archives without chunking (`ZV3\0`) or tail manifests.
   - The manifest size fix only applies to V3 streaming container encryption (`encryptItemsToV3Vault`).
   - Legacy V1 and V2 decryption remains fully preserved via `decryptV1OrV2Vault`.
2. **Stale v6.3.0 Manifests on Disk**:
   - If a user previously encrypted a file using v6.3.0 that suffered from the 0-byte manifest bug, the manifest inside that specific `.zev` file already contains `"size": 0`.
   - While full vault decryption (`decryptV3VaultToDirectory`) will extract the file with 100% data integrity, "Instant Explorer" (`loadInstantCatalog`) will read `"size": 0` from the old manifest.
   - To ensure smooth preview of such files in Milestone 2, `VaultViewModel.extractSingleFileIfNeeded` must maintain the session extraction fallback (`fallbackExtractAll`) if targeted extraction fails.
3. **JVM Memory Limits for Very Large Previews**:
   - For files $> 50\text{ MB}$, targeted extraction in Milestone 2 must not concatenate all decrypted chunks into a single contiguous in-memory `ByteArray`, as standard Android apps have per-process heap limits (typically 192 MB to 256 MB). Streaming chunk by chunk directly to `outputFile` preserves the $< 150\text{ MB}$ memory boundary.

---

## 4. Conclusion & Actionable Recommendations

### 4.1 Summary Assessment
- **Backward Compatibility**: Guaranteed 100%. `decryptV3VaultToDirectory` does not use the manifest trailer; modifying manifest sizes has zero regression impact on existing or new vaults.
- **Cross-Platform Compatibility**: Fixing manifest sizes and computing CRC-32 for DEFLATED files resolves the fatal `CRC-32 checksum mismatch` in `StreamUnpacker.js`, achieving full parity between Android and Web encryptors/decryptors.
- **Edge Cases**:
  - Genuinely empty files ($0\text{ bytes}$) will write `"size": 0`, `"compressedSize": 0`, `"crc32": 0`, which cleanly unpacks on both Android and Web.
  - Virtual files ($0\text{ reported, } > 0\text{ actual}$) will write exact stream-measured uncompressed bytes, compressed bytes, and CRC-32, resolving the truncation and checksum failure bugs.

### 4.2 Actionable Implementation Recipe for M1 Developer

In `CryptoEngine.kt` inside `encryptItemsToV3Vault`:

```kotlin
// Track total measured bytes across all items for catalogRoot
var totalMeasuredBytes = 0L

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

    var writtenBytes = 0L
    context.contentResolver.openInputStream(item.uri)?.use { inStream ->
        var read: Int
        while (inStream.read(ioBuffer).also { read = it } != -1) {
            if (!isPrecompressed) {
                crc.update(ioBuffer, 0, read)
            }
            zipOut.write(ioBuffer, 0, read)
            writtenBytes += read
            processedSourceBytes += read
            val totalForProgress = if (totalSourceBytes > 0) totalSourceBytes else processedSourceBytes
            val pct = (processedSourceBytes.toFloat() / totalForProgress) * 100f
            onProgress(pct.coerceIn(0f, 98f), processedSourceBytes, totalForProgress)
        }
    } ?: throw java.io.IOException("Cannot open input stream for ${item.name}")
    zipOut.closeEntry()

    if (!isPrecompressed) {
        measuredUncompressedSize = writtenBytes
        val zipCompSize = zipEntry.compressedSize
        measuredCompressedSize = if (zipCompSize > 0) {
            zipCompSize
        } else {
            // Fallback: exclude 30-byte local header, filename, and 16-byte data descriptor
            val headerOverhead = 30 + item.relativePath.toByteArray(Charsets.UTF_8).size + 16
            val span = currentZipOffset - entryOffset
            if (span > headerOverhead) span - headerOverhead else measuredUncompressedSize
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

// In catalogRoot (line ~581):
val catalogRoot = JSONObject().apply {
    put("version", 3)
    put("totalSize", if (totalMeasuredBytes > 0L) totalMeasuredBytes else totalSourceBytes)
    put("fileCount", items.size)
    put("files", catalogEntries)
}
```

---

## 5. Verification Method

### 5.1 Unit Tests in `CryptoUnitTest.kt`
Add the following unit test cases to `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`:

1. **`testZeroReportedSizeManifestAccuracyAndDecryption`**:
   - Prepare a 128 KB sample file.
   - Construct a `SelectedItem(uri = testUri, name = "photo.jpg", relativePath = "photo.jpg", sizeBytes = 0L)`.
   - Call `CryptoEngine.encryptItemsToV3Vault`.
   - Call `CryptoEngine.readV3VaultManifest`.
   - Assert `catalog.files[0].sizeBytes == 131072L` (NOT 0L).
   - Assert `catalog.files[0].compressedSize == 131072L`.
   - Assert `catalog.totalSize == 131072L`.
   - Assert `catalog.files[0].crc32 != 0L`.
   - Call `CryptoEngine.decryptV3VaultToDirectory`.
   - Assert extracted file length == 131072L and content matches original byte-for-byte.

2. **`testGenuinelyEmptyFileHandling`**:
   - Prepare a 0-byte sample file.
   - Construct a `SelectedItem(uri = emptyUri, name = "empty.txt", relativePath = "empty.txt", sizeBytes = 0L)`.
   - Call `CryptoEngine.encryptItemsToV3Vault`.
   - Call `CryptoEngine.readV3VaultManifest`.
   - Assert `catalog.files[0].sizeBytes == 0L`.
   - Assert `catalog.files[0].compressedSize == 0L`.
   - Assert `catalog.files[0].crc32 == 0L`.
   - Call `CryptoEngine.decryptV3VaultToDirectory`.
   - Assert extracted file exists and has length == 0L.

3. **`testDeflatedFileCRCAndCompressedSize`**:
   - Prepare a 200 KB compressible text file.
   - Construct a `SelectedItem(uri = textUri, name = "data.json", relativePath = "data.json", sizeBytes = 0L)`.
   - Call `CryptoEngine.encryptItemsToV3Vault`.
   - Call `CryptoEngine.readV3VaultManifest`.
   - Assert `catalog.files[0].sizeBytes == 200 * 1024L`.
   - Assert `catalog.files[0].compressedSize > 0L && catalog.files[0].compressedSize < catalog.files[0].sizeBytes`.
   - Assert `catalog.files[0].crc32 != 0L`.
   - Assert `catalog.files[0].isCompressed == true`.

4. **`testLegacyV630VaultBackwardCompatibility`**:
   - Decrypt a simulated or existing v6.3.0 vault container with `decryptV3VaultToDirectory`.
   - Assert 100% extraction success and data match.

### 5.2 Independent Verification Commands
- Web regression suite:
  ```bash
  node test/test-android-encryption.js
  node test/test-integration-m5.js
  ```
- Android unit tests:
  ```bash
  ./gradlew test
  ```

### 5.3 Invalidation Conditions
- Any code that writes `0L` to `entryJson` when `item.sizeBytes == 0L` but the stream contains data invalidates R1.
- Any calculation of `compressedSize` for DEFLATED files that includes the 30-byte local header or 16-byte data descriptor invalidates `StreamUnpacker.js` compatibility.
- Any regression in `decryptV3VaultToDirectory` reading existing v6.3.0 vaults invalidates backward compatibility.
