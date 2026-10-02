# 🧠 ZevSafe Android — Project Memory

> **System Memory, Technical Reference & Architectural Guidelines for `app-zevsafe`**  
> **Package Identity:** `com.zevbuild.zevsafe`  
> **Release Version:** `v6.3.0` (Build `30`) · **Target SDK:** 35 · **Min SDK:** 26

> **🤖 MANDATORY AI AGENT RULE:**  
> Whenever **ANY** AI agent (Antigravity, Gemini, Cursor, Windsurf, Claude, Copilot) modifies, adds, refactors, or fixes any code or configuration in this repository, you **MUST AUTOMATICALLY UPDATE `PROJECT_MEMORY.md` AND THE RELEVANT FILES IN `PROJECT-MEMORY/`** (alongside `CHANGELOG.md` and `README.md`) before finishing your task. This ensures any future AI agent can immediately understand the exact current state of the project.

---

## 📌 Executive Summary

**`app-zevsafe`** is the official standalone, native Android application for the ZevSafe zero-knowledge file and folder encryption ecosystem. Built with 100% Kotlin and Jetpack Compose, it brings the complete military-grade **v3 STREAM AEAD (`ZV3\0`)** cipher pipeline to Android devices without requiring network access, server dependencies, or cloud services.

The application adheres to an air-gapped, zero-knowledge security model: **`android.permission.INTERNET` is omitted from `AndroidManifest.xml`**, ensuring mathematically guaranteed offline operation.

---

## 📑 Detailed Documentation Index (`PROJECT-MEMORY/`)

Comprehensive technical documentation is maintained under the [`PROJECT-MEMORY/`](./PROJECT-MEMORY/) directory:

1. 🔐 [**Cryptographic Specification (`ZV3\0`)**](./PROJECT-MEMORY/CRYPTOGRAPHIC_SPECIFICATION.md)  
   Complete binary specification: 57-byte container header, 600,000-round PBKDF2-SHA512 key derivation, Keyfile 2FA XOR mixing, 4 MB chunk STREAM AEAD, 12-byte counter IVs, 42-byte AAD binding, encrypted tail manifest, and backward compatibility with v2/v1.

2. 🏗️ [**Architecture & System Components**](./PROJECT-MEMORY/ARCHITECTURE_AND_COMPONENTS.md)  
   Layered architecture overview: Jetpack Compose Material 3 UI (`ui/`), ViewModel reactive state machine (`viewmodel/`), Android 14 `VaultForegroundService` (`service/`), `CryptoEngine` (`crypto/`), AndroidX Media3 ExoPlayer Cinema streaming player, `FileProvider` (`${applicationId}.fileprovider`), and automatic `MediaStore.Downloads` saving.

3. ⚡ [**Streaming Pipeline & Memory Bounds**](./PROJECT-MEMORY/STREAMING_AND_MEMORY_BOUNDS.md)  
   Memory bounding strategy keeping peak JVM heap under 150 MB across 5 GB+ datasets. Details why pull-based `V3DecryptedInputStream` was selected over asynchronous coroutine pipes to eliminate deadlocks and buffer overruns.

4. 🚀 [**Build, Release & CI/CD Pipeline**](./PROJECT-MEMORY/BUILD_RELEASE_AND_CICD.md)  
   Gradle Kotlin DSL configuration, AGP 8.2.2, compileSdk 35, ProGuard rules, local build commands (`./gradlew assembleRelease`), and automated GitHub Actions workflow (`.github/workflows/build-apk.yml`) for publishing tagged APK releases.

5. 🛡️ [**Development & AI Agent Guidelines**](./PROJECT-MEMORY/DEVELOPMENT_AND_AI_GUIDELINES.md)  
   Non-negotiable security constraints: zero network permissions, zero telemetry, bounded memory usage, no disk plaintext leaks, bit-identical cross-platform compatibility, unit test verification, and mandatory Project Memory + Changelog synchronization.

---

## 🔑 Core Technical Specifications Quick-Reference

| Property | Value |
|---|---|
| **Cipher** | AES-256-GCM (128-bit authentication tag) |
| **Streaming Mode** | STREAM AEAD in 4 MB chunks (4,194,304 bytes) |
| **Header Format** | 57-byte binary container (`ZV3\0`) |
| **KDF** | PBKDF2-HMAC-SHA512 (600,000 iterations, 32-byte salt) |
| **Two-Factor Auth** | Optional Keyfile: `DerivedKey ⊕ SHA-256(Keyfile)` |
| **AAD Length** | 42 bytes (Prefix, IV prefix, chunk index, chunk length, last-chunk flag, manifest offset, keyfile tag) |
| **Peak Heap Bound** | < 150 MB RAM across multi-GB archives |
| **Background Processing** | Android 14 `ForegroundService` with `dataSync` type & partial wake lock |
| **Media Player** | AndroidX Media3 (ExoPlayer 1.5.1) with in-memory `ByteArrayDataSource` streaming |
| **Output & Sharing** | Auto-saves `.zev` & `.zip` to `Downloads` (`MediaStore.Downloads`) + System Share Sheet via `FileProvider` (`${applicationId}.fileprovider`) |
| **System Intents** | `ACTION_VIEW` (.zev files), `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (share sheet) |
| **Brand Assets** | Structured `assets/` directory: quantized PNG + WebP (`icon-192`, `icon-512`, `zevsafe-logo`, `cyber-vault`) & minified `favicon.svg` (93.4% reduction: 2.24 MB → 148.9 KB) |
| **Network Stack** | Completely absent (`android.permission.INTERNET` not present) |
