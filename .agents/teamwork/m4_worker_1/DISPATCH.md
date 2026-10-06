## 2026-10-06T10:16:48Z

You are M4 Worker 1 for Milestone 4: UI/UX Screen Overhaul (Requirement R4).
Your working directory is: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m4_worker_1
Project root: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe

MANDATORY INPUTS:
- ORIGINAL_REQUEST.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\TEST_READY.md
- Survey 3 Report (UI/UX Blueprint): c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\explorer_survey_3\handoff.md

EXCLUSIVE WRITE OWNERSHIP:
You own exclusively:
- `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoModels.kt`
- `app/src/main/java/com/zevbuild/zevsafe/viewmodel/VaultViewModel.kt`
- `app/src/main/java/com/zevbuild/zevsafe/ui/components/StageProgressTracker.kt`
- `app/src/main/java/com/zevbuild/zevsafe/ui/screens/EncryptScreen.kt`
- `app/src/main/java/com/zevbuild/zevsafe/ui/screens/DecryptScreen.kt`
- `app/src/main/java/com/zevbuild/zevsafe/ui/screens/VaultBrowserScreen.kt`
- Documentation files: `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, `PROJECT-MEMORY/*.md`

DO NOT modify `CryptoEngine.kt` or test suites in this milestone.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

TASK OBJECTIVE:
Implement the comprehensive UI/UX overhaul across all screens per Requirement R4:
1. `CryptoModels.kt`:
   - In `OperationProgressState`: add `val speedMBs: Double = 0.0`
2. `VaultViewModel.kt`:
   - In `executeEncryption`: track `cryptoStart` timestamp and compute `val mbPerSec = if (elapsed > 0) (processed / (1024.0 * 1024.0)) / elapsed else 0.0`. Set `speedMBs = mbPerSec` in `_progressState.update { ... }`.
   - In `executeDecryption`: compute `mbPerSec` in the streaming progress callback and set `speedMBs = mbPerSec` in `_progressState.update { ... }`.
3. `StageProgressTracker.kt`:
   - Enhance with cyber styling:
     - Live throughput speed gauge badge: show `String.format(Locale.US, "%.1f MB/s", progressState.speedMBs)` in a glowing pill badge when `progressState.speedMBs > 0`
     - Glowing accent borders on active stages
     - Percentage badge and smooth animated linear progress indicator
4. `EncryptScreen.kt`:
   - Animated glowing dropzone: glassmorphic card with gradient accent border (`Brush.linearGradient`)
   - Expandable selected files list: when multiple files are selected, provide an expandable list/card displaying each file's icon, name, formatted size, and remove/clear action
   - Persistent in-screen Success & Error banners:
     - Persistent Success banner card: "Vault Created Successfully" with formatted vault size, file count, and direct action buttons: `[ 📤 Share Vault ]`, `[ 📁 View in Downloads ]`, `[ 🚀 Explore Now ]`
     - Error banner card: prominent cyber alert card with error details and `[ 🔄 Try Again ]` action
5. `DecryptScreen.kt`:
   - Glowing vault selection card with cyber gradient border
   - Interactive Vault Format Inspector badge/card: for v3 vaults, prominently display badges for `v3 STREAM AEAD`, `4 MB Chunks`, `PBKDF2 600k`, and `2FA Keyfile Status` (or for legacy v1/v2, their respective parameters)
   - Real-time MB/s decryption speed gauge
   - Polished cyber success card with glowing checkmark, recovered items summary, and direct actions (`Browse Files` / `Export ZIP`)
6. `VaultBrowserScreen.kt`:
   - MIME color-coding in file list items (`FileItemRow`):
     - Rose (`Color(0xFFF43F5E)`) for Video (`video/*`)
     - Amber (`Color(0xFFF59E0B)`) for Audio (`audio/*`)
     - Teal (`Color(0xFF2DD4BF)`) for Images (`image/*`)
     - Sky Blue (`Color(0xFF38BDF8)`) for Documents (`text/*`, `application/pdf`, etc.)
     - Violet (`Color(0xFFA78BFA)`) for Archives (`application/zip`, `.zev`, etc.)
   - File extension tag badges (`MP4`, `MP3`, `PDF`, `PNG`, `TXT`, etc.)
   - "Tap to preview" / "Tap to play" subtitle hints
   - Floating "Export All" action button: an `ExtendedFloatingActionButton` anchored at the bottom-end of the screen with download/export icon and badge showing total file count
7. Run unit test suite (`./gradlew testDebugUnitTest --no-daemon`) to ensure zero regressions across all 36 tests.
8. Synchronize all documentation: `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and `PROJECT-MEMORY/*.md` files per mandatory repository rules.
9. Record progress in `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m4_worker_1\progress.md` with timestamp.
10. Write handoff report to `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m4_worker_1\handoff.md`.
Notify the orchestrator via send_message when done.
