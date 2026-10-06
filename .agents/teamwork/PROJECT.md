# Project: ZevSafe Android v6.4.0 Overhaul

## Architecture
ZevSafe Android (`app-zevsafe`) is a 100% offline, air-gapped Android encryption application built with Jetpack Compose, Kotlin coroutines/flows, and AndroidX Media3.
- **Crypto Subsystem (`com.zevbuild.zevsafe.crypto`)**: Custom `ZV3\0` STREAM AEAD vault format with 4 MB AES-256-GCM chunks, authenticated tail manifest, and PBKDF2-HMAC-SHA256 key derivation.
- **State & ViewModel Subsystem (`com.zevbuild.zevsafe.viewmodel`)**: `VaultViewModel` orchestrating encryption/decryption pipelines, streaming progress state, catalog loading, and targeted single-file extraction.
- **UI Subsystem (`com.zevbuild.zevsafe.ui`)**:
  - `EncryptScreen`: File selection dropzone, progress pipeline, speed/throughput telemetry, success/error banners.
  - `DecryptScreen`: Vault selection, format inspector badge, speed/throughput telemetry, unlock success card.
  - `VaultBrowserScreen`: File catalog list with MIME-based color coding, file type tags, tap-to-preview hints, floating "Export All" FAB.
  - `ui/viewer`: Fullscreen `VideoPlayerActivity` with Media3 ExoPlayer, auto-hiding controls, portrait/landscape support.
  - `ui/components`: Cyber audio modal with glowing disc, animated equalizer bars, seekbar, loop/speed controls; `FileViewerHelper` with dynamic `FileProvider` external intent fallback.
