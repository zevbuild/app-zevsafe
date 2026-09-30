package com.zevbuild.zevsafe.crypto

import android.net.Uri

enum class VaultVersion(val displayName: String, val tag: String) {
    V1("v1 Standard", "PBKDF2-SHA256 · 100k iters · 16B Salt"),
    V2("v2 Standard", "PBKDF2-SHA512 · 600k iters · 32B Salt + Keyfile 2FA"),
    V3("v3 Streaming", "PBKDF2-SHA512 · 600k iters · 4MB Chunked AES-GCM (ZV3\\0)")
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
    val ciphertextOffset: Int,
    val chunkSize: Int = 4 * 1024 * 1024,
    val baseIVPrefix: ByteArray? = null,
    val manifestOffset: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultHeader
        if (version != other.version) return false
        if (flags != other.flags) return false
        if (requiresKeyfile != other.requiresKeyfile) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (ciphertextOffset != other.ciphertextOffset) return false
        if (chunkSize != other.chunkSize) return false
        if (manifestOffset != other.manifestOffset) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + flags
        result = 31 * result + requiresKeyfile.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertextOffset
        result = 31 * result + chunkSize
        result = 31 * result + manifestOffset.hashCode()
        return result
    }
}

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
    val textContentPreview: String? = null,
    val localHeaderOffset: Long = 0L,
    val compressedSize: Long = 0L,
    val isCompressed: Boolean = false,
    val crc32: Long = 0L,
    val chunkStart: Int = 0,
    val chunkEnd: Int = 0
)

data class VaultCatalog(
    val version: Int,
    val totalSize: Long,
    val fileCount: Int,
    val files: List<DecryptedFileEntry>
)

data class DecryptionResult(
    val vaultName: String,
    val outputZipUri: Uri?,
    val files: List<DecryptedFileEntry>,
    val totalSizeBytes: Long,
    val version: VaultVersion
)
