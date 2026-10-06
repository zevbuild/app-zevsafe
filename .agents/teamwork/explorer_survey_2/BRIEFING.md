# BRIEFING — 2026-10-05T12:35:00Z

## Mission
Conduct a detailed code-level exploration of Requirement R3 (Full in-app media & document viewer) in ZevSafe Android.

## 🔒 My Identity
- Archetype: explorer
- Roles: Media & Viewer Specialist (Survey Explorer 2)
- Working directory: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\explorer_survey_2
- Original parent: 7ef9a2be-df5e-4270-93b2-5a924aa5da45
- Milestone: Requirement R3 Survey (Full in-app media & document viewer)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- 100% Air-Gapped Offline: ZERO android.permission.INTERNET
- Zero-Knowledge Principle: Passwords and keys must never touch non-volatile disk unencrypted
- Memory Bounding: Streaming pipeline must stay bounded (< 150 MB RAM)
- Dynamic FileProvider Authority: "${context.packageName}.fileprovider"
- Output only to .agents/teamwork/explorer_survey_2/

## Current Parent
- Conversation ID: 7ef9a2be-df5e-4270-93b2-5a924aa5da45
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `app/src/main/java/com/zevbuild/zevsafe/ui/screens/VaultBrowserScreen.kt` (lines 98-106, 201-235, 319-438)
  - `app/src/main/java/com/zevbuild/zevsafe/viewmodel/VaultViewModel.kt` (lines 829-871)
  - `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt` (lines 645-675, 810-825)
  - `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoModels.kt` (lines 119-148)
  - `app/build.gradle.kts` (lines 58-78)
  - `gradle/libs.versions.toml` (lines 15, 33-34)
  - `app/src/main/AndroidManifest.xml` (lines 1-64)
  - `app/src/main/res/xml/file_paths.xml` (lines 1-8)
  - `app/src/main/java/com/zevbuild/zevsafe/MainActivity.kt` & `HomeScreen.kt`
- **Key findings**:
  - Video in `FilePreviewModal` is constrained to a fixed 280dp Box within a Dialog; no fullscreen, no landscape mode, no controls for volume/fullscreen toggle.
  - Audio files are fed to `androidx.media3.ui.PlayerView`, causing a black video surface (SurfaceView has 0 frames); lacks album art, waveform, title, duration, and loop controls.
  - PDF & binary documents fall into a static "Binary File" state with zero actions/buttons. FileItemRow only offers `ACTION_SEND` (sharing), not `ACTION_VIEW` ("Open with").
  - `androidx.media3:media3-exoplayer:1.5.1` and `androidx.media3:media3-ui:1.5.1` are already in `build.gradle.kts` and ready to use. Zero network dependencies needed.
  - `ExoPlayer` in Compose currently lacks `LifecycleEventObserver` (does not pause on app backgrounding).
  - `FileProvider` is correctly configured with `${applicationId}.fileprovider` and dynamic authority in code. Extracted files are strictly in `context.filesDir` (zero network, air-gapped).
- **Unexplored areas**: None for R3 scope.

## Key Decisions Made
- Architecture blueprint designed:
  1. Fullscreen Video via `VideoPlayerActivity` (or dedicated full-screen Composable) with full orientation and cyber-dark media controls.
  2. Dedicated Cyber-Dark Audio Player Composable with album-art disc, waveform visualization, timestamps, +/-10s seek, play/pause, and loop toggle.
  3. External "Open with" (ACTION_VIEW) and "Share" (ACTION_SEND) fallbacks using `FileProvider` (`${context.packageName}.fileprovider`).
  4. Full lifecycle management with `LifecycleEventObserver`.

## Artifact Index
- DISPATCH.md — Initial dispatch message
- BRIEFING.md — Persistent working memory
- progress.md — Liveness heartbeat and progress tracking
- handoff.md — Comprehensive survey findings and recommendations
