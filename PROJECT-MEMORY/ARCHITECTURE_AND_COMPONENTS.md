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
  * `HomeScreen.kt`: Hero branding, quick action cards (Encrypt / Decrypt / Guide), and system security status badge. Supplies cross-screen navigation lambdas (`onNavigateToDecrypt`, `onNavigateToBrowser`) to `EncryptScreen` for seamless direct transitions upon vault creation.
  * `EncryptScreen.kt`: Animated glowing dropzone (`Brush.linearGradient`), expandable selected files list with individual item removal, password input with entropy meter, optional Keyfile 2FA picker, persistent in-screen Success banner card (`Share Vault`, `View in Downloads`, `Explore Now`), isolated banner visibility (`!progressState.isActive` with automatic state reset on selection clearance or new encryption runs), and prominent cyber error alert card with retry.
  * `DecryptScreen.kt`: Glowing vault file selection card (`.zev`), interactive Vault Format Inspector badge card (`v3 STREAM AEAD`, `4 MB Chunks`, `PBKDF2 600k`, `2FA Keyfile Status`), instant catalog inspection button (`viewModel.loadInstantCatalog(context)`), real-time MB/s decryption speed gauge, polished cyber success card with glowing checkmark, and prominent error recovery card.
  * `VaultBrowserScreen.kt`: Decrypted directory hierarchy browser with search filtering, MIME-type color coding (Rose video, Amber audio, Teal image, Sky Blue docs, Violet archive), file extension tag badges (`MP4`, `MP3`, `PDF`, etc.), tap-to-preview hints, single-file extraction routing, embedded Cinema Media Player, and a floating `ExtendedFloatingActionButton` ("Export All") anchored with live file count badge.
  * `SecurityGuideScreen.kt`: Offline cryptographic documentation, zero-knowledge explanations, and FAQ.
* **Reusable Components (`ui.components`):**
  * `StageProgressTracker.kt`: 3-stage progress pills (`Compress` → `Encrypt/Decrypt` → `Save`) with glowing accent borders on active stages, percentage badge, smooth animated progress indicator, and live glowing throughput speed gauge pill badge (`X.X MB/s`).
  * `PasswordStrengthMeter.kt`: 5-level real-time entropy calculator (Very Weak to Military Grade) with dynamic color bars.
  * `CyberCard.kt`: Glassmorphic elevated container with specular gradients and neon border accents.
  * `AmbientBackground.kt`: Animated deep-space dark background with glowing purple and teal radial orbs.
  * `PasswordRecoveryDialog.kt`: Zero-knowledge safety warning dialog emphasizing non-recoverability of lost passwords.
  * `ActivityTerminal.kt`: Real-time cryptographic execution log emulator.

---

### B. State Management Layer (`com.zevbuild.zevsafe.viewmodel`)

* **`VaultViewModel.kt`:**
  * Exposes reactive state via Kotlin `StateFlow` (`encryptProgress`, `decryptProgress`, `decryptedFiles`, `terminalLogs`).
  * Manages asynchronous tasks using `viewModelScope` and `Dispatchers.IO`.
  * **Thread-Safe UI Completion Callbacks:** All completion callbacks (`onSuccess`) are dispatched onto `withContext(Dispatchers.Main)` inside a defensive `try-catch` block. This ensures UI/Intent actions execute safely on the Android main thread and prevents post-operation UI exceptions from falsely triggering encryption/decryption failure states.
  * **Automatic Public `Downloads` Saving (`saveToPublicDownloads`):** Automatically saves encrypted `.zev` vaults and exported `.zip` archives directly into the device's public `Downloads` folder using `MediaStore.Downloads` on Android 10+ (API 29+) and `Environment.DIRECTORY_DOWNLOADS` on Android 8–9.
  * **On-Demand Extraction (`extractSingleFileIfNeeded` & `exportDecryptedZip`):** Since v3 STREAM AEAD vaults unlock by reading only the 57-byte header and tail manifest (`entry.file == null`), `VaultViewModel` supports on-demand selective chunk decryption for individual files (`extractSingleFileIfNeeded`) and streaming full-archive export (`exportDecryptedZip`) when requested in `VaultBrowserScreen` or `DecryptScreen`.
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
  * Implements `createV3ContainerHeader()`, `parseVaultHeader()`, `computeChunkAAD()`, `deriveKeyV3()`.
  * Provides `encryptItemsToV3Vault()`: reads input streams, streams into `ZipOutputStream` with chunked 4 MB AEAD blocks, measures uncompressed and compressed byte lengths, calculates IEEE 802.3 CRC-32 for all items (STORED and DEFLATED), appends authenticated tail manifest with accurate sizes, and updates header manifestOffset. Supports both Android `Context` and pure JVM `inputStreamProvider` / `streamOpener` overloads.
  * Provides `readV3VaultManifest()`: parses header, seeks to tail manifest, decrypts manifest envelope, and returns `VaultCatalog`. Supports `InputStream` and `Context`/`Uri` overloads.
  * Provides `decryptV3VaultStreamToDirectory()` / `decryptV3VaultToDirectory()`: streams and decrypts chunks via `V3DecryptedInputStream` and extracts files via `ZipInputStream`.
  * Provides `V3DecryptedInputStream`: custom sequential `InputStream` enabling on-the-fly streaming decryption directly into media players.

