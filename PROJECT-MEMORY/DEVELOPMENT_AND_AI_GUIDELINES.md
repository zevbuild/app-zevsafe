# 🛡️ Development & AI Agent Guidelines for ZevSafe Android

> **CRITICAL ARCHITECTURAL CONSTRAINTS:**  
> These rules apply to all human developers and AI assistants (Antigravity, Cursor, Claude, Copilot, Windsurf) working on `app-zevsafe`.

---

## 🔒 1. Absolute Security & Privacy Constraints

### A. Zero Network Stack (Non-Negotiable)
* **NEVER add `android.permission.INTERNET` to `AndroidManifest.xml`.**
* **NEVER introduce network libraries or telemetry SDKs** (Retrofit, OkHttp, Ktor-client, Firebase, Sentry, Mixpanel, Google Play Services).
* The application must remain mathematically verifiable as air-gapped and offline.

### B. Zero Plaintext Disk Persistence
* Decrypted files must exist only in volatile RAM or directly in user-selected output folders.
* In-app media playback (via ExoPlayer) must stream directly from `V3DecryptedInputStream`. **NEVER stage decrypted videos as temporary files in `context.cacheDir` or `externalCacheDir`.**

### C. Zero-Knowledge Cryptography
* Encryption keys derived from passphrases or keyfiles must never be persisted to `SharedPreferences`, `DataStore`, or non-volatile storage.
* Passwords must be discarded as soon as cryptographic operations complete.

---

## ⚡ 2. Memory Bounding & Streaming Constraints

* **Strict Heap Limit (< 150 MB):** Never read entire input streams into byte arrays (`stream.readBytes()` is strictly forbidden on vault or payload inputs).
* **Chunked Processing:** All encryption and decryption must route through the 4 MB STREAM AEAD chunking pipeline.
* **Avoid Asynchronous Pipe Deadlocks:** For feeding decrypted streams into consumers, use sequential pull streams (`V3DecryptedInputStream`) rather than concurrent `PipedInputStream`/`PipedOutputStream`.

---

## 🤝 3. Cross-Platform Interoperability (`ZV3\0`)

* The `.zev` container specification is shared between the Android native app, the web portal ([`zevsafe.pages.dev`](https://zevsafe.pages.dev)), and desktop PowerShell tools.
* **NEVER change the header layout (57 bytes), PBKDF2 parameters (600,000 iterations, 32-byte salt), AAD structure (42 bytes), or chunk size (4 MB)** without synchronizing the web and desktop codebases.

---

## 📋 4. Mandatory AI Agent Update Protocol

Whenever modifying or adding code in `app-zevsafe`:

1. **Update `CHANGELOG.md`:** Document changes under the active version with categorized tags (`New`, `UI`, `Security`, `Perf`, `Fix`).
2. **Update `README.md`:** Keep version badges, feature tables, and usage instructions synchronized.
3. **Verify Unit Tests:** Always run unit tests before concluding:
   ```bash
   ./gradlew test
   ```
   Ensure 100% of tests pass.
