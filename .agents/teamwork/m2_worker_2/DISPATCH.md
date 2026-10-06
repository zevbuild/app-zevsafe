## 2026-10-06T09:13:44Z
You are M2 Worker 2 for Milestone 2: Fast Single-File Targeted Extraction (R2).
Your working directory is: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m2_worker_2
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
   - Implement `extractSingleFileFromV3Vault`:
     - Reads vault container header.
     - Derives key from password, salt, and optional keyfile hash.
     - Computes chunk range `chunkStart..chunkEnd` from `entry`.
     - Reads and decrypts ONLY those chunks using `FileChannel` seek (or `InputStream.skip()`) without reading preceding or succeeding chunks.
     - Merges decrypted chunk span.
     - Locates ZIP local file header at `relPos = entry.localHeaderOffset - (chunkStart * chunkSize)` to locate raw payload offset and length.
     - If `entry.isCompressed`, decompresses raw DEFLATE bytes using `java.util.zip.Inflater(true)`. If STORED, writes raw bytes.
     - Writes decompressed bytes to `outputFile` and returns updated `DecryptedFileEntry(localFileUri = Uri.fromFile(outputFile), sizeBytes = outputFile.length(), textContentPreview = ...)`.
   - Provide file/stream overloads for JVM unit tests (`extractSingleFileFromV3Vault(vaultFile, ...)` or `(inStream, ...)`).
   - Verify RAM consumption remains bounded < 150 MB (reading only target chunks).
2. Fix `extractSingleFileIfNeeded` in `VaultViewModel.kt`:
   - If `entry.localFileUri` exists on disk, immediately return it.
   - For v3 vaults, invoke `CryptoEngine.extractSingleFileFromV3Vault` to extract only that specific file into `File(context.filesDir, "preview_cache_${safeVaultBase}/${entry.name}")`.
   - If targeted extraction fails or for legacy v1/v2 vaults, use session directory caching (`sessionExtractionDir`) so full decryption runs at most ONCE per vault session, never on repeated previews.
   - Update `_decryptionResult` with the extracted entry.
3. Verify all existing and targeted extraction unit tests in `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt` pass 100%.
4. Run `./gradlew testDebugUnitTest --no-daemon` and report full results.
5. Update `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and all `PROJECT-MEMORY/*.md` files per mandatory repository rules.
6. Write your handoff report to `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m2_worker_2\handoff.md`.
Notify the orchestrator via send_message when done.


## 2026-10-06T09:20:35Z
**Context**: Milestone 2 Implementation
**Content**: Please proceed directly with implementing `extractSingleFileFromV3Vault` in `CryptoEngine.kt` and `extractSingleFileIfNeeded` in `VaultViewModel.kt` per the Survey 1 blueprint. Use file tools (view_file, replace_file_content) to make the code changes.
**Action**: Implement the targeted extraction and session caching logic, verify tests, and update documentation.
