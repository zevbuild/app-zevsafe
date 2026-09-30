# ⚡ Streaming Pipeline & Bounded Memory Architecture

> **Target Standard:** Peak JVM Heap < 150 MB across 5 GB+ datasets on Android devices with 3 GB–16 GB RAM.

---

## 1. The Mobile Memory Challenge

On Android systems, apps run within individual ART (Android Runtime) instances. Depending on the device and Android version:
* Default app heap limits range from **192 MB to 512 MB** (`android:largeHeap="true"`).
* If an app attempts to read a 1 GB or 4 GB video file into a `ByteArray` or buffer, the Android Low Memory Killer (LMK) triggers an uncatchable `OutOfMemoryError` (OOM) and kills the process.
* Furthermore, garbage collection pressure from millions of temporary objects causes audio/video stutter, dropped frames, and UI freezes.

---

## 2. The 4 MB Chunk Streaming Solution

To eliminate memory scaling with file size, ZevSafe employs an invariant 4 MB chunking pipeline:

$$\text{ChunkSize} = 4 \times 1024 \times 1024 \text{ bytes} = 4\,194\,304 \text{ bytes}$$

### Why 4 MB?
1. **Cipher Throughput:** AES-256-GCM hardware acceleration (ARMv8 Cryptography Extensions) operates at peak bandwidth on large continuous memory segments.
2. **I/O Efficiency:** Matches the standard block I/O size on modern UFS 2.x/3.x flash storage, avoiding write amplification.
3. **Strict Memory Bounding:** At any given instant, active memory consists only of:
   * 1 raw chunk buffer: 4 MB
   * 1 ciphertext/plaintext buffer: 4 MB + 16 bytes
   * Cipher context state: < 64 KB
   * **Total active footprint per stream: ~8.5 MB**, well below the 150 MB safety ceiling.

---

## 3. Pull-Based Streaming vs Piped Concurrency

### The Pitfall of Asynchronous Pipes
In early prototypes, a common pattern was using Kotlin coroutines to pipe decrypted bytes from a producer coroutine into a consumer via `PipedInputStream`/`PipedOutputStream` or `ByteChannel`.
However, this introduced severe reliability hazards:
1. **Thread Deadlocks:** If the consumer paused (e.g. video player buffering), the pipe filled up and blocked the producer thread. If coroutine dispatchers shared threads, a mutual deadlock occurred.
2. **Buffer Overrun:** Without strict backpressure, producer coroutines could outrun slow consumers, caching unbounded chunks in heap memory until an OOM occurred.

### The Solution: Sequential `V3DecryptedInputStream`
ZevSafe resolves this with a pull-based, single-threaded sequential stream:

```kotlin
class V3DecryptedInputStream(
    private val encryptedSource: InputStream,
    private val cipherKey: SecretKey,
    private val ivPrefix: ByteArray,
    private val manifestOffset: Long,
    private val keyfileHash: ByteArray?
) : InputStream()
```

#### How It Operates:
1. **Lazy Loading:** No chunks are decrypted until the consumer explicitly invokes `read(b, off, len)`.
2. **In-Place Decryption:** When the internal 4 MB plaintext buffer is depleted:
   - Reads exactly `chunkLength + 16` bytes from the underlying encrypted container.
   - Computes the 42-byte AAD for the current chunk index.
   - Decrypts the chunk via `Cipher.doFinal()` into the internal buffer.
   - Increments the internal chunk counter.
3. **Zero Deadlock Risk:** Execution is strictly synchronous to the consumer's pull requests. No asynchronous queues or pipes exist.
4. **Instant Disposal:** As soon as a chunk is consumed, its memory is reused for the next chunk, keeping heap garbage collection flat and predictable.

---

## 4. End-to-End Encryption Streaming Flow

```text
[Plaintext File/Folder Stream]
             │
             ▼ (4 MB chunks)
[ZipOutputStream (STORE / DEFLATE)]
             │
             ▼ (4 MB block)
[Compute 42-Byte AAD (ivPrefix, index, size, flags)]
             │
             ▼
[AES-256-GCM Encrypt with Nonce(ivPrefix + index)]
             │
             ▼ (4 MB + 16B tag)
[BufferedOutputStream -> SAF Destination URI]
```

At no point in this pipeline does data buffer in memory beyond the current 4 MB block. Multi-gigabyte archives stream directly from source to destination.
