# 🛡️ ZevSafe Android v6.4.2 (Build 33)

### 🚀 Highlights & Ecosystem Alignment with `zevsafe.pages.dev`
- **Frosted Cryptographic Trust Strip**: Added trust indicator pill bar (`AES-256-GCM`, `600k PBKDF2`, `Air-Gapped`) directly under top header on the home screen, directly matching the design of the official web portal [`zevsafe.pages.dev`](https://zevsafe.pages.dev).
- **One-Tap Strong Key Generator**: Instant 16-character cryptographic password generation using `SecureRandom` in `EncryptScreen`, auto-populating confirm password fields and copying to system clipboard.
- **Category Filter Chips in Vault Explorer**: Added category filtering (`All`, `Videos`, `Audio`, `Images`, `Docs`, `Archives`) with active item count badges and MIME color-coding in `VaultBrowserScreen`, mirroring the web portal's explorer.
- **Multi-Mode File Sorting**: Added one-tap sorting across active vaults by Name (A-Z, Z-A) and Size (Largest, Smallest).
- **Official Web Portal & Cross-Platform Card**: Added dedicated card in `SecurityGuideScreen` detailing seamless bit-identical `ZV3\0` STREAM AEAD interoperability with [`zevsafe.pages.dev`](https://zevsafe.pages.dev) and one-tap link copying.
- **100% Offline Air-Gapped Security**: Zero `android.permission.INTERNET`, zero telemetry SDKs, memory strictly bounded < 150 MB RAM across multi-GB archives.

---

### 📦 Release Assets
- **Release APK**: [`ZevSafe-release.apk`](https://github.com/zevbuild/app-zevsafe/releases/download/v6.4.2/ZevSafe-release.apk)
- **Debug APK**: [`ZevSafe-debug.apk`](https://github.com/zevbuild/app-zevsafe/releases/download/v6.4.2/ZevSafe-debug.apk)
