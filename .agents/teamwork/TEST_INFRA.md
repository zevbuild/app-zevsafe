# E2E Test Infra: ZevSafe Android v6.4.0

## Test Philosophy
- Opaque-box and requirement-driven derived from `ORIGINAL_REQUEST.md`.
- No reliance on internal implementation details; exercises cryptographic invariants, streaming chunk bounds, manifest accuracy, single-file extraction performance, and air-gap network absence.
- Methodology: 4-Tier Testing Model (Feature Coverage, Boundary/Corner Cases, Cross-Feature Combinations, Real-World Workloads).

## Feature Inventory
| # | Feature | Source (Requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|----------------------|:------:|:------:|:------:|
| 1 | R1: Stream-Measured Manifest Size | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 2 | R1: 0-Byte Query Encryption/Decryption | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 3 | R2: Fast Single-File Extraction | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 4 | R2: Preview Session Caching | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 5 | R3: Media & Document Viewer Routing | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 6 | R3: Dynamic FileProvider & Air-Gap | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 7 | R4: Speed Telemetry & Model Fields | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| 8 | R5: Version Metadata & Release Tag | ORIGINAL_REQUEST §R5 | 5 | 5 | ✓ |

## Test Architecture
- Test Runner: JVM unit test execution via `./gradlew testDebugUnitTest --no-daemon`
- Acceptance criteria: 100% test pass rate, 0 failures, 0 errors
- Test File Location: `app/src/test/java/com/zevbuild/zevsafe/CryptoUnitTest.kt` and dedicated E2E test cases
- Air-gap validation: Static analysis of `AndroidManifest.xml` verifying absence of `android.permission.INTERNET` and no network dependencies in `app/build.gradle.kts`.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Multi-file vault containing 0-byte reported images, deflated texts, and binary files encrypts and round-trip decrypts with byte-exact match | F1, F2, F3 | High |
| 2 | Large multi-chunk vault (>50 MB) single-file extraction extracts file within bounds without decrypting unaffected chunks | F3, F4 | High |
| 3 | Legacy v6.3.0 vault decryption regression test | F1, F2 | Medium |
| 4 | Dynamic FileProvider authority URI generation and intent compatibility | F5, F6 | Medium |
| 5 | Offline integrity assurance: Static manifest inspection for zero network permissions | F6, F8 | Low |

## Coverage Thresholds
- Tier 1: ≥5 tests per core feature
- Tier 2: ≥5 boundary & corner tests per feature
- Tier 3: Pairwise combinations across encryption, single extraction, and preview
- Tier 4: ≥5 realistic end-to-end workload scenarios
