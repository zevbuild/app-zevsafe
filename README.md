# 🔒 ZevSafe — Offline Folder Encryption Portal

> **by [zevbuild](https://github.com/zevbuild) · Encrypt and decrypt entire folders directly in your browser or native Android app — no server, no uploads, 100% private.**

[![Live Demo](https://img.shields.io/badge/Live%20Demo-Cloudflare%20Pages-8b5cf6?style=for-the-badge&logo=cloudflare)](https://zevsafe.pages.dev)
[![Android APK](https://img.shields.io/badge/Android%20APK-Download%20v6.3.0-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/zevbuild/app-zevsafe/releases/latest)
[![Build Android APK](https://github.com/zevbuild/app-zevsafe/actions/workflows/build-apk.yml/badge.svg)](https://github.com/zevbuild/app-zevsafe/actions/workflows/build-apk.yml)
[![Project Memory](https://img.shields.io/badge/Project%20Memory-Knowledge%20Base-8b5cf6?style=for-the-badge&logo=gitbook&logoColor=white)](PROJECT_MEMORY.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-10b981?style=for-the-badge)](LICENSE)
[![Security: AES-256-GCM](https://img.shields.io/badge/Security-AES--256--GCM-ef4444?style=for-the-badge)](#cryptography)
[![100% Offline](https://img.shields.io/badge/Mode-100%25%20Offline-f59e0b?style=for-the-badge)](#)

---

> 🟢 **Current stable release — v6.3.0 (`.zev` format):** native Android streaming edition.  
> PBKDF2-SHA512 · 600,000 iterations · 4 MB Chunked AES-256-GCM (`ZV3\0`) · Instant <100ms Vault Explorer · Embedded Media3 Cinema Player · Foreground Service · 100% Zero-Knowledge.  
> 🧠 **Comprehensive System Architecture & Technical Specifications:** See [**`PROJECT_MEMORY.md`**](PROJECT_MEMORY.md) & [**`PROJECT-MEMORY/`**](PROJECT-MEMORY/).

---

## ✨ What is ZevSafe for Android?

**ZevSafe** for Android is a dedicated, zero-trust, client-side encryption application built in **Native Kotlin & Jetpack Compose (Material 3)**. It encrypts and decrypts files, media, and entire folder trees into single portable encrypted vaults (`.zev`) with zero backend dependencies, zero telemetry, and zero tracking.

### 🚀 Key Features

| Feature | Details |
|---|---|
| 🔐 **v3 STREAM AEAD (`ZV3\0`)** | 4 MB chunked AES-256-GCM with per-chunk 12B counter IVs and 42B AAD container binding |
| 🛡️ **PBKDF2-SHA512 (600,000 iters)** | Military-grade key stretching with 32-byte CSPRNG random salt |
| 🗝️ **Keyfile 2FA (Physical 2nd Factor)** | SHA-256 fingerprint mixed into raw key bytes via bitwise XOR |
| ⚡ **Instant Vault Explorer (<100ms)** | Reads encrypted tail manifest trailer directly without buffering or decrypting the whole vault |
| 🎬 **Embedded Cinema Media Player** | Direct playback for decrypted video (`.mp4`, `.mov`, `.webm`, `.mkv`) and audio (`.mp3`, `.wav`, `.flac`) via AndroidX Media3 (ExoPlayer) with zero disk writes |
| 🔋 **Android Foreground Service** | Persistent notification (MB/s, ETA, progress bar) & WakeLock for 5 GB operations |
| 📂 **Android System Intents** | Full `.zev` file association (`ACTION_VIEW`) + Share Target (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) |
| 🔄 **Tri-Format Compatibility** | Auto-detects and seamlessly decrypts v1 Legacy, v2 Standard, and v3 Streaming vaults |
| 🌐 **100% Offline & Zero-Knowledge** | Zero network calls; passwords and keys never touch non-volatile disk unencrypted |
| 🤖 **Automated GitHub Actions CI/CD** | Builds debug and release APKs on every commit with automatic artifact and release publishing |


---

## 🔐 How It Works

### v1 — Standard Mode (default)
```
[Your Folder]
     │
     ▼
 Compress (ZIP / DEFLATE level 6)
     │
     ▼
 Generate: Salt (16 bytes) + IV (12 bytes)  ← cryptographically random
     │
     ▼
 PBKDF2(password, salt, 100k iterations, SHA-256) → 256-bit AES-GCM key
     │
     ▼
 AES-256-GCM Encrypt(zip bytes, key, iv) → ciphertext + auth tag
     │
     ▼
 Output file: [ Salt(16) | IV(12) | Ciphertext+Tag ] → yourfolder.zev
```

### v2 — Enhanced Mode (opt-in toggle)
```
[Your Folder]
     │
     ▼
 Compress (ZIP / DEFLATE level 6)
     │
     ▼
 Generate: Salt (32 bytes) + IV (12 bytes)  ← cryptographically random
     │
     ▼
 PBKDF2(password, salt, 600k iterations, SHA-512) → 256 raw key bytes
     │
     ▼ (if keyfile provided)
 SHA-256(keyfile) XOR rawKey  → mixed key bytes
     │
     ▼
 Import as AES-256-GCM key
     │
     ▼
 AES-256-GCM Encrypt → ciphertext + auth tag
     │
     ▼
 Output file: [ Magic(4) | Version(1) | Flags(1) | Salt(32) | IV(12) | Ciphertext+Tag ]
```

**Decryption** is fully automatic — ZevSafe detects the format by reading the 4-byte magic header (`ZV2\0`) and routes to the correct pipeline. v1 vaults always work in v2-capable builds.

---

## 🛡️ Cryptography

### v1 Parameters
| Parameter | Value |
|---|---|
| Cipher | AES-256-GCM |
| Key size | 256 bits |
| IV size | 96 bits (12 bytes) |
| Salt size | 128 bits (16 bytes) |
| KDF | PBKDF2-SHA256 |
| Iterations | 100,000 |
| Authentication | Built-in GCM tag — tamper-proof |
| Entropy source | `window.crypto.getRandomValues()` |

### v2 Parameters (Enhanced)
| Parameter | Value |
|---|---|
| Cipher | AES-256-GCM (same) |
| Key size | 256 bits (same) |
| IV size | 96 bits — 12 bytes (same) |
| Salt size | 256 bits — 32 bytes (2× larger) |
| KDF | PBKDF2-SHA512 |
| Iterations | 600,000 (6× stronger) |
| Second factor | Optional keyfile (SHA-256 XOR'd into key material) |
| Format | Magic header `ZV2\0` for auto-detection |

> **GCM (Galois/Counter Mode)** provides both **confidentiality AND integrity**. Any tampering with the vault file will cause decryption to fail — no silent data corruption possible.

---

## 📖 Usage Guide

### 🔐 Encrypt a Folder — v1 (Standard)
1. Open **ZevSafe** in your browser.
2. Drag & drop your folder into the **Encrypt Folder** panel (or click "Browse Folder").
3. Enter a strong password (8+ characters) and confirm it.
4. Click **Encrypt & Download** → downloads `yourfolder.zev`.

### 🚀 Encrypt a Folder — v2 (Enhanced)
1. Complete steps 1–3 above.
2. Toggle **Enhanced Security Mode (v2)** — the options panel expands.
3. Optionally select a **Keyfile** (any file — photo, document, random binary).
4. Click **Encrypt & Download** → downloads `yourfolder.zev` (v2 format).
   > ⚠️ If you used a keyfile, keep it. Without it, the vault **cannot be decrypted** — even with the correct password.

### 🔓 Decrypt a Vault
1. Open **ZevSafe**.
2. Drag & drop your `.zev` file into the **Decrypt** panel (or click "Select .zev File").
3. If the vault is v2 with a keyfile, click **Select Keyfile** on the decrypt side.
4. Enter your password.
5. Click **Decrypt & Download** → downloads `yourfolder_decrypted.zip`.
6. Extract the ZIP to restore your original files.

> **Format is auto-detected.** You do not need to manually select v1 or v2 mode when decrypting.

---

## 📲 PWA — Install as an App

ZevSafe is a fully installable **Progressive Web App (PWA)**. Once installed, it runs in standalone mode (like a native app) and works fully offline.

### Desktop (Chrome / Edge)
1. Visit [zevsafe.pages.dev](https://zevsafe.pages.dev)
2. Click the install icon (➕) in the address bar, or look for the banner.
3. Click **Install**. ZevSafe appears in your app launcher.

### Android (Chrome)
1. Visit the site — an **"Install ZevSafe"** banner appears at the bottom.
2. Tap **Install** → added to your home screen.

### iOS (Safari)
1. Tap the **Share** button (□↑).
2. Select **"Add to Home Screen"**.
3. Tap **Add** → ZevSafe icon appears on your home screen.

Once installed, the app works **100% offline** — no internet required for encryption or decryption.

---

## 🤖 Android Native App & Automated APK Builds

In addition to the web portal and PWA, ZevSafe includes a **native Android application** built with **Kotlin** and **Jetpack Compose** in the [`app/`](app/) directory. It features identical cryptographic specifications and full `.zev` v1 and v2 format compatibility.

### ⬇️ Download the Latest APK
Every push to `main` automatically builds and signs the latest APK via GitHub Actions:
1. Open the [**GitHub Actions — Build Android APK**](https://github.com/zevbuild/app-zevsafe/actions/workflows/build-apk.yml) page.
2. Click on the latest workflow run marked with a green checkmark (✓).
3. Scroll down to the **Artifacts** section at the bottom.
4. Download **`ZevSafe-Android-APKs`** (extract the ZIP to get `ZevSafe-release.apk` and `ZevSafe-debug.apk`).
5. Open the `.apk` on your Android device to install.

### ⚙️ Automated CI/CD Workflow
The automated workflow ([`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)):
- Triggers on push to `main`, pull requests, version tags (`v*`), or manual execution (`workflow_dispatch`).
- Configures JDK 17, Android SDK tools, and Gradle 8.10.2.
- Runs cryptographic verification unit tests (`CryptoUnitTest`).
- Assembles both signed `debug` and `release` APKs.
- Automatically attaches the `.apk` binaries to a GitHub Release whenever a version tag (`v*`) is pushed.

### 🛠️ Building Android App Locally
```bash
# Build debug APK (signed with debug keystore, ready for installation)
gradle assembleDebug

# Build release APK
gradle assembleRelease

# Generated APKs are placed in:
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk
```

---

## ❓ FAQ — File Size Limits

**Q: How large can files be?**

ZevSafe works entirely in your browser's RAM. The practical limits are:

| Device | Safe Limit |
|--------|-----------|
| Desktop (8–16 GB RAM) | Up to ~1–2 GB per vault |
| Mid-range laptop | Up to ~500 MB per vault |
| Mobile / tablet | Up to ~100–300 MB per vault |

The bottleneck is the JavaScript heap limit, not network speed (there is no network). Compressing 500 MB of files may produce a vault of 200–480 MB depending on content type.

**Q: What if my file is too large?**

Split your folder into smaller sub-folders and encrypt each separately. Or use the PowerShell scripts (`encrypt.ps1` / `decrypt.ps1`) included in the repo for streaming large files with no memory limit.

---

## 🗂️ Project Structure

```text
app-zevsafe/
├── .github/
│   └── workflows/
│       └── build-apk.yml          # Automated CI/CD workflow building release & debug APKs
├── app/                           # Native Android app module (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/zevbuild/zevsafe/
│   │   ├── crypto/                # CryptoEngine.kt & CryptoModels.kt (v3 STREAM AEAD)
│   │   ├── service/               # VaultForegroundService.kt (Android 14 dataSync)
│   │   ├── ui/                    # Screens (HomeScreen, Encrypt, Decrypt, VaultBrowser) & components
│   │   ├── viewmodel/             # VaultViewModel.kt (MVVM, MediaStore.Downloads auto-saving)
│   │   └── MainActivity.kt        # Single activity, Material 3, intent handling
│   ├── src/main/res/              # Adaptive mipmaps, colors, strings, themes, file_paths.xml
│   ├── src/test/java/             # CryptoUnitTest.kt (100% cryptographic unit test suite)
│   ├── build.gradle.kts           # Android module build configuration (compileSdk 35)
│   └── proguard-rules.pro         # ProGuard code shrinking & optimization rules
├── PROJECT_MEMORY.md              # System Memory & architectural technical reference
├── PROJECT-MEMORY/                # Comprehensive modular engineering documentation
│   ├── ARCHITECTURE_AND_COMPONENTS.md
│   ├── BUILD_RELEASE_AND_CICD.md
│   ├── CRYPTOGRAPHIC_SPECIFICATION.md
│   ├── DEVELOPMENT_AND_AI_GUIDELINES.md
│   ├── README.md
│   └── STREAMING_AND_MEMORY_BOUNDS.md
├── docs/                          # Internal task tracker and project indices
│   ├── README.md                  # Documentation index
│   └── task.md                    # Active development task tracker
├── tools/                         # Zero-RAM Windows PC streaming tools (25 GB - 100 GB+)
│   ├── README.md                  # Usage guide
│   ├── Encrypt-Vault.bat          # 1-click drag & drop batch folder encryptor
│   ├── Decrypt-Vault.bat          # 1-click drag & drop batch vault decryptor
│   ├── encrypt.ps1                # PowerShell streaming encryption engine
│   └── decrypt.ps1                # PowerShell streaming decryption engine
├── build.gradle.kts               # Root Gradle build configuration
├── settings.gradle.kts            # Gradle settings & dependency repositories
├── gradle.properties              # JVM memory & AndroidX configuration
├── gradle/
│   ├── libs.versions.toml         # Version catalog (AGP 8.2.2, Compose, Kotlin 1.9.22)
│   └── wrapper/                   # Gradle wrapper configuration
├── assets/                        # High-efficiency brand assets & icons (PNG, WebP, SVG)
│   ├── README.md                  # Asset directory documentation
│   ├── icon-192.png / .webp       # Compressed app launcher icons
│   ├── icon-512.png / .webp       # Compressed app launcher icons
│   ├── zevsafe-logo.png / .webp   # Modern corporate business brand identity (512×512)
│   ├── cyber-vault.png / .webp    # Cyber vault graphics
│   └── favicon.svg                # Vector brand favicon
├── CHANGELOG.md                   # Release history and milestone documentation
└── archive/
    └── android-native-planning/   # Historical native planning notes
```

---

## 💻 Run Locally

No build step required for the web app — pure HTML/JS/CSS:

```bash
git clone https://github.com/zevbuild/app-zevsafe.git
cd app-zevsafe

# Just open index.html in your browser:
start index.html      # Windows
open index.html       # macOS
xdg-open index.html   # Linux
```

Or serve with a local HTTP server (required for Service Worker):
```bash
npx serve .
# or
python -m http.server 8080
```
> ⚠️ Service Workers only work over HTTPS or `localhost`. Use a local server to test PWA install and offline caching.

---

## ⚠️ Security Notes

- **Password strength matters.** Use a long passphrase (16+ characters). v2 mode is significantly stronger for the same password due to 6× more KDF iterations.
- **No password recovery.** There is no backdoor, no reset. Lose your password → vault is permanently unrecoverable.
- **Keyfile loss = vault loss.** If you encrypted with a keyfile in v2 mode, that exact file is required for decryption. Store it separately from the vault.
- **Memory safety.** All crypto runs in the browser's native sandbox. Decrypted data exists only in RAM and is never written to disk until you choose to download.
- **Service Worker never caches `.zev` files** or blob download URLs — decrypted output cannot be captured by the cache layer.
- **Verify the source.** Always use ZevSafe from the official Cloudflare Pages URL ([zevsafe.pages.dev](https://zevsafe.pages.dev)) or a locally cloned copy you trust.

---

## 👤 About

Built by **[zevbuild](https://github.com/zevbuild)** — crafting offline-first, privacy-first tools.

---

## 📜 License

MIT License — free to use, modify, and distribute.

---

*ZevSafe — Secure your data. Trust no one. Not even us.*