# 🛡️ ZevSafe Android v6.4.0 (Build 31)

### 🚀 Highlights & Major Features
- **Decryption Corruption Fix**: Measured exact stream byte lengths and calculated IEEE 802.3 CRC-32 for both STORED (precompressed) and DEFLATED files during streaming. Fixed zero-reported-size items (`SelectedItem.sizeBytes <= 0L`) in the manifest envelope.
- **Fast Single-File Targeted Extraction**: Decrypts only the specific AEAD chunk range (`chunkStart..chunkEnd`) containing the target file via direct `FileChannel` seeking (< 15 MB RAM, < 100 ms latency) without re-decrypting entire vaults.
- **In-App Cinema Video Player**: Fullscreen edge-to-edge video player activity (`VideoPlayerActivity`) with auto-hiding cyber-dark controls, ±10s seek buttons, timestamps, volume toggle, and rotation controls powered by Media3 ExoPlayer.
- **Cyber Audio Player Dialog**: Dedicated audio modal (`AudioPlayerModal`) featuring a rotating vinyl album-art disc, animated equalizer waveform, interactive seekbar, loop toggle, and 1.0x-2.0x playback speed selector.
- **Secure External Document Viewer**: `FileViewerHelper` open-with and share fallback using dynamic `FileProvider` authority `${applicationId}.fileprovider` with `FLAG_GRANT_READ_URI_PERMISSION` for PDFs, office files, and binary documents.
- **Cyber-Dark UI Overhaul**: Animated dropzones, glowing accent borders, live real-time MB/s throughput telemetry in `StageProgressTracker`, Vault Format Inspector badges, MIME color-coded file cards, and floating "Export All" action button.
- **100% Offline Air-Gapped Security**: Zero `android.permission.INTERNET`, zero telemetry SDKs, memory strictly bounded < 150 MB RAM across multi-GB archives.

---

### 📦 Release Assets
- **Release APK**: [`ZevSafe-release.apk`](https://github.com/zevbuild/app-zevsafe/releases/download/v6.4.0/ZevSafe-release.apk)
- **Debug APK**: [`ZevSafe-debug.apk`](https://github.com/zevbuild/app-zevsafe/releases/download/v6.4.0/ZevSafe-debug.apk)
