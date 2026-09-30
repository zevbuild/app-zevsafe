# 🤖 Gemini / Antigravity Agent Guidelines for ZevSafe Android (`app-zevsafe`)

> **CRITICAL RULE FOR ALL AI AGENTS (Antigravity, Gemini, Cursor, Windsurf, Claude, Copilot, etc.)**:
> Whenever you modify, add, refactor, or delete ANY file or code in this repository, you **MUST AUTOMATICALLY UPDATE `PROJECT_MEMORY.md`, `PROJECT-MEMORY/*.md`, `CHANGELOG.md`, AND `README.md` BEFORE COMPLETING YOUR TASK.**
> Never finish a task or prompt without completing this Project Memory and documentation sync so that any future AI agent can immediately understand the exact state of the project.

---

## 📋 Mandatory Rules When Changing Anything

### 1. Always Read & Update Project Memory (`PROJECT_MEMORY.md` & `PROJECT-MEMORY/`)
- Before making architectural or cryptographic changes, consult [`PROJECT_MEMORY.md`](./PROJECT_MEMORY.md) and the modular docs in [`PROJECT-MEMORY/`](./PROJECT-MEMORY/):
  - [`PROJECT-MEMORY/CRYPTOGRAPHIC_SPECIFICATION.md`](./PROJECT-MEMORY/CRYPTOGRAPHIC_SPECIFICATION.md)
  - [`PROJECT-MEMORY/ARCHITECTURE_AND_COMPONENTS.md`](./PROJECT-MEMORY/ARCHITECTURE_AND_COMPONENTS.md)
  - [`PROJECT-MEMORY/STREAMING_AND_MEMORY_BOUNDS.md`](./PROJECT-MEMORY/STREAMING_AND_MEMORY_BOUNDS.md)
  - [`PROJECT-MEMORY/BUILD_RELEASE_AND_CICD.md`](./PROJECT-MEMORY/BUILD_RELEASE_AND_CICD.md)
  - [`PROJECT-MEMORY/DEVELOPMENT_AND_AI_GUIDELINES.md`](./PROJECT-MEMORY/DEVELOPMENT_AND_AI_GUIDELINES.md)
- Whenever you change any component, service, intent, `FileProvider`, UI screen, ViewModel method, or build setting, **immediately update `PROJECT_MEMORY.md` and the corresponding `PROJECT-MEMORY/*.md` file**.

### 2. Update `CHANGELOG.md` & `README.md`
- Add a clear entry under the active version section in `CHANGELOG.md` using tags (`New`, `UI`, `Security`, `Perf`, `Fix`, `Tests`, `Docs`).
- Keep version numbers, badges, and feature tables in `README.md` synchronized.

### 3. Verify Unit Tests
- Always run the automated unit tests before committing:
  ```bash
  ./gradlew test
  ```
- Ensure 100% of tests pass.

---

## 🔒 Security & Architectural Non-Negotiables
- **100% Air-Gapped Offline:** NEVER add `android.permission.INTERNET` or any network/telemetry SDK.
- **Zero-Knowledge Principle:** Passwords and keys must never touch non-volatile disk unencrypted.
- **Memory Bounding:** Streaming pipeline must stay bounded (< 150 MB RAM) across multi-GB files using 4 MB `ZV3\0` STREAM AEAD chunks.
- **Dynamic FileProvider Authority:** Always use `${applicationId}.fileprovider` in `AndroidManifest.xml` and `"${context.packageName}.fileprovider"` in Kotlin code.