---

### E. Media Player & Document Viewer Integration (`AndroidX Media3 ExoPlayer` & `FileProvider`)

* **Fullscreen Video Player (`VideoPlayerActivity`):**
  * Dedicated edge-to-edge Compose activity registered in `AndroidManifest.xml` (`android:exported="false"`, `configChanges="orientation|screenSize|smallestScreenSize|screenLayout"`).
  * Auto-hiding cyber-dark controls overlay (fade-out after 3.5s of playback; tap screen to toggle visibility).
  * Top bar with back navigation, video title, and format badge.
  * Center transport controls: rewind 10s (`Replay10`), large play/pause circular toggle, fast-forward 10s (`Forward10`).
  * Bottom controls: current position and duration (`mm:ss`), seekbar `Slider` with `TealPrimary` styling, audio mute/volume toggle, and portrait/landscape orientation toggle.
  * Pauses on `Lifecycle.Event.ON_PAUSE` and cleanly releases `ExoPlayer` on `onDestroy()`.

* **Dedicated Cyber Audio Player (`AudioPlayerModal`):**
  * Hosted in `ui/components/AudioPlayerModal.kt` as a specialized Compose modal for audio playback.
  * Rotating vinyl album-art disc with glowing border and musical note center.
  * 14-bar animated equalizer waveform that dynamically animates during playback.
  * Track title, MIME format pill, and file size metadata.
  * Interactive seekbar with `mm:ss` timestamps, repeat/looping toggle (`Player.REPEAT_MODE_ONE`), skip -/+10s controls, and 1.0x/1.5x/2.0x playback speed selector.
  * Smooth 250ms coroutine ticker for seek position; releases `ExoPlayer` on modal dismiss.

* **In-Vault Interactive Image Lightbox (`RichImageViewerModal`):**
  * Hosted in `ui/screens/VaultBrowserScreen.kt` as an edge-to-edge Compose lightbox dialog.
  * On-the-fly targeted chunk extraction renders image bitmaps directly via `BitmapFactory.decodeFile` without writing plaintext unencrypted archives to disk.
  * Touch gesture controls powered by `pointerInput`: two-finger pinch-to-zoom (0.5x to 5.0x) with translation drag-to-pan, and double-tap toggle to immediately zoom to 2.5x or reset to 1.0x.
  * Interactive toolbar: Zoom In (`🔍+`), Zoom Out (`🔍-`), Reset 100% (`1:1`), and 90° Clockwise Rotation (`↻`).
  * Header displays exact image dimensions (e.g. `1920 × 1080`), file size, direct system share sheet launcher, and one-tap save to public device `Downloads`.

* **In-Vault Monospace Code & Text Viewer (`RichCodeTextViewerModal`):**
  * Hosted in `ui/screens/VaultBrowserScreen.kt` for viewing documents and source code directly inside the vault without third-party app leaks.
  * Recognizes over 30 extensions: `.txt`, `.md`, `.json`, `.xml`, `.yaml`, `.yml`, `.csv`, `.log`, `.js`, `.ts`, `.html`, `.css`, `.py`, `.kt`, `.java`, `.c`, `.cpp`, `.h`, `.cs`, `.rs`, `.go`, `.sh`, `.bat`, `.ps1`, `.sql`, `.env`, `.toml`, `.ini`, `.gradle`, `.properties`, `.conf`.
  * Targeted extraction decodes up to 50,000 characters and 1 MB into memory on demand.
  * Monospace typography with dedicated line number gutter, soft-wrap toggle (`Wrap: ON / OFF`), 1-tap clipboard copy button with Android Toast confirmation, outbound share sheet, and direct export to `Downloads`.

