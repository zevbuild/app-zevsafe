# 🧠 ZevSafe Android — Project Memory & Knowledge Base

> **Official Architecture, Cryptographic Engineering & System Knowledge Base**  
> **Repository:** `zevbuild/app-zevsafe`  
> **Package Identity:** `com.zevbuild.zevsafe`  
> **Release Version:** `v6.3.0` (Build `30`)

---

## 📌 Mission & Zero-Trust Core Philosophies

1. **100% Zero-Knowledge & Client-Side:**  
   The application intentionally omits `android.permission.INTERNET` from its `AndroidManifest.xml`. It possesses **no network stack, zero telemetry, zero analytics, and zero external API dependencies**. All cryptographic transformations occur strictly on local CPU cores.

2. **Bounded-Memory Streaming (< 150 MB Peak Heap):**  
   Mobile Android systems face aggressive OS Out-Of-Memory (OOM) killer thresholds when processing multi-GB video archives. ZevSafe processes data in **strictly bounded 4 MB chunks** using sequential streams (`V3DecryptedInputStream` and buffered sinks), guaranteeing peak memory usage remains under 150 MB regardless of whether a vault is 100 MB or 50 GB.

3. **Universal Cross-Platform Interoperability (`ZV3\0`):**  
   Vaults encrypted on Android can be seamlessly extracted, navigated, or streamed inside the browser at [zevsafe.pages.dev](https://zevsafe.pages.dev) or via the desktop PowerShell streaming tools, and vice versa. The binary container layout, nonces, key derivation, and AAD definitions are bit-identical across all implementations.

4. **Zero Disk Plaintext Leaks:**  
   During media streaming (via the in-app Cinema Player), decrypted video and audio bytes are fed directly into AndroidX Media3 (ExoPlayer) via in-memory stream abstractions. Plaintext is **never written to temporary staging files on disk or external flash**.

---

## 📚 Knowledge Base Index

| Document | Description |
|---|---|
| 🔐 [**Cryptographic Specification**](./CRYPTOGRAPHIC_SPECIFICATION.md) | Full binary breakdown of `ZV3\0` STREAM AEAD, 57-byte header, 42-byte AAD, PBKDF2-SHA512 (600k rounds), Keyfile 2FA, tail manifest parser, and tamper defense. |
| 🏗️ [**Architecture & System Components**](./ARCHITECTURE_AND_COMPONENTS.md) | Structural hierarchy: Jetpack Compose Material 3 UI, ViewModel state machines, Android 14 `VaultForegroundService`, Intent Filters, and SAF storage access. |
| ⚡ [**Streaming Pipeline & Memory Bounds**](./STREAMING_AND_MEMORY_BOUNDS.md) | Detailed analysis of bounded memory management, backpressure, chunk sequencing, and the `V3DecryptedInputStream` architecture. |
| 🚀 [**Build, Release & CI/CD Pipeline**](./BUILD_RELEASE_AND_CICD.md) | Gradle Kotlin DSL setup, compile/target SDK 35, GitHub Actions CI/CD (`.github/workflows/build-apk.yml`), signing configs, and automated release deployment. |
| 🛡️ [**Development & AI Guidelines**](./DEVELOPMENT_AND_AI_GUIDELINES.md) | Strict engineering rules, security constraints, coding conventions, regression testing protocols, and documentation sync requirements for AI assistants. |

---

## 🏷️ System & Release Identity

- **Application ID:** `com.zevbuild.zevsafe`
- **Current Version Code:** `30`
- **Current Version Name:** `6.3.0`
- **Minimum Android SDK:** Android 8.0 (API Level 26)
- **Target Android SDK:** Android 15 (API Level 35)
- **Compile Android SDK:** API Level 35
- **JVM Target:** Java 17
- **UI Toolkit:** 100% Jetpack Compose with Material 3 & Navigation
- **Crypto Engine:** BouncyCastle lightweight provider / Java Cryptography Architecture (JCA)
- **Media Engine:** AndroidX Media3 ExoPlayer 1.5.1