- **Verification Subsystem (`app/src/test/java/com/zevbuild/zevsafe`)**: Pure JVM JUnit 4 test suites for cryptographic correctness, chunk streaming, manifest validation, and single-file extraction.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | R1: Stream-Measured Manifest Sizes | Record actual uncompressed and compressed byte lengths and CRC-32 for all items (STORED and DEFLATED) in manifest JSON; fix 0-byte size query bug | M1 | Survey 1 (Crypto) |
| 2 | R1: Manifest Total Size Calculation | Populate manifest root `totalSize` using measured byte sums instead of unverified source sizes | M1 | Survey 1 (Crypto) |
| 3 | R1: Backward Compatibility & Zero-Byte Test | Verify zero-byte reported files encrypt and decrypt cleanly; ensure v6.3.0 vaults decrypt without regression | M1 | Survey 1 (Crypto) |
| 4 | R2: Targeted Single-File Extraction | Add `CryptoEngine.extractSingleFileFromV3Vault` reading only `chunkStart..chunkEnd` via `FileChannel` seeking (< 15 MB RAM, < 100 ms) | M2 | Survey 1 (Crypto) |
| 5 | R2: Session Extraction Caching | Cache extracted previews per vault session in `VaultViewModel.kt`; eliminate repeated full-vault decryptions | M2 | Survey 1 (Crypto) |
| 6 | R3: Fullscreen Video Player | `VideoPlayerActivity` + `VideoPlayerScreen` with auto-hiding media controls (play/pause, seek, time, volume, rotation) | M3 | Survey 2 (Media) |
| 7 | R3: Dedicated Cyber Audio Player | `AudioPlayerModal` with glowing album-art disc, animated equalizer bars, interactive seekbar, timestamps, repeat/speed toggles | M3 | Survey 2 (Media) |
| 8 | R3: External File Fallback | `FileViewerHelper` launching `Intent.ACTION_VIEW` via dynamic FileProvider `${context.packageName}.fileprovider` with `FLAG_GRANT_READ_URI_PERMISSION` for PDFs and office documents | M3 | Survey 2 (Media) |
| 9 | R3: VaultBrowser Media Routing | Route taps in `VaultBrowserScreen` to video activity, audio modal, preview modal, or external open-with based on MIME type | M3 | Survey 2 (Media) |
| 10 | R4: Telemetry & Speed Gauges | Add `speedMBs` to `OperationProgressState`; calculate MB/s for both encrypt and decrypt pipelines; update `StageProgressTracker` | M4 | Survey 3 (UI/UX) |
| 11 | R4: Encrypt Screen Overhaul | Animated glowing dropzone, expandable selected files list, persistent in-screen Success & Error banners with direct actions | M4 | Survey 3 (UI/UX) |
| 12 | R4: Decrypt Screen Overhaul | Glowing vault selection card, interactive Vault Format Inspector badge (v3, 4 MB chunk, PBKDF2 600k, 2FA status), speed gauge, polished success card | M4 | Survey 3 (UI/UX) |
| 13 | R4: Vault Browser Overhaul | MIME color coding (Rose video, Amber audio, Teal image, Sky Blue docs, Violet archive), file type badges, preview hints, floating "Export All" FAB | M4 | Survey 3 (UI/UX) |
| 14 | R4: Cyber-Dark Design Language | Glowing gradient borders (`Brush.linearGradient`/`sweepGradient`), glassmorphic card surfaces, smooth transitions | M4 | Survey 3 (UI/UX) |
| 15 | R5: Version Bump | Bump to `versionCode = 31`, `versionName = "6.4.0"` in `app/build.gradle.kts` | M5 | Survey 3 (Release) |
| 16 | R5: Git Tag & CI/CD Verification | Create Git tag `v6.4.0` pointing to HEAD; verify `.github/workflows/build-apk.yml` triggers on `v*` and builds release APKs | M5 | Survey 3 (Release) |
| 17 | R5: Project Memory & Docs Sync | Synchronize `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and all `PROJECT-MEMORY/*.md` files | M5 | Survey 3 (Release) |
| 18 | E2E: Automated Verification Suite | JVM unit tests covering R1 0-byte manifest, R2 targeted extraction, R3 media routing, R4 models, and offline security bounds | E2E | All Surveys |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Manifest Size Bug Fix (R1) | `CryptoEngine.kt` stream-measured sizes, manifest JSON, CRC calculation, unit tests | none | DONE |
| 2 | M2: Fast Single-File Extraction (R2) | `CryptoEngine.kt` targeted chunk extraction, `VaultViewModel.kt` preview caching | M1 | DONE |
| 3 | M3: In-App Media & Document Viewer (R3) | `VideoPlayerActivity`, `AudioPlayerModal`, `FileViewerHelper`, `VaultBrowserScreen` integration | M2 | DONE |
| 4 | M4: UI/UX Screen Overhaul (R4) | `EncryptScreen`, `DecryptScreen`, `VaultBrowserScreen`, `StageProgressTracker`, `CyberCard`, floating FAB | M3 | DONE |
| 5 | M5: Version Bump, Release Tag & Docs (R5) | `build.gradle.kts`, `git tag v6.4.0`, `CHANGELOG.md`, `README.md`, all `PROJECT-MEMORY` docs | M4 | DONE |
| 6 | E2E: Requirement & Regression Test Suite | Comprehensive tests in `CryptoUnitTest.kt` and E2E runner validating all acceptance criteria | none (dual-track) | DONE |

## Interface Contracts

### M1 ↔ M2: Manifest Data Contract
- `DecryptedFileEntry` must contain accurate `sizeBytes`, `compressedSize`, `localHeaderOffset`, `isCompressed`, `crc32`, `chunkStart`, and `chunkEnd`.
- When an item has 0-byte provider-reported size, `sizeBytes` in `DecryptedFileEntry` must match the measured uncompressed byte length (> 0 for non-empty files).
- `chunkStart = (lho / DEFAULT_CHUNK_SIZE).toInt()`
- `chunkEnd = ((lho + 30 + path.length + compSize) / DEFAULT_CHUNK_SIZE).toInt()`

### M2 ↔ M3: Extraction & Preview Contract
- `CryptoEngine.extractSingleFileFromV3Vault(context, vaultUri, entry, password, keyfileHash, outputFile): DecryptedFileEntry`
- `VaultViewModel.extractSingleFileIfNeeded(context, entry, onReady: (DecryptedFileEntry) -> Unit)`
- Extracted files must reside in `context.filesDir` (e.g. `preview_cache_<vaultName>/<filename>`) and set `localFileUri = Uri.fromFile(outputFile)`.
- Previews must complete in < 100 ms for small files, and never decrypt unaffected chunks.

### M3 ↔ M4: Media & Viewer Routing Contract
- `VideoPlayerActivity.EXTRA_URI`: String (URI of extracted video file)
- `VideoPlayerActivity.EXTRA_NAME`: String (File display name)
- `AudioPlayerModal(audioEntry, onDismiss)`
- `FileViewerHelper.openWithExternalApp(context, entry)` using `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`

### M4 ↔ M5: State & Build Contract
- `OperationProgressState` includes `speedMBs: Double = 0.0`.
- All screens compile with Jetpack Compose Material 3 without missing resource references.
- Version bump in `app/build.gradle.kts` matches `versionCode = 31`, `versionName = "6.4.0"`.

## Code Layout
- `app/src/main/java/com/zevbuild/zevsafe/crypto/`:
  - `CryptoEngine.kt` (Owned by M1 & M2)
  - `CryptoModels.kt` (Owned by M1, M2, M4)
- `app/src/main/java/com/zevbuild/zevsafe/viewmodel/`:
  - `VaultViewModel.kt` (Owned by M2 & M4)
- `app/src/main/java/com/zevbuild/zevsafe/ui/`:
  - `screens/EncryptScreen.kt` (Owned by M4)
  - `screens/DecryptScreen.kt` (Owned by M4)
  - `screens/VaultBrowserScreen.kt` (Owned by M3 & M4)
  - `viewer/VideoPlayerActivity.kt` (Owned by M3)
  - `viewer/FileViewerHelper.kt` (Owned by M3)
  - `components/AudioPlayerModal.kt` (Owned by M3)
  - `components/StageProgressTracker.kt` (Owned by M4)
  - `components/CyberCard.kt` (Owned by M4)
- `app/src/main/AndroidManifest.xml` (Owned by M3)
- `app/build.gradle.kts` (Owned by M5)
- `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt` (Owned by E2E & M1/M2)
- Documentation:
  - `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, `PROJECT-MEMORY/*.md` (Owned by M5)
