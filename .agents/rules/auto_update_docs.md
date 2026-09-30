---
trigger: always_on
---

# 🤖 Mandatory AI Rule: Automatic Project Memory, Changelog & README Updates

Whenever ANY AI agent modifies, adds, refactors, or deletes code/files in `app-zevsafe`:

1. **Always Update Project Memory (`PROJECT_MEMORY.md` & `PROJECT-MEMORY/*.md`):** Keep the executive summary, quick-reference table, and modular documentation (`CRYPTOGRAPHIC_SPECIFICATION.md`, `ARCHITECTURE_AND_COMPONENTS.md`, `STREAMING_AND_MEMORY_BOUNDS.md`, `BUILD_RELEASE_AND_CICD.md`, `DEVELOPMENT_AND_AI_GUIDELINES.md`) 100% synchronized with the codebase so any AI can understand the project immediately.
2. **Update `CHANGELOG.md`:** Document the change under the active/new version with categorized tags (`New`, `UI`, `Security`, `Perf`, `Fix`, `Tests`, `Docs`).
3. **Update `README.md`:** Synchronize version badges, release descriptions, and feature tables.
4. **Run Tests:** Ensure `./gradlew test` passes with 100%.
