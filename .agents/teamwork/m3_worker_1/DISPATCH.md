## 2026-10-06T09:48:16Z

You are M3 Worker 1 for Milestone 3: Full In-App Media & Document Viewer (Requirement R3).
Your working directory is: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m3_worker_1
Project root: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe

MANDATORY INPUTS:
- ORIGINAL_REQUEST.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\TEST_READY.md
- Survey 2 Report (Comprehensive Blueprint): c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\explorer_survey_2\handoff.md

EXCLUSIVE WRITE OWNERSHIP:
You own exclusively:
- `app/src/main/java/com/zevbuild/zevsafe/ui/viewer/VideoPlayerActivity.kt` (new file)
- `app/src/main/java/com/zevbuild/zevsafe/ui/components/AudioPlayerModal.kt` (new file)
- `app/src/main/java/com/zevbuild/zevsafe/ui/viewer/FileViewerHelper.kt` (new file)
- `app/src/main/AndroidManifest.xml` (register VideoPlayerActivity)
- `app/src/main/java/com/zevbuild/zevsafe/ui/screens/VaultBrowserScreen.kt` (routing media clicks to video activity, audio modal, preview modal, or external open-with; active buttons in modal)
- Documentation files: `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, `PROJECT-MEMORY/*.md`

DO NOT modify `CryptoEngine.kt` or test suites in this milestone.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

TASK OBJECTIVE:
1. Implement Fullscreen Video Player (`VideoPlayerActivity.kt`):
   - Package: `com.zevbuild.zevsafe.ui.viewer`
   - Edge-to-edge Compose activity (`ComponentActivity`) with `WindowCompat.setDecorFitsSystemWindows(window, false)`
   - Uses `androidx.media3.exoplayer.ExoPlayer` and `androidx.media3.ui.PlayerView`
   - Intent extras: `EXTRA_URI = "extra_uri"`, `EXTRA_NAME = "extra_name"`
   - Interactive auto-hiding controls overlay:
     - Top bar: Back navigation icon button, title text, format pill
     - Center controls: Rewind 10s (`Icons.Default.Replay10`), Play/Pause circular toggle, Fast-forward 10s (`Icons.Default.Forward10`)
     - Bottom controls: Current time (`mm:ss`), Compose Slider (`TealPrimary` track) bound to player position, Total duration (`mm:ss`), Mute/Volume toggle, Screen orientation toggle (switch portrait/landscape)
     - Tap anywhere on video surface toggles controls visibility; auto-hides after 3.5s of playback
     - Lifecycle management: pauses on `ON_PAUSE`, releases player on `onDestroy()`.
2. Implement Dedicated Cyber Audio Player (`AudioPlayerModal.kt`):
   - Package: `com.zevbuild.zevsafe.ui.components`
   - Dedicated Compose modal/dialog for audio playback (`audioEntry: DecryptedFileEntry, onDismiss: () -> Unit`)
   - Uses `ExoPlayer`
   - Cyber-dark UI design:
     - Glowing rotating album-art disc / vinyl with music note icon (`TealPrimary`/`PurplePrimary` border glow)
     - Animated Equalizer waveform bars (vertical bars that animate when `isPlaying` is true)
     - Track title, MIME format badge, file size
     - Interactive Compose `Slider` for seek position with left/right `mm:ss` timestamps
     - Transport Controls:
       - Looping toggle button (`Repeat` / `RepeatOne`)
       - Skip -10s (`Replay10`)
       - Large glowing Play/Pause button
       - Skip +10s (`Forward10`)
       - Playback speed toggle button (`1.0x` -> `1.5x` -> `2.0x` -> `1.0x`)
     - Coroutine ticker updating seekbar position every 250ms; cleans up and releases player on dismiss.
3. Implement File Viewer & Fallback Helper (`FileViewerHelper.kt`):
   - Package: `com.zevbuild.zevsafe.ui.viewer`
   - `openWithExternalApp(context: Context, entry: DecryptedFileEntry)`:
     - Resolves file from `entry.localFileUri`
     - Obtains content URI via `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`
     - Builds `Intent(Intent.ACTION_VIEW)` with `setDataAndType(contentUri, entry.mimeType.ifEmpty { "*/*" })`
     - Adds `FLAG_GRANT_READ_URI_PERMISSION` and `FLAG_ACTIVITY_NEW_TASK`
     - Launches `Intent.createChooser(intent, "Open ${entry.name} with...")`
     - Catches `ActivityNotFoundException` and gracefully falls back to `shareFile(context, entry)`
   - `shareFile(context: Context, entry: DecryptedFileEntry)` helper function.
4. Update `AndroidManifest.xml`:
   - Register `com.zevbuild.zevsafe.ui.viewer.VideoPlayerActivity`:
     `android:exported="false"`
     `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"`
     `android:theme="@style/Theme.ZevSafe"`
   - Ensure ZERO `android.permission.INTERNET` or network permissions.
5. Update `VaultBrowserScreen.kt`:
   - State: add `var selectedAudioFile by remember { mutableStateOf<DecryptedFileEntry?>(null) }`
   - In `FileItemRow.onClick`:
     - Tapping a video file (`video/*`) launches `VideoPlayerActivity`
     - Tapping an audio file (`audio/*`) sets `selectedAudioFile = extracted`
     - Tapping an image or text file sets `selectedPreviewFile = extracted`
     - Tapping an unsupported document (e.g. `.pdf`, `.docx`, binary) calls `FileViewerHelper.openWithExternalApp(context, extracted)`
   - Render `if (selectedAudioFile != null) AudioPlayerModal(selectedAudioFile!!) { selectedAudioFile = null }`
   - In `FilePreviewModal`, update the unsupported file dead-end box into an active card featuring:
     - File icon, name, MIME type, size
     - Primary button: `[ 🚀 Open in External App ]` calling `FileViewerHelper.openWithExternalApp(context, entry)`
     - Secondary button: `[ 📤 Share File ]` calling `FileViewerHelper.shareFile(context, entry)`
6. Run unit tests (`./gradlew testDebugUnitTest --no-daemon`) to ensure zero regressions across all 36 tests.
7. Update `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and all `PROJECT-MEMORY/*.md` files per mandatory repository rules.
8. Record your progress in `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m3_worker_1\progress.md` with timestamp.
9. Write your handoff report to `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m3_worker_1\handoff.md`.
Notify the orchestrator via send_message when done.
