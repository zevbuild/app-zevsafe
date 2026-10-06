## 2026-10-05T13:02:51Z
You are M1 Worker 1 for Milestone 1: Manifest Size Bug Fix in `CryptoEngine.kt` (R1).
Your working directory is: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m1_worker_1
Project root: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe

MANDATORY INPUTS:
- ORIGINAL_REQUEST.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\PROJECT.md
- M1 Explorer 1 Report (Fix Strategy): c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m1_explorer_1\handoff.md
- M1 Explorer 2 Report (Unit Test Design): c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m1_explorer_2\handoff.md
- M1 Explorer 3 Report (Compatibility & Regressions): c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m1_explorer_3\handoff.md

EXCLUSIVE WRITE OWNERSHIP:
You own exclusively:
- `app/src/main/java/com/zevbuild/zevsafe/crypto/CryptoEngine.kt`
- `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`
- Documentation files for mandatory sync: `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and `PROJECT-MEMORY/*.md`.
DO NOT modify UI or ViewModel files in this milestone.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

TASK OBJECTIVE:
1. Implement the manifest size fix in `CryptoEngine.kt`:
   - Support `inputStreamProvider` or stream-based overloads so pure JVM tests can supply streams directly without Android framework mocking errors.
   - For STORED entries (`isPrecompressed == true`): accurately measure uncompressed and compressed size and CRC-32 during pre-pass. Set `zipEntry.size = measuredUncompressedSize`, `zipEntry.compressedSize = measuredUncompressedSize`, `zipEntry.crc = crc.value`.
   - For DEFLATED entries: update CRC-32 on write pass. Capture `zipEntry.compressedSize` after `zipOut.closeEntry()`.
   - Set manifest JSON entry: `"size": finalSize`, `"uncompressedSize": finalSize`, `"compressedSize": finalCompSize`, `"localHeaderOffset": entryOffset`, `"compressed": !isPrecompressed`, `"crc32": crc.value`.
   - Track `totalMeasuredBytes` across all items, and set root manifest `"totalSize": if (totalMeasuredBytes > 0L) totalMeasuredBytes else totalSourceBytes`.
   - Ensure truly 0-byte files (0 actual bytes) write `"size": 0`, `"compressedSize": 0`, `"crc32": 0`.
2. Add comprehensive unit tests in `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt`:
   - Zero-reported-size STORED file test (`sizeBytes = 0L`, non-zero stream payload). Verify manifest sizes > 0, CRC != 0, and roundtrip decryption byte-identical match.
   - Zero-reported-size DEFLATED file test (`sizeBytes = 0L`, compressible text payload). Verify manifest sizes, compressed flag, CRC, and roundtrip byte-identical match.
   - Multi-file mixed test (STORED, DEFLATED, truly empty 0-byte file). Verify totalSize and roundtrip decryption.
   - Multi-chunk span test (> 4 MB payload, sizeBytes = 0L). Verify chunkEnd calculation and decryption.
3. Run tests using `./gradlew test` (or `./gradlew.bat testDebugUnitTest --no-daemon`) and verify 100% pass rate.
4. Update `CHANGELOG.md`, `README.md`, `PROJECT_MEMORY.md`, and `PROJECT-MEMORY/*.md` per mandatory repo rules.
5. Record your progress in `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m1_worker_1\progress.md` with timestamp.
6. Write your handoff report to `c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\m1_worker_1\handoff.md`.
Notify the orchestrator via send_message when done.
