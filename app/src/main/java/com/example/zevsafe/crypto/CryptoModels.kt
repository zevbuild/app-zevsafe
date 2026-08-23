package com.example.zevsafe.crypto

import android.net.Uri

enum class VaultVersion(val displayName: String, val tag: String) {
    V1("v1 Standard", "PBKDF2-SHA256 · 100k iters · 16B Salt"),
    V2("v2 Enhanced", "PBKDF2-SHA512 · 600k iters · 32B Salt + Keyfile 2FA")
}

data class KeyfileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val sha256Hex: String
)

data class SelectedItem(
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean = false
)

data class VaultHeader(
    val version: VaultVersion,
    val flags: Int,
    val requiresKeyfile: Boolean,
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertextOffset: Int
)

data class StageMetrics(
    val originalSizeBytes: Long = 0,
    val compressedSizeBytes: Long = 0,
    val compressionRatioPercent: Float = 0f,
    val compressTimeMs: Long = 0,
    val cryptoSizeBytes: Long = 0,
    val cryptoTimeMs: Long = 0,
    val totalTimeMs: Long = 0,
    val outputName: String = "",
    val outputSizeBytes: Long = 0,
    val authTagVerified: Boolean? = null,
    val versionString: String = ""
)

enum class StageStatus {
    IDLE, IN_PROGRESS, COMPLETED, FAILED
}

data class OperationProgressState(
    val isActive: Boolean = false,
    val isEncrypting: Boolean = true,
    val currentTitle: String = "",
    val overallPercent: Float = 0f,
    val compressStatus: StageStatus = StageStatus.IDLE,
    val compressPercent: Float = 0f,
    val cryptoStatus: StageStatus = StageStatus.IDLE,
    val cryptoPercent: Float = 0f,
    val saveStatus: StageStatus = StageStatus.IDLE,
    val savePercent: Float = 0f,
    val metrics: StageMetrics = StageMetrics(),
    val errorMessage: String? = null
)

data class LogEntry(
    val timestamp: String,
    val message: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO, SUCCESS, WARN, ERROR
}

data class PasswordRecoveryRecord(
    val folderName: String,
    val vaultFilename: String,
    val version: String,
    val password: String,
    val keyfileRequired: Boolean,
    val keyfileName: String = "",
    val keyfileFingerprint: String = "",
    val timestampFormatted: String = ""
)

data class DecryptedFileEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val mimeType: String,
    val localFileUri: Uri? = null,
    val textContentPreview: String? = null
)

data class DecryptionResult(
    val vaultName: String,
    val outputZipUri: Uri,
    val files: List<DecryptedFileEntry>,
    val totalSizeBytes: Long,
    val version: VaultVersion
)
