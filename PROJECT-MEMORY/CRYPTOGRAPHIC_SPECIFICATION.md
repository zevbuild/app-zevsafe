# 🔐 ZevSafe Cryptographic Specification — v3 STREAM AEAD (`ZV3\0`)

> **Interoperability Standard:** Bit-identical container specification shared across:
> - Android Native App (`app-zevsafe`)
> - Web Portal (`zevsafe` / `zevsafe.pages.dev`)
> - Desktop PowerShell Streaming Engine (`tools/encrypt.ps1` / `tools/decrypt.ps1`)

---

## 1. Container Header Architecture (57 Bytes)

Every `.zev` v3 vault begins with a 57-byte binary container header structured as follows:

```text
┌──────────────┬──────────────┬───────────┬──────────────────┬────────────────┬────────────────────────┐
│ Magic 'ZV3\0'│ Version 0x03 │ Flags (1) │ Salt (32 Bytes)  │IV Prefix (12B) │ Manifest Offset (7B)   │
│ 4 Bytes      │ 1 Byte       │ 1 Byte    │ CSPRNG Random    │ Base Nonce     │ Big-Endian UInt56      │
└──────────────┴──────────────┴───────────┴──────────────────┴────────────────┴────────────────────────┘
0              4              5           6                  38               50                       57
```

### Byte-by-Byte Field Breakdown

| Offset | Length | Field Name | Type | Description |
|---|---|---|---|---|
| `0..3` | 4 Bytes | **Magic Identifier** | ASCII | Constant `ZV3\0` (`0x5A, 0x56, 0x33, 0x00`). Identifies the v3 stream format. |
| `4` | 1 Byte | **Container Version** | Uint8 | Always `0x03` for the STREAM AEAD format. |
| `5` | 1 Byte | **Vault Flags** | Bitmask | Bit 0 (`0x01`): Keyfile 2FA enabled.<br>Bit 1 (`0x02`): Multi-file / Folder archive.<br>Bits 2..7: Reserved (must be `0`). |
| `6..37` | 32 Bytes | **CSPRNG Salt** | Binary | 256-bit cryptographically secure random salt generated via `java.security.SecureRandom`. |
| `38..49` | 12 Bytes | **IV Prefix** | Binary | 96-bit base nonce. Bytes `0..7` form the random streaming prefix; bytes `8..11` form the initial counter base. |
| `50..56` | 7 Bytes | **Manifest Offset** | Big-Endian Uint56 | Absolute byte offset pointing to the start of the encrypted tail catalog. Allows <100ms random-access catalog loading without parsing file chunks. |

---

## 2. Key Derivation Function (PBKDF2-SHA512)

ZevSafe employs rigorous key stretching to defend against modern GPU, ASIC, and distributed dictionary attacks:

