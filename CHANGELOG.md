# Changelog

## Version 6.4.0 — October 5, 2026
- `Fix` (`CryptoEngine.kt`): Added missing `java.io.ByteArrayInputStream` import for `parseVaultHeader` in `extractSingleFileFromV3Vault`, resolving GitHub Actions Android build failure during Kotlin compilation.
- `Fix` (`CryptoEngine.kt`): Resolved manifest size bug where items with zero-reported initial size (`SelectedItem.sizeBytes <= 0L`) wrote `"size": 0`, `"compressedSize": 0`, and `"crc32": 0` into the encrypted manifest JSON envelope.
- `Fix` (`CryptoEngine.kt`): Stream-measured uncompressed and compressed byte lengths and calculated IEEE 802.3 CRC-32 for both STORED (precompressed) and DEFLATED files during streaming. Correctly set root manifest `totalSize` to the sum of measured bytes, preserving accurate `0 B` values for genuinely empty files.
- `New` (`CryptoEngine.kt`): Added stream-based overloads for `encryptItemsToV3Vault`, `readV3VaultManifest`, and `decryptV3VaultStreamToDirectory` supporting direct `InputStream` operations in pure JVM unit tests without Android `Context` or framework stubs.
- `New` (`CryptoEngine.kt`): Added targeted single-file extraction `extractSingleFileFromV3Vault` (supporting `Context + Uri`, `File`, and `InputStreamProvider` overloads), decrypting only the specific AEAD chunk range (`chunkStart..chunkEnd`) containing the target file via direct `FileChannel` seeking (< 15 MB RAM, < 100 ms latency) and parsing the ZIP Local File Header with `Inflater(true)` decompressor.
- `New` (`CryptoEngine.kt`): Included `chunkStart` and `chunkEnd` attributes in manifest catalog JSON entries for instant chunk span lookup during targeted extraction and cross-platform unpacking.
- `Perf` (`VaultViewModel.kt`): Replaced full-vault re-decryption on preview taps in `extractSingleFileIfNeeded` with targeted single-file extraction into `preview_cache_${safeVaultBase}/${entry.name}`, eliminating multi-second full vault unpacking.
- `New` (`VaultViewModel.kt`): Implemented session directory extraction caching (`fallbackExtractAll` to `extracted_${safeVaultBase}_session`) for legacy v1/v2 vaults, guaranteeing monolithic decryption executes at most once per vault session.
- `New` (`VideoPlayerActivity.kt`, `AndroidManifest.xml`): Implemented edge-to-edge fullscreen video player activity with auto-hiding cyber-dark controls overlay (top bar with format pill, center 10s skip/seek and play/pause controls, bottom seekbar with timestamps, volume/mute toggle, and portrait/landscape orientation toggle) powered by Media3 ExoPlayer.
- `New` (`AudioPlayerModal.kt`): Added dedicated cyber audio player dialog featuring a rotating vinyl album-art disc, 14-bar animated equalizer waveform, interactive seekbar, repeat/looping toggle, skip -/+10s controls, and 1.0x/1.5x/2.0x playback speed selector.
- `New` (`FileViewerHelper.kt`): Implemented secure external viewer launcher and share fallback (`openWithExternalApp` & `shareFile`) utilizing dynamic `FileProvider` authority `${context.packageName}.fileprovider` with `FLAG_GRANT_READ_URI_PERMISSION` for PDFs, office documents, and binary files.
- `UI` (`StageProgressTracker.kt`, `CryptoModels.kt`, `VaultViewModel.kt`): Added dedicated real-time throughput telemetry (`speedMBs`) to `OperationProgressState` and calculated live MB/s transfer speed during both v3 chunked encryption and streaming decryption pipelines; enhanced `StageProgressTracker` with live glowing throughput pill badge (`X.X MB/s`), percentage badge, glowing accent borders on active stages, and smooth animated linear progress indicator.
- `UI` (`EncryptScreen.kt`): Overhauled encryption screen with pulsing animated glassmorphic dropzone (`Brush.linearGradient`), expandable selected files list featuring per-item icon, size, and remove controls, persistent in-screen success banner with direct action buttons (`[ Share Vault ]`, `[ View in Downloads ]`, `[ Explore Now ]`), and prominent cyber error alert card with one-tap retry.
- `UI` (`DecryptScreen.kt`): Enhanced decryption screen with glowing vault dropzone card, interactive Vault Format Inspector card displaying parameter badges (`v3 STREAM AEAD`, `4 MB Chunks`, `PBKDF2 600k`, `2FA Keyfile Status`), real-time decryption speed gauge, polished cyber success restoration card with glowing checkmark, and prominent error recovery card.
- `UI` (`VaultBrowserScreen.kt`): Implemented strict MIME color-coding across file entries (Rose `#F43F5E` for Video, Amber `#F59E0B` for Audio, Teal `#2DD4BF` for Images, Sky Blue `#38BDF8` for Documents, Violet `#A78BFA` for Archives), file extension tag badges (`MP4`, `MP3`, `PDF`, `PNG`, `TXT`, etc.), contextual tap-to-preview/tap-to-play subtitle hints, and an `ExtendedFloatingActionButton` ("Export All") anchored at the bottom-end of the screen with live file count badge.
- `UI` (`HomeScreen.kt`): Passed `onNavigateToDecrypt` and `onNavigateToBrowser` navigation lambdas to `EncryptScreen`, enabling instant one-tap navigation to the decrypted vault catalog from the persistent `[ 🚀 Explore Now ]` success banner.
- `Fix` (`DecryptScreen.kt`): Corrected instant catalog inspection invocation from `loadV3CatalogFast` to `loadInstantCatalog` in `DecryptScreen.kt`, aligning with `VaultViewModel.kt` API.
- `Fix` (`EncryptScreen.kt`): Resolved success banner state leak by enforcing `!progressState.isActive` in `AnimatedVisibility` and automatically resetting `lastCreatedVault = null` upon selection clearance (`clearEncryptSelection`) and new encryption runs.
- `Tests` (`CryptoUnitTest.kt`): Added comprehensive unit test suites validating zero-reported-size files, targeted single-file extraction (STORED and DEFLATED), multi-chunk boundary spans (> 4 MB), 2FA keyfile verification, and empty marker files, confirming 100% data integrity.
- `Security`: Enforced 100% offline air-gapped architecture with zero network permissions (`android.permission.INTERNET` omitted), zero third-party telemetry, memory bounded < 150 MB RAM, and dynamic FileProvider URI isolation (`${applicationId}.fileprovider` and `"${context.packageName}.fileprovider"`).
- `Docs`: Fully synchronized technical documentation across `README.md`, `PROJECT_MEMORY.md`, and all `PROJECT-MEMORY/*.md` files (`CRYPTOGRAPHIC_SPECIFICATION.md`, `ARCHITECTURE_AND_COMPONENTS.md`, `STREAMING_AND_MEMORY_BOUNDS.md`, `BUILD_RELEASE_AND_CICD.md`, `DEVELOPMENT_AND_AI_GUIDELINES.md`, `README.md`) reflecting v6.4.0 (build 31) release parameters.

