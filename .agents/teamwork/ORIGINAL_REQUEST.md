# Original User Request

## 2026-10-05T12:28:00Z

Fix several concrete, code-reviewed bugs in the ZevSafe Android app (`app-zevsafe`), overhaul the UI, add proper in-app media playback and file viewers, and publish a new versioned GitHub Release with a bumped APK.

Working directory: `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe`
Integrity mode: development

---

## Context

The project is a 100% offline, air-gapped Android encryption app (Jetpack Compose, Kotlin) using a custom `ZV3\0` STREAM AEAD format (4 MB AES-256-GCM chunked vault).
Current version: `versionCode = 30`, `versionName = "6.3.0"`, latest git tag `v6.3.0` is 8 commits behind HEAD.
Dependencies already declared: `androidx.media3.exoplayer`, `androidx.media3.ui`, `coil-compose`.

**Non-negotiable constraints (must never be violated):**
- Zero `INTERNET` permission or network calls of any kind.
- Passwords and derived keys must never touch non-volatile storage unencrypted.
- Streaming RAM must remain bounded < 150 MB.
- `FileProvider` authority must always be `"${context.packageName}.fileprovider"`.

---

## Requirements

### R1. Fix the manifest size bug causing decryption corruption

In `CryptoEngine.kt → encryptItemsToV3Vault`, the manifest JSON entry records `"size": item.sizeBytes` (line ~562). However, for pre-compressed files (images, videos, etc.), `item.sizeBytes` may be 0 or stale because it came from an unreliable provider query. The CRC pass correctly computes `actualSize` from the stream, but this measured value is discarded. The manifest must record the stream-measured size so decryption can correctly verify extracted lengths. Fix this bug and verify that encrypting a file whose provider-reported size is 0 still produces a vault that decrypts cleanly.

### R2. Fix `extractSingleFileIfNeeded` — do not re-decrypt the entire vault for single-file preview

In `VaultViewModel.kt → extractSingleFileIfNeeded`, when a catalog entry has no local URI, the code decrypts the entire vault from scratch just to preview one file. This is unacceptably slow for large vaults. Implement targeted single-file extraction that decrypts only the relevant chunk range (using `localHeaderOffset`, `compressedSize`, `chunkStart`/`chunkEnd` from the catalog entry), or at minimum fast-path by caching extraction per vault session so the full decrypt only runs once per vault open.

### R3. Full in-app media and document viewer

The current `FilePreviewModal` in `VaultBrowserScreen.kt` has these gaps:
- Video player is fixed at 280dp in a dialog — no fullscreen, no landscape support.
- Audio files reuse the video `PlayerView`, which renders a blank black rectangle.
- PDFs and binary documents show "Binary File" with no action except export.

Implement:
- Full-screen video playback with media controls (play/pause, seekbar, time, volume, fullscreen toggle). The player should open in an activity or dedicated full-screen Composable, not a small dialog.
- A dedicated audio player UI with album-art placeholder, track name, waveform or progress bar, play/pause, seek, duration, and looping toggle.
- A "Open with" / "Share" fallback for unsupported types (PDF, office docs, etc.) using `FileProvider` to launch an external app.
- All viewers must work on files already extracted to `context.filesDir` (no network access).

### R4. UI / UX overhaul across Encrypt, Decrypt, and Vault Browser screens

The current screens are functional but need polish:
- Encrypt screen: improve the file selection dropzone, add animated progress pipeline stages, real-time speed display (MB/s), and clear success / error banners.
- Decrypt screen: improve the vault selection card, add animated unlock progress, per-stage status indicators, and clear vault format badge.
- Vault Browser screen: improve the file list with MIME-type color-coded icons, file type badges, tap-to-preview hint text, and a floating "Export All" action button.
- Apply consistent cyber-dark theme with glowing accent borders, glassmorphic card surfaces, and smooth transitions throughout.

### R5. Version bump, new release tag, and documentation sync

- Bump `versionCode` to 31 and `versionName` to `"6.4.0"` in `app/build.gradle.kts`.
- Create and push git tag `v6.4.0` pointing at HEAD.
- Update `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and all `PROJECT-MEMORY/*.md` files to reflect all changes made by this task.
- Verify that `.github/workflows/build-apk.yml` will trigger on tag `v6.4.0` (it already fires on `tags: ["v*"]`) and that the release APK is properly attached.

---

## Acceptance Criteria

### Crypto correctness
- [ ] Encrypt a test file whose ContentResolver-reported size is 0 bytes, then decrypt it — the extracted file must be byte-identical to the original.
- [ ] Decrypt a previously-created `v6.3.0` vault — must succeed without regression.
- [ ] Single-file preview extraction for a file inside a 50 MB vault completes in < 10 seconds (no full re-decrypt per preview).

### Media playback
- [ ] Tapping a `.mp4` or `.mkv` video file in the Vault Browser opens a full-screen player with visible play/pause, seekbar, and time display.
- [ ] Tapping an `.mp3` or `.flac` audio file shows a dedicated audio UI (not a blank video rectangle) with play/pause and seek controls.
- [ ] Tapping an unsupported file type (e.g., `.pdf`) triggers a system share/open-with intent rather than showing an empty state.

### UI consistency
- [ ] All three screens (Encrypt, Decrypt, Vault Browser) use the same cyber-dark card style, accent colors, and font hierarchy.
- [ ] Progress during encryption/decryption shows at least: stage name, percentage, and MB/s speed.

### Versioning & release
- [ ] `app/build.gradle.kts` shows `versionCode = 31` and `versionName = "6.4.0"`.
- [ ] Git tag `v6.4.0` exists and points at the commit that bumped the version.
- [ ] `CHANGELOG.md`, `README.md`, and `PROJECT_MEMORY.md` all reference version 6.4.0.

### Security non-regression
- [ ] `AndroidManifest.xml` still does **not** contain `android.permission.INTERNET`.
- [ ] No new dependencies that make network requests are added.


## Follow-up — 2026-10-06T09:09:11Z

Server restarted. Please resume execution: check the current state of the orchestrator and milestones (M1 was completed; M2 worker implemented targeted extraction in CryptoEngine.kt and VaultViewModel.kt), proceed through review/audit gates, and drive the remaining milestones:
- M2: Complete review, testing, and audit of fast single-file extraction.
- M3: Implement full-screen video player (VideoPlayerActivity/Screen with Media3 controls), dedicated audio player UI (AudioPlayerModal), and external file open-with fallback.
- M4: UI/UX overhaul across Encrypt, Decrypt, and Vault Browser screens (cyber-dark theme, glowing borders, speed MB/s display, type badges, floating Export All button).
- M5: Version bump to versionCode = 31, versionName = "6.4.0", git tag v6.4.0, and update CHANGELOG.md, README.md, PROJECT_MEMORY.md, and all PROJECT-MEMORY/*.md.
- Ensure all automated unit tests pass 100%.