* **Algorithm:** PBKDF2 with HMAC-SHA512 (RFC 8018 / PKCS #5 v2.1)
* **Iteration Count:** **600,000 rounds**
* **Salt Length:** 32 bytes (256 bits)
* **Derived Key Length:** 256 bits (32 bytes)
* **Execution:** Offloaded to background coroutine / worker threads (`Dispatchers.Default`) to maintain 60 FPS UI responsiveness.

### Keyfile Two-Factor Authentication (2FA)

When the Keyfile 2FA flag (`0x01`) is set:
1. The user selects any physical file (image, key document, binary, video).
2. The file is digested into a 256-bit hash using SHA-256:
   $$\text{KeyfileHash} = \text{SHA-256}(\text{KeyfileBytes})$$
3. The derived PBKDF2 key is combined with the keyfile hash via bitwise XOR:
   $$\text{MasterKey} = \text{PBKDF2}_{\text{SHA512}}(\text{Password}, \text{Salt}, 600\,000) \oplus \text{KeyfileHash}$$
4. Neither the password nor the keyfile alone can derive the decryption key.

---

## 3. 4 MB Chunk STREAM AEAD Pipeline

To enforce bounded memory (< 150 MB heap), files and folders are partitioned into **4 MB (4,194,304 bytes)** chunks.

### A. Nonce / IV Construction (12 Bytes)

For each chunk $i$ ($0, 1, 2, \dots$):
* Bytes `0..7`: `ivPrefix[0..7]` (Fixed 8-byte session nonce)
* Bytes `8..11`: Big-Endian 32-bit integer representing chunk index $i$:
  $$\text{IV}_i = \text{ivPrefix}[0..7] \;\|\; \text{toBigEndian4Bytes}(i)$$

This mathematical construction guarantees that no two chunks ever share the same IV under the derived key.

### B. Authenticated Additional Data (AAD) Construction (42 Bytes)

Every chunk is authenticated with a 42-byte cryptographically bound AAD block:

```text
┌─────────────────┬─────────────────┬──────────────┬──────────────┬──────────────┬──────────────────┬──────────────────┐
│ 'ZV3AAD' Prefix │ IV Prefix (12B) │ Chunk Idx(4B)│ Chunk Len(4B)│ IsLast (1B)  │ Manifest Off(7B) │ Keyfile Tag (8B) │
│ 6 Bytes         │ Session Nonce   │ UInt32 BE    │ UInt32 BE    │ 0x00 or 0x01 │ UInt56 BE        │ SHA256(Key)[0..7]│
└─────────────────┴─────────────────┴──────────────┴──────────────┴──────────────┴──────────────────┴──────────────────┘
0                 6                 18             22             26             27                 34                 42
```

1. **`AAD_PREFIX` (6 Bytes):** Constant ASCII `ZV3AAD` (`0x5A, 0x56, 0x33, 0x41, 0x41, 0x44`).
2. **`ivPrefix` (12 Bytes):** The 12-byte base IV from the container header.
3. **`chunkIndex` (4 Bytes):** 32-bit big-endian index. Enforces strict chronological order; prevents chunk reordering, dropping, or repetition.
4. **`chunkPlaintextSize` (4 Bytes):** 32-bit big-endian length of the unencrypted chunk plaintext. Prevents truncation.
5. **`isLastChunk` (1 Byte):** `0x01` if this is the final chunk in the data stream; `0x00` otherwise. Prevents stream truncation attacks.
6. **`manifestOffset` (7 Bytes):** 56-bit big-endian offset of the manifest. Cryptographically binds header metadata to every chunk payload.
7. **`keyfileTag` (8 Bytes):** First 8 bytes of `SHA-256(Keyfile)` (or 8 zeros if 2FA is absent). Ensures keyfile status is authenticated in cipher metadata.

### C. Ciphertext & Auth Tag

* **Cipher:** AES-256 in Galois/Counter Mode (AES-256-GCM)
* **Tag Size:** 128 bits (16 bytes)
* Each encrypted chunk output has length:
  $$\text{EncryptedChunkLength} = \text{PlaintextLength} + 16 \text{ bytes}$$

---

## 4. Tail Manifest Catalog

Located at `manifestOffset`, the tail manifest envelope stores directory metadata, file paths, stream-measured uncompressed and compressed sizes, CRC32 checksums, and chunk byte spans.

1. **Format:** JSON metadata envelope (`{"version": 3, "totalSize": Long, "fileCount": Int, "files": [...]}`).
2. **File Entry Schema:**
   - `"path"`: Relative file path in vault
   - `"name"`: File display name
   - `"size"`: Stream-measured uncompressed byte length (accurately measured during streaming, even when SAF queries report 0 bytes)
   - `"uncompressedSize"`: Alias for uncompressed byte length
   - `"compressedSize"`: Actual compressed payload byte length (measured post-compression; matches `size` for STORED files)
   - `"localHeaderOffset"`: Byte offset of `PK\x03\x04` in the ZIP plaintext stream
   - `"compressed"`: Boolean (`false` for STORED precompressed files, `true` for DEFLATED files)
   - `"crc32"`: IEEE 802.3 CRC-32 checksum (calculated for all STORED and DEFLATED files)
   - `"chunkStart"`: Starting 4 MB chunk index `(localHeaderOffset / CHUNK_SIZE)`
   - `"chunkEnd"`: Ending 4 MB chunk index `((localHeaderOffset + 30 + path.length + compressedSize) / CHUNK_SIZE)`
3. **Encryption:** Encrypted via AES-256-GCM envelope with dedicated salt and 12-byte IV.
4. **Random-Access Slicing:** The native app and web portal read the first 57 bytes, seek immediately to `manifestOffset`, and decrypt only the catalog block. This allows displaying 10,000+ files in **< 100 ms** using **< 15 MB RAM**.

---

## 5. Streaming Decryption & `V3DecryptedInputStream`

To deliver decrypted bytes into consumers (such as AndroidX Media3 ExoPlayer or file extractors) without thread deadlocks or memory accumulation:

* **`V3DecryptedInputStream`:** A custom `java.io.InputStream` that maintains an internal 4 MB plaintext buffer.
* As the consumer calls `read()`, bytes are drained from the current buffer.
* When the buffer is exhausted, the next 4 MB ciphertext chunk is read from disk/content URI, decrypted in-place via AES-GCM, verified against its 42-byte AAD, and placed into the buffer.
* Peak memory is bounded to exactly **one 4 MB chunk buffer + cipher overhead (< 15 MB)**.

---

## 6. Backward Compatibility (v2 & v1 Vaults)

`CryptoEngine.kt` automatically identifies container formats:

1. **`ZV2\0` (v2 Standard):**
   * 50-byte header: `ZV2\0` (4B) + Version `0x02` (1B) + Flags (1B) + Salt (32B) + IV (12B).
   * Monolithic AES-256-GCM ciphertext.
   * PBKDF2-SHA512 (600,000 iterations).
2. **Legacy v1 Format:**
   * 28-byte header: Salt (16B) + IV (12B).
   * PBKDF2-SHA256 (100,000 iterations).

---

## 7. Fast Targeted Single-File Extraction (`extractSingleFileFromV3Vault`)

To preview or extract an individual file without decompressing or decrypting an entire multi-gigabyte vault:

1. **Deterministic Chunk Calculation:**
   * Each entry in the manifest catalog provides `localHeaderOffset`, `compressedSize`, `chunkStart`, and `chunkEnd`.
   * For non-final chunks, vault offset is:
     $$\text{chunkVaultStart} = 57 + i \times (\text{chunkSize} + 4 + 16)$$
   * `CryptoEngine.extractSingleFileFromV3Vault` seeks directly to `chunkVaultStart` using `FileChannel.position()`, decrypting only the contiguous span $i \in [\text{chunkStart}, \text{chunkEnd}]$ ($\le 2$ chunks for typical files $< 4$ MB).
2. **In-Span Local File Header Parsing:**
   * Relative offset: $\text{relPos} = \text{localHeaderOffset} - (\text{chunkStart} \times \text{chunkSize})$.
   * Parses standard ZIP Local File Header (`PK\x03\x04` / `0x04034b50`) at $\text{relPos}$ to extract exact filename length $n$ and extra field length $m$.
   * Payload starts at $\text{relPos} + 30 + n + m$.
3. **Adaptive Boundary Expansion:**
   * If extra header bytes extend the payload beyond the initial span, the engine automatically decrypts chunk $\text{chunkEnd} + 1$ without failing.
4. **Decompression & Memory Bound:**
   * DEFLATED streams are inflated via `java.util.zip.Inflater(true)` (nowrap); STORED payloads are copied directly.
   * Peak heap memory during targeted extraction remains strictly bounded under **15 MB** (1–2 chunks in memory), and execution completes in **< 100 ms**.
