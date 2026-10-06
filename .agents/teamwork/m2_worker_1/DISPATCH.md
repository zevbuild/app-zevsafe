## 2026-10-05T13:46:39Z
You are M2 Worker 1 for Milestone 2: Fast Single-File Extraction (R2).
Your working directory is: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m2_worker_1
Project root: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe

MANDATORY INPUTS:
- ORIGINAL_REQUEST.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\TEST_READY.md
- Survey 1 Report (Detailed Blueprint): c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\explorer_survey_1\handoff.md

EXCLUSIVE WRITE OWNERSHIP:
- `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt`
- `app/src/main/java/com/zevbuild/zevsafe/viewmodel/VaultViewModel.kt`
- `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`
- Documentation files: `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, `PROJECT-MEMORY/*.md`

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

TASK OBJECTIVE:
1. Implement targeted single-file extraction in `CryptoEngine.kt`:
   - Implement `extractSingleFileFromV3Vault` which decrypts ONLY the relevant chunk range (`chunkStart..chunkEnd`), seeks directly via `FileChannel` (or `InputStream.skip()`), parses the ZIP Local File Header at `relPos = entry.localHeaderOffset - (chunkStart * chunkSize)`, decompresses raw DEFLATE bytes if `isCompressed` using `Inflater(true)` (or copies raw STORED bytes), and writes to `outputFile`.
   - Also implement stream/file overloads for testability and JVM test support (`extractSingleFileFromV3Vault(inStream/file, ...)`).
   - Verify RAM consumption remains bounded < 150 MB (reading only target chunks).
2. Fix `extractSingleFileIfNeeded` in `VaultViewModel.kt`:
   - If `entry.localFileUri` is already populated and the file exists on disk, immediately return it.
   - For v3 vaults, invoke `CryptoEngine.extractSingleFileFromV3Vault` to extract only that specific file into `preview_cache_${safeVaultBase}/${entry.name}`.
   - If targeted extraction fails or for legacy v1/v2 vaults, use session directory caching (`fallbackExtractAll` to `extracted_${safeVaultBase}_session`) so full decryption runs at most ONCE per vault session, never on repeated previews.
   - Update `_decryptionResult` with the extracted entry.
3. Verify all existing and targeted extraction unit tests in `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt` pass 100%.
4. Run `./gradlew testDebugUnitTest --no-daemon` and report full results.
5. Update `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and all `PROJECT-MEMORY/*.md` files per mandatory repository rules.
6. Write your handoff report to `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m2_worker_1\handoff.md`.
Notify the orchestrator via send_message when done.