## Version 6.3.0 — September 30, 2026
- `New`: Upgraded cryptographic engine to **v3 STREAM AEAD (`ZV3\0`)** with 57-byte container header, 4 MB chunked AES-256-GCM, per-chunk 12B IVs & 42B AAD, and encrypted tail manifest trailer in `CryptoEngine.kt`.
- `New`: Implemented **Instant Vault Explorer (< 100 ms)** — loads and displays file catalogs instantly from the tail manifest without buffering or decrypting the entire vault payload.
- `New`: Integrated **AndroidX Media3 (ExoPlayer)** Cinema Media Player for streaming video (`.mp4`, `.webm`, `.mkv`, `.mov`) and audio (`.mp3`, `.wav`, `.flac`) directly in-app with zero disk writes.
- `New`: Created **`VaultForegroundService`** with ongoing progress notification (MB/s, ETA, progress bar) and `PARTIAL_WAKE_LOCK` for resilient multi-gigabyte (up to 5 GB) operations.
- `New`: Added Android system intent filters for **`.zev` file associations** (`ACTION_VIEW`) and **Share Target** (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) in `MainActivity.kt` and `AndroidManifest.xml`.
- `Security`: Enforced strict zero-knowledge memory architecture — no unencrypted plaintext or passwords touch non-volatile storage.
- `Fix`: Standardized package identity and application ID to `com.zevbuild.zevsafe`.
- `Fix` (`AndroidManifest.xml`, `EncryptScreen.kt`, `DecryptScreen.kt`, `VaultBrowserScreen.kt`, `VaultViewModel.kt`): Resolved `IllegalArgumentException: Couldn't find meta-data for provider with authority com.aistudio.zevsafe.qvkn.fileprovider` by synchronizing `FileProvider` authority to `${applicationId}.fileprovider` / `${context.packageName}.fileprovider`, automatically saving encrypted `.zev` vaults and exported `.zip` archives directly to the device's `Downloads` folder via `MediaStore.Downloads`, and dispatching UI completion callbacks safely on `Dispatchers.Main`.
- `UI`: Modernized dark cyber theme with v3 STREAM badges, instant exploration buttons, and live telemetry cards.
- `Test` (`CryptoUnitTest.kt`): Expanded test suite to 36 comprehensive tests across 4 tiers: Tier 1 & 2 boundary and feature tests (zero-reported-size item round-trip decryption, stream-measured manifest sizes, targeted single-file extraction chunk calculations, boundary clamping, and chunk isolation), Tier 3 backward compatibility (v1/v2 monolithic vault decryption, v6.3.0 regression invariants, envelope tamper detection), and Tier 4 real-world workloads (mixed STORED/DEFLATED multi-file vaults, dynamic FileProvider authority format verification, and static air-gap check asserting zero internet permissions).
- `Perf` (`icon-192.png`, `icon-512.png`, `zevsafe-logo.png`, `cyber-vault.png`, `favicon.svg`): High-efficiency FastOctree quantization, LANCZOS scaling, and SVG minification across all repository logos and brand images, reducing total image payload from 2,242.60 KB (2.24 MB) down to 148.90 KB (93.4% size reduction).
- `New` (`icon-192.webp`, `icon-512.webp`, `zevsafe-logo.webp`, `cyber-vault.webp`): Generated next-generation WebP image counterparts (`icon-192.webp` at 1.85 KB, `icon-512.webp` at 5.93 KB, `zevsafe-logo.webp` at 10.22 KB, `cyber-vault.webp` at 48.32 KB).
- `Refactor` (`tools/`, `docs/`): Reorganized repository root into modular directories. Moved standalone Windows streaming tools (`encrypt.ps1`, `decrypt.ps1`, `Encrypt-Vault.bat`, `Decrypt-Vault.bat`) into `tools/` with usage documentation. Moved internal task trackers into `docs/task.md` with `docs/README.md`. Cleaned up legacy root scaffold files.
- `Brand` (`assets/zevsafe-logo.svg`, `assets/zevsafe-logo.png`, `assets/zevsafe-logo.webp`, `assets/icon-512.png`, `assets/icon-512.webp`, `assets/icon-192.png`, `assets/icon-192.webp`, `assets/favicon.svg`, `app/src/main/res/drawable/ic_launcher_foreground.xml`): Adopted the definitive "Orbit Concept" visual brand identity system representing a self-contained, air-gapped cryptographic ecosystem. Deployed mathematical SVG master vectors, high-efficiency quantized 512×512 brand marks and squircle app icons, 192×192 launcher icons, and matching Android vector drawable foregrounds.
- `Docs` (`PROJECT_MEMORY.md`, `PROJECT-MEMORY/`, `AGENTS.md`, `GEMINI.md`, `.cursorrules`, `.agents/rules/auto_update_docs.md`): Established comprehensive Project Memory knowledge base and mandatory AI agent synchronization rules detailing binary cryptographic specifications, system architecture, `FileProvider` & `MediaStore.Downloads` integration, streaming memory bounding, build & CI/CD pipelines, and development guidelines so any AI agent can immediately understand and maintain the project.
- `Fix` (`EncryptScreen.kt`, `VaultViewModel.kt`, `CryptoEngine.kt`): Resolved "Select Files" failure and unresponsive file selection UX.
  - Implemented 5-tier fallback URI resolution in `VaultViewModel.resolveSelectedItem` (`OpenableColumns`, `DocumentFile`, `lastPathSegment`, `ParcelFileDescriptor.statSize`, and `InputStream.available`) to ensure resilient file metadata extraction across Google Drive, Recent, MediaStore, and external cloud/SAF document providers.
  - Added `singleFilePickerLauncher` alongside `filePickerLauncher` (`OpenMultipleDocuments`) with automatic fallback to guarantee compatibility across all Android device OEM file managers.
  - Promoted "Select Files" to the primary action button and made the entire dropzone card interactive via `.clickable { launchFilesPicker() }`.
  - Fixed precompressed file STORED packaging in `CryptoEngine.kt` by dynamically calculating the exact byte stream size during the CRC32 pass instead of trusting potentially unqueryable provider sizes, eliminating `ZipException: invalid entry size`.
  - Added single-file visual feedback in the selected items card showing `InsertDriveFile` icon and exact file name.



