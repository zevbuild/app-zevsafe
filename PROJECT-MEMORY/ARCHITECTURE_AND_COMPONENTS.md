# 🏗️ ZevSafe Android Architecture & System Components

> **Package Identity:** `com.zevbuild.zevsafe`  
> **Architecture Pattern:** Unidirectional Data Flow (UDF) with MVVM, Kotlin Coroutines, and Jetpack Compose.

---

## 1. High-Level Architectural Layers

```text
┌─────────────────────────────────────────────────────────────────┐
│                 Jetpack Compose UI (Material 3)                 │
│  HomeScreen · EncryptScreen · DecryptScreen · VaultBrowserScreen│
└───────────────────────────────┬─────────────────────────────────┘
                                │ Observes UI State (StateFlow)
                                ▼ Dispatches User Intents
┌─────────────────────────────────────────────────────────────────┐
│                     VaultViewModel (MVVM)                       │
│  State Management · Coroutine Lifecycles · Progress Calculations│
└───────────────────────────────┬─────────────────────────────────┘
                                │ Triggers Long-Running Ops
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│               VaultForegroundService (Android 14)               │
│  dataSync Service · Persistent Notification · Telemetry · WakeLock│
└───────────────────────────────┬─────────────────────────────────┘
                                │ Invokes Chunk Streaming
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                CryptoEngine (v3 STREAM AEAD)                    │
│  PBKDF2-SHA512 · AES-256-GCM · 4MB Chunks · V3DecryptedStream   │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Reads / Writes via ContentResolver
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              Storage Access Framework (SAF) / I/O               │
│  Scoped Storage · DocumentFile · Content URIs · Zero Disk Temp   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Details

### A. Presentation Layer (`com.zevbuild.zevsafe.ui`)

* **`MainActivity.kt`:** Single-activity architecture. Houses `AppNavigation()`, Material 3 theme wrapper, and handles incoming system intents via `handleIncomingIntent()`.
* **Screens (`ui.screens`):**
  * `HomeScreen.kt`: Hero branding, quick action cards (Encrypt / Decrypt / Guide), and system security status badge.
  * `EncryptScreen.kt`: Target file/folder selector, password input with real-time entropy estimation, optional Keyfile 2FA picker, and stage progress tracker.
  * `DecryptScreen.kt`: Vault file selector (`.zev`), password input, keyfile picker, extraction target selector, and decrypt progress tracker.
  * `VaultBrowserScreen.kt`: Decrypted directory hierarchy browser with breadcrumbs, search filtering, single-file extraction, and the embedded **Cinema Media Player**.
  * `SecurityGuideScreen.kt`: Offline cryptographic documentation, zero-knowledge explanations, and FAQ.
* **Reusable Components (`ui.components`):**
  * `StageProgressTracker.kt`: 3-stage progress pills (`Compress` → `Encrypt/Decrypt` → `Save`) with live throughput telemetry (MB/s, ETA, bytes processed).
  * `PasswordStrengthMeter.kt`: 5-level real-time entropy calculator (Very Weak to Military Grade) with dynamic color bars.
  * `CyberCard.kt`: Glassmorphic elevated container with specular gradients and neon border accents.
  * `AmbientBackground.kt`: Animated deep-space dark background with glowing purple and teal radial orbs.
  * `PasswordRecoveryDialog.kt`: Zero-knowledge safety warning dialog emphasizing non-recoverability of lost passwords.
  * `ActivityTerminal.kt`: Real-time cryptographic execution log emulator.

---

### B. State Management Layer (`com.zevbuild.zevsafe.viewmodel`)

* **`VaultViewModel.kt`:**
  * Exposes reactive state via Kotlin `StateFlow<VaultUiState>`.
  * Manages asynchronous tasks using `viewModelScope` and `Dispatchers.IO`.
  * Emits live progress events (percent, speed in MB/s, stage name, elapsed time).
  * Coordinates with `VaultForegroundService` to keep operations alive across app backgrounding or screen rotation.

---

### C. Background Service Layer (`com.zevbuild.zevsafe.service`)

* **`VaultForegroundService.kt`:**
  * **Android 14 Compliant:** Registered with `android:foregroundServiceType="dataSync"`.
  * **Notification Channel:** Displays persistent notification with real-time progress bar (`setProgress(100, percent, false)`), processed megabytes, and current transfer speed.
  * **WakeLock Management:** Acquires a partial wake lock (`PowerManager.PARTIAL_WAKE_LOCK`) to prevent Android OS Doze mode from throttling CPU cycles during multi-GB encryption.
  * **Lifecycle Cleanup:** Automatically releases wake lock and dismisses notification upon completion or failure.

---

### D. Cryptographic Engine Layer (`com.zevbuild.zevsafe.crypto`)

* **`CryptoEngine.kt`:**
  * Pure Kotlin/JVM cryptographic pipeline utilizing standard JCA (`javax.crypto.Cipher`, `java.security.SecureRandom`).
  * Implements `packV3Header()`, `unpackV3Header()`, `computeChunkAad()`, `deriveMasterKey()`.
  * Provides `encryptV3Stream()`: reads input stream, chunks into 4 MB blocks, applies AES-GCM encryption with incrementing counter IV, appends tail manifest, and streams to output.
  * Provides `decryptV3Stream()`: unpacks header, seeks to tail manifest, verifies AAD, extracts files directly into destination directory.
  * Provides `V3DecryptedInputStream`: custom sequential `InputStream` enabling on-the-fly streaming decryption directly into media players.

---

### E. Media Player Integration (`AndroidX Media3 ExoPlayer`)

* **In-App Cinema Player:**
  * Hosted inside `VaultBrowserScreen.kt`.
  * Plays encrypted videos (`.mp4`, `.mkv`, `.webm`, `.mov`) and audio files (`.mp3`, `.wav`, `.flac`, `.aac`) directly from the vault.
  * Decrypts chunks sequentially into a custom Media3 `DataSource` backed by `V3DecryptedInputStream`.
  * **Zero Disk Persistence:** No decrypted media data ever touches flash storage; volatile RAM is freed immediately as playback progresses.

---

### F. System Integration & Scoped Storage

1. **System Share Sheet Integration:**
   * Handled via `intent-filter` in `AndroidManifest.xml`:
     * `ACTION_VIEW` for `.zev` MIME types (`application/octet-stream`, `*/*`).
     * `ACTION_SEND` & `ACTION_SEND_MULTIPLE` for receiving photos, videos, or archives directly from other apps to encrypt.
2. **Storage Access Framework (SAF):**
   * Uses `ActivityResultContracts.OpenDocument()`, `OpenDocumentTree()`, and `CreateDocument()`.
   * Preserves folder hierarchy using `androidx.documentfile.provider.DocumentFile`.
   * Operates via `ContentResolver.openInputStream()` and `ContentResolver.openOutputStream()`.
