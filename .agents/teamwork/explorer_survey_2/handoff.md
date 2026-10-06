# Survey Report: Requirement R3 — Full In-App Media & Document Viewer

## 1. Observation

### 1.1 `FilePreviewModal` Current Rendering & Layout
In `app/src/main/java/com/zevbuild/zevsafe/ui/screens/VaultBrowserScreen.kt`:
- **Dialog Container & Fixed 280dp Box** (lines 323–373):
```kotlin
Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // ... Header ...
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
```
- **Video & Audio Branch Coupling** (lines 380–402):
```kotlin
} else if ((entry.mimeType.startsWith("video/") || entry.mimeType.startsWith("audio/")) && entry.localFileUri != null) {
    val playerContext = LocalContext.current
    val exoPlayer = remember(entry.localFileUri) {
        ExoPlayer.Builder(playerContext).build().apply {
            setMediaItem(MediaItem.fromUri(entry.localFileUri!!))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(entry.localFileUri) {
        onDispose {
            exoPlayer.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
```
- **PDF & Binary File Dead-End** (lines 414–435):
```kotlin
} else {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Binary File",
            fontSize = 13.sp,
            color = TextPrimary
        )
        Text(
            text = "Use export to open in external application",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}
```

### 1.2 `FileItemRow` Click & Share Actions
In `app/src/main/java/com/zevbuild/zevsafe/ui/screens/VaultBrowserScreen.kt` (lines 201–234):
- Tapping any non-directory row calls `viewModel.extractSingleFileIfNeeded(context, fileEntry) { extracted -> selectedPreviewFile = extracted }`.
- The row's share button launches `Intent(Intent.ACTION_SEND)` (share sheet), but there is **no** `Intent(Intent.ACTION_VIEW)` ("Open with" in viewer apps).
- Tapping an unsupported file (e.g., `.pdf`) opens `FilePreviewModal`, which displays the dead-end "Binary File" state with no action buttons.

### 1.3 Media Dependencies & ExoPlayer Setup
In `app/build.gradle.kts` (lines 72–76):
- `libs.coil.compose` (`2.7.0`) is declared.
- `libs.androidx.media3.exoplayer` (`1.5.1`) is declared.
- `libs.androidx.media3.ui` (`1.5.1`) is declared.
- In `gradle/libs.versions.toml`: `media3 = "1.5.1"`, `coilCompose = "2.7.0"`. No new library dependencies are required.

### 1.4 ExoPlayer Lifecycle in Compose
- `ExoPlayer` is managed via `remember(entry.localFileUri)` and `DisposableEffect(entry.localFileUri) { onDispose { exoPlayer.release() } }`.
- **Missing**:
  1. `LifecycleEventObserver` listening to `Lifecycle.Event.ON_PAUSE` / `ON_STOP` — playback continues in background if user switches apps.
  2. Audio focus management (`AudioAttributes.setUsage(C.USAGE_MEDIA)`).
  3. Interactive UI state bindings: Compose has no reactive state for `isPlaying`, `currentPosition`, `duration`, or `isLooping`.

### 1.5 File Storage & Air-Gapped Offline Verification
- In `app/src/main/java/com/zevbuild/zevsafe/viewmodel/VaultViewModel.kt` (lines 851–855):
  Extracted files are stored in `File(context.filesDir, "extracted_${safeVaultBase}_${System.currentTimeMillis()}")`.
- In `app/src/main/AndroidManifest.xml` (lines 4–7, 52–60):
  - No `android.permission.INTERNET` exists in the manifest.
  - FileProvider is defined with `android:authorities="${applicationId}.fileprovider"`.
- In `app/src/main/res/xml/file_paths.xml` (lines 1–7):
  - `<files-path name="vault_files" path="." />` covers `context.filesDir`.
- All media and file preview operations strictly operate on local file URIs (`Uri.fromFile(...)` or `file://...`).

---

## 2. Logic Chain

### 2.1 Video Playback Deficiencies
1. **Observation 1.1**: `FilePreviewModal` is hosted inside a `Dialog` with a fixed `Modifier.fillMaxWidth().height(280.dp)` Box container.
2. `PlayerView` inside this box is constrained to 280dp height.
3. Therefore, videos are forced into a small 280dp card on all screen sizes, portrait and landscape.
4. In landscape orientation, a 280dp dialog exceeds available screen height or leaves excessive margin, creating letterboxing and clipping.
5. `PlayerView` has no custom fullscreen button listener (`setFullscreenButtonClickListener`) or activity navigation.
6. **Inference**: To meet Acceptance Criterion R3 ("Tapping a `.mp4` or `.mkv` video file opens a full-screen player with visible play/pause, seekbar, and time display"), video playback must open a dedicated full-screen experience (`VideoPlayerActivity` or dedicated full-screen Composable), not a 280dp dialog Box.