* **Document Fallback & Open-With Intent (`FileViewerHelper`):**
  * Hosted in `ui/viewer/FileViewerHelper.kt`.
  * `openWithExternalApp(context, entry)`: Resolves extracted local file, creates content URI via `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`, and fires `Intent(Intent.ACTION_VIEW)` with `FLAG_GRANT_READ_URI_PERMISSION`.
  * Gracefully catches `ActivityNotFoundException` and delegates to `shareFile(context, entry)`.
  * `FilePreviewModal` in `VaultBrowserScreen.kt` displays an active card for unsupported binary formats with `[ 🚀 Open in External App ]` and `[ 📤 Share File ]` buttons.

---

### F. System Integration, `FileProvider` & Scoped Storage

1. **System Share Sheet Integration (`Intent` Filters):**
   * Handled via `intent-filter` in `AndroidManifest.xml`:
     * `ACTION_VIEW` for `.zev` MIME types (`application/octet-stream`, `*/*`).
     * `ACTION_SEND` & `ACTION_SEND_MULTIPLE` for receiving photos, videos, or archives directly from other apps to encrypt.
2. **`FileProvider` Authority & Outbound Sharing:**
   * Configured in `AndroidManifest.xml` with dynamic authority `android:authorities="${applicationId}.fileprovider"` (resolving to `com.zevbuild.zevsafe.fileprovider`) and paths defined in `res/xml/file_paths.xml` (`cache-path`, `files-path`, `external-path`).
   * All screens (`EncryptScreen.kt`, `DecryptScreen.kt`, `VaultBrowserScreen.kt`) dynamically reference `"${context.packageName}.fileprovider"` when generating `content://` URIs via `FileProvider.getUriForFile(...)`. Never hardcode legacy package authorities.
3. **Storage Access Framework (SAF) & `MediaStore.Downloads`:**
   * Utilizes `ActivityResultContracts.OpenMultipleDocuments()` with seamless fallback to `ActivityResultContracts.OpenDocument()` for single/multi-file selection across diverse Android OEM file pickers, and `OpenDocumentTree()` for directory trees.
   * Employs resilient 5-tier URI metadata resolution (`VaultViewModel.resolveSelectedItem`): queries `OpenableColumns.DISPLAY_NAME` & `SIZE`, falls back to `DocumentFile.fromSingleUri`, then `Uri.lastPathSegment`, `ParcelFileDescriptor.statSize`, and finally `InputStream.available()` bytes to prevent silent metadata resolution failures from cloud or third-party content providers.
   * Automatically takes persistable read URI permissions (`FLAG_GRANT_READ_URI_PERMISSION`).
   * Dynamic STORED entry measurement in `CryptoEngine.kt`: measures actual input stream length during CRC32 calculation to eliminate `ZipException: invalid entry size` when packing precompressed files whose size could not be queried via SAF.
   * Writes final encrypted `.zev` vaults and exported `.zip` archives to the user's `Downloads` directory via `MediaStore.Downloads` while also presenting the Android system share sheet (`Intent.ACTION_SEND`) with `FLAG_GRANT_READ_URI_PERMISSION`.

---

### G. Verification Subsystem (`app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`)

* **4-Tier Test Architecture (36 Pure JVM Unit Tests):**
  * **Tier 1 & 2 (Feature & Boundary Tests):**
    * Zero-reported-size item round-trip encryption/decryption (STORED images and DEFLATED text).
    * Stream-measured manifest size recording (`actualSize`, `compressedSize`, IEEE 802.3 CRC-32).
    * Single-file targeted extraction chunk calculation (`chunkStart`, `chunkEnd`), boundary crossing, clamping, and chunk isolation.
    * Empty 0-byte file handling and manifest schema validation.
  * **Tier 3 (Cross-Feature Combinations & Backward Compatibility):**
    * Legacy v1 and v2 monolithic vault decryption compatibility.
    * Encrypted manifest envelope tamper detection and corrupted header detection.
    * MIME-type classification and media viewer routing contract.
    * Telemetry models and speed throughput calculations.
  * **Tier 4 (Real-World Workloads & Security Assurances):**
    * Multi-file mixed STORED/DEFLATED vault round-trip with targeted extraction.
    * Dynamic `FileProvider` authority verification (`${context.packageName}.fileprovider`).
    * Static air-gap audit: zero `android.permission.INTERNET` and no network libraries in `build.gradle.kts`.
    * Large multi-chunk extraction latency and chunk isolation benchmark (< 200 ms).
    * Zero-knowledge password recovery sheet formatting and warnings.

