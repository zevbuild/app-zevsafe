# BRIEFING — 2026-10-05T12:43:00Z

## Mission
Conduct a detailed code-level investigation of Requirements R1 (CryptoEngine manifest size bug) and R2 (VaultViewModel extractSingleFileIfNeeded single-file preview).

## 🔒 My Identity
- Archetype: explorer
- Roles: Crypto & Extraction Specialist
- Working directory: c:\Users\Raju\Another-world\GitHub\zevbuild-studio\app-zevsafe\.agents\teamwork\explorer_survey_1
- Original parent: 7ef9a2be-df5e-4270-93b2-5a924aa5da45
- Milestone: Survey & Investigation (R1 & R2)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement / modify production or test files
- 100% offline, zero network / internet
- Zero-knowledge principle: Passwords/keys never touch disk unencrypted
- Memory bounds: streaming RAM < 150 MB RAM
- Dynamic FileProvider: "${context.packageName}.fileprovider"

## Current Parent
- Conversation ID: 7ef9a2be-df5e-4270-93b2-5a924aa5da45
- Updated: 2026-10-05T12:43:00Z

## Investigation State
- **Explored paths**:
  - `CryptoEngine.kt` (lines 442-596 `encryptItemsToV3Vault`, lines 344-440 `readV3VaultManifest`, lines 598-674 `decryptV3VaultToDirectory`)
  - `CryptoModels.kt` (`DecryptedFileEntry`, `VaultCatalog`, `VaultHeader`, `SelectedItem`)
  - `VaultViewModel.kt` (lines 197-265 `resolveSelectedItem`, lines 585-628 `loadInstantCatalog`, lines 829-871 `extractSingleFileIfNeeded`)
  - `CryptoUnitTest.kt` (existing unit test suite)
  - `zevsafe/js/stream-unpacker.js` (reference web implementation of targeted chunk extraction)
- **Key findings**:
  - **R1 root cause confirmed**: `actualSize` is computed at line 528 in `encryptItemsToV3Vault` inside `if (isPrecompressed)` but discarded; line 562 uses `item.sizeBytes` in manifest JSON. If `item.sizeBytes` was 0, manifest records `size: 0, compressedSize: 0`. During decryption / extraction, length verification fails, chunkEnd is truncated, or 0 bytes extracted.
  - **R1 solution designed**: Lift measurement variables, accumulate stream bytes for both STORED and DEFLATED, record final measured size and compressed size in JSON manifest and totalSize in root.
  - **R2 root cause confirmed**: `extractSingleFileIfNeeded` in `VaultViewModel.kt` executes full `decryptV3VaultToDirectory` whenever `localFileUri` is null, writing all vault contents to a timestamped folder.
  - **R2 solution designed**: Implement targeted chunk extraction in `CryptoEngine.kt` (`extractSingleFileFromV3Vault`) decrypting only `chunkStart..chunkEnd` (< 15 MB RAM, < 100 ms runtime), with session caching in `VaultViewModel.kt` so full decryption happens at most once per session as fallback.
- **Unexplored areas**: None for R1/R2.

## Key Decisions Made
- Formulate precise, drop-in code recommendations for R1 and R2 with exact line numbers and logic chains.
- Provide comprehensive 5-component handoff report.

## Artifact Index
- `DISPATCH.md` — Inbound message record
- `progress.md` — Liveness heartbeat and milestone tracking
- `handoff.md` — 5-component handoff report