### 2.2 Audio Player Black Rectangle Defect
1. **Observation 1.1**: Line 380 groups audio files with video files: `(entry.mimeType.startsWith("video/") || entry.mimeType.startsWith("audio/"))`.
2. Both feed into `androidx.media3.ui.PlayerView`.
3. `PlayerView` hosts a video `SurfaceView` / `TextureView`. Since audio streams provide zero video decoders or frames, the surface remains entirely black.
4. `PlayerView` default transport controls are designed for video and lack dedicated audio components:
   - No track title inside the playback surface.
   - No album-art / cyber-dark vinyl or audio disc placeholder.
   - No waveform visualization.
   - No looping toggle (`Player.REPEAT_MODE_ONE`).
   - No skip +/- 10s buttons.
5. **Inference**: Audio must be decoupled into a dedicated cyber-styled `AudioPlayerModal` or rich Compose audio card.

### 2.3 PDF & Document Viewer Dead-End
1. **Observation 1.1 & 1.2**: If `entry.textContentPreview == null` and MIME is not image/video/audio, the modal displays `InsertDriveFile` icon and "Binary File / Use export to open in external application".
2. There are no clickable buttons inside the modal. The user must dismiss the modal and use the row's share button.
3. Furthermore, the row share button uses `Intent.ACTION_SEND` (sharing/exporting), not `Intent.ACTION_VIEW` ("Open with" in an installed PDF viewer or document editor).
4. **Inference**:
   - Tapping an unsupported file (e.g. `.pdf`) in `VaultBrowserScreen` should directly trigger an "Open with" `Intent.ACTION_VIEW` chooser via `FileProvider`.
   - The modal fallback for binary files must also offer active buttons: `[ Open in External App ]` (`ACTION_VIEW`) and `[ Share File ]` (`ACTION_SEND`).

---

## 3. Caveats

1. **External App Dependency for PDF / Office Docs**:
   - Android has no built-in native PDF viewer Composable in Jetpack Compose without adding heavy libraries (like `android-pdf-viewer` or PdfRenderer bitmap rendering).
   - Using `FileProvider` with `Intent.ACTION_VIEW` allows external apps (Google Drive PDF, Adobe Reader, Microsoft 365, etc.) to view the file securely.
   - If a device has zero PDF viewers installed, `ActivityNotFoundException` must be caught and gracefully fall back to `ACTION_SEND` (Share sheet).
2. **Streaming Ram vs Extracted Media Size**:
   - Media playback operates on the single file extracted to `context.filesDir`. For multi-GB videos, single-file extraction will allocate disk space in internal cache/filesDir. Disk space checks and cleanup on vault close should be maintained.
3. **Orientation Handling**:
   - A dedicated `VideoPlayerActivity` handles orientation (`orientation|screenSize`) natively and cleanly without causing recomposition cascades across `MainActivity`.

---

## 4. Conclusion & Architectural Recommendation

Requirement R3 can be implemented cleanly using the following architecture without adding any new Gradle dependencies:

### 4.1 Component Blueprint

#### Component A: Fullscreen Video Player (`VideoPlayerActivity` + `VideoPlayerScreen`)
- **Location**: `app/src/main/java/com/zevbuild/zevsafe/ui/viewer/VideoPlayerActivity.kt`
- **Manifest Entry**:
  ```xml
  <activity
      android:name="com.zevbuild.zevsafe.ui.viewer.VideoPlayerActivity"
      android:exported="false"
      android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
      android:theme="@style/Theme.ZevSafe" />
  ```
- **Features**:
  1. Edge-to-edge immersive full screen (`WindowCompat.setDecorFitsSystemWindows(window, false)`).
  2. Auto-hiding cyber-dark controls overlay (fade out after 3.5s of playback; tap screen to toggle).
  3. Top Bar: Back button, file title, format pill.
  4. Center Controls: Rewind 10s (`Replay10`), large Play/Pause toggle, Fast-Forward 10s (`Forward10`).
  5. Bottom Controls: Current position (`mm:ss`), seek slider (`TealPrimary`), total duration (`mm:ss`), audio volume toggle, orientation toggle (portrait / landscape lock).
  6. Lifecycle-aware: Pauses on `ON_PAUSE`, releases player on `onDestroy()`.