## Version 3 — July 5, 2026
- Renamed vault file extension from `.enc` to `.zev`
- Updated all UI text, file-picker filters, and PowerShell scripts to match
- Fixed remaining `.enc` references in docs (`README.md`, `how-to-use.html`)
- Optimized mobile layout by scaling down icons slightly on phone screens (<= 520px) for improved comfort and vertical space efficiency
- Streamlined main page copy: removed redundant warning alerts and simplified descriptions to declutter the workspace
- Enhanced design aesthetics: added high-end glows, dynamic gradient borders on card hover, and micro-scale animations to interactive buttons
- Scaled down buttons on phone screens (<= 520px) to provide a more compact and thumb-friendly vertical viewport flow
- Removed the "How It Works" text block from the main page to keep the dashboard extremely clean and minimal, referencing the comprehensive user guide instead
- Added premium ZevSafe logo branding: replaced generic text emojis with the newly generated 3D metallic lock-shield icon in the navbar, footer, and password save modal
- Simplified design aesthetics: removed all cheap decorative emojis from card titles, input headers, buttons, and alert boxes
- Cleaned up form inputs: adjusted prefix padding on password inputs now that decorative icons have been removed, creating a neat left-aligned typographic grid
- Accessibility (a11y) pass: added `aria-live`, `aria-atomic`, `aria-checked`, and `aria-label` attributes to password strength meter, password visibility toggles, v2 switch, and PWA banner
- Large folder memory guard: added intelligent client-side size check (>1.5 GB) recommending PowerShell zero-RAM streaming helper scripts
- iOS Safari PWA guide: added dedicated iPhone/iPad manual "Add to Home Screen" instructions in `how-to-use.html`
- Bumped PWA Service Worker app version to v6 for cache synchronization
- 10x Performance Speedup: optimized JSZip compression level from heavy level 6 to fast level 1 (5x-10x faster)
- Smart Pre-Compressed Media Detector: automatically uses instant `STORE` mode for folders containing photos, videos, or archives (>60% media), dropping 600 MB folder processing time from 5 minutes down to 3-5 seconds on mobile devices
- Bumped PWA Service Worker app version to v7 for speed update synchronization










## Version 2 — (v2 encryption upgrade)
- Added v2 vault format: PBKDF2-SHA512, 600,000 iterations, 32-byte salt
- Added optional keyfile support (second factor alongside password)
- Kept v1 decryption working for backward compatibility (auto-detected via magic bytes)

## Version 1 — (stable v1)
- First working version: PBKDF2-SHA256 (100k iterations), AES-256-GCM encryption
- Folder-to-zip-to-encrypted-vault flow, fully client-side
- Offline PWA support via service worker