#### Component B: Dedicated Cyber Audio Player (`AudioPlayerModal`)
- **Location**: `app/src/main/java/com/zevbuild/zevsafe/ui/components/AudioPlayerModal.kt`
- **UI Elements**:
  1. **Album-Art Placeholder**: Glowing cyber disc / vinyl with musical note icon (`TealPrimary` / `PurplePrimary` glow border).
  2. **Waveform / Equalizer Animation**: 12–16 animated vertical amplitude bars that bounce when audio `isPlaying` is true.
  3. **Track Metadata**: Track name (bold title), MIME format tag, file size.
  4. **Seekbar & Timestamps**: Interactive Compose `Slider`, left timestamp `currentPosition` (`00:00`), right timestamp `duration` (`00:00`).
  5. **Controls Row**:
     - Looping toggle button (`Repeat` / `RepeatOne`), updating `exoPlayer.repeatMode`.
     - Skip -10s (`Replay10`).
     - Play/Pause toggle (60dp circular button with glowing background).
     - Skip +10s (`Forward10`).
     - Playback Speed toggle (`1.0x`, `1.5x`, `2.0x`).
  6. **Lifecycle**: Smooth 250ms coroutine ticker for seekbar position while playing; releases player when dismissed.

#### Component C: Document & Unsupported File Fallback (`FileViewerHelper`)
- **Location**: Helper functions in `app/src/main/java/com/zevbuild/zevsafe/ui/viewer/FileViewerHelper.kt` (or within `VaultBrowserScreen.kt`):
  ```kotlin
  fun openWithExternalApp(context: Context, entry: DecryptedFileEntry) {
      val fileUri = entry.localFileUri ?: return
      val file = File(fileUri.path ?: "")
      if (!file.exists()) return
      val contentUri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          file
      )
      val viewIntent = Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(contentUri, entry.mimeType.ifEmpty { "*/*" })
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      val chooser = Intent.createChooser(viewIntent, "Open ${entry.name} with...")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
          context.startActivity(chooser)
      } catch (_: ActivityNotFoundException) {
          shareFile(context, entry)
      }
  }
  ```
- **Modal Fallback UI**:
  In `FilePreviewModal`, for unsupported types, display an active cyber card with:
  - File icon + Name + MIME type + Size.
  - Primary button: `[ 🚀 Open in External App ]` (`openWithExternalApp`).
  - Secondary button: `[ 📤 Share / Export File ]` (`shareFile`).

#### Component D: `VaultBrowserScreen` Routing
In `VaultBrowserScreen.kt` `FileItemRow.onClick`:
```kotlin
viewModel.extractSingleFileIfNeeded(context, fileEntry) { extracted ->
    when {
        extracted.mimeType.startsWith("video/") -> {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_URI, extracted.localFileUri.toString())
                putExtra(VideoPlayerActivity.EXTRA_NAME, extracted.name)
            }
            context.startActivity(intent)
        }
        extracted.mimeType.startsWith("audio/") -> {
            selectedAudioFile = extracted
        }
        extracted.mimeType.startsWith("image/") || extracted.mimeType.startsWith("text/") -> {
            selectedPreviewFile = extracted
        }
        else -> {
            // Unsupported file (PDF, docx, bin, etc.): trigger system open-with intent
            FileViewerHelper.openWithExternalApp(context, extracted)
        }
    }
}
```

---

## 5. Verification Method

1. **Codebase Inspection**:
   - Check `app/src/main/AndroidManifest.xml` has `VideoPlayerActivity` declared with `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"`.
   - Verify `FileProvider` authority remains `"${context.packageName}.fileprovider"` in all Kotlin code.
   - Verify zero occurrences of `android.permission.INTERNET` in `AndroidManifest.xml`.
2. **Media Playback Verification**:
   - Extract/open `.mp4` video: opens `VideoPlayerActivity` full-screen with play/pause, seekbar, time display, volume toggle, and rotation.
   - Extract/open `.mp3` audio: opens `AudioPlayerModal` with album art disc, dancing equalizer bars, seekbar, time, play/pause, and looping toggle.
   - Extract/open `.pdf`: triggers `Intent.ACTION_VIEW` system chooser with `FLAG_GRANT_READ_URI_PERMISSION`.
3. **Build & Unit Test Verification**:
   - Run: `./gradlew test` to ensure 100% test pass.
   - Run: `./gradlew assembleDebug` to ensure compilation succeeds with no syntax or packaging errors.
