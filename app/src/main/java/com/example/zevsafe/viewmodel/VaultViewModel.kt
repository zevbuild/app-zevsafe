package com.example.zevsafe.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zevsafe.crypto.CryptoEngine
import com.example.zevsafe.crypto.DecryptedFileEntry
import com.example.zevsafe.crypto.DecryptionResult
import com.example.zevsafe.crypto.KeyfileInfo
import com.example.zevsafe.crypto.LogEntry
import com.example.zevsafe.crypto.LogType
import com.example.zevsafe.crypto.OperationProgressState
import com.example.zevsafe.crypto.PasswordRecoveryRecord
import com.example.zevsafe.crypto.SelectedItem
import com.example.zevsafe.crypto.StageMetrics
import com.example.zevsafe.crypto.StageStatus
import com.example.zevsafe.crypto.VaultHeader
import com.example.zevsafe.crypto.VaultVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _progressState = MutableStateFlow(OperationProgressState())
    val progressState: StateFlow<OperationProgressState> = _progressState.asStateFlow()

    // Encrypt State
    private val _selectedEncryptItems = MutableStateFlow<List<SelectedItem>>(emptyList())
    val selectedEncryptItems: StateFlow<List<SelectedItem>> = _selectedEncryptItems.asStateFlow()

    private val _selectedEncryptFolderName = MutableStateFlow("")
    val selectedEncryptFolderName: StateFlow<String> = _selectedEncryptFolderName.asStateFlow()

    private val _encryptPassword = MutableStateFlow("")
    val encryptPassword: StateFlow<String> = _encryptPassword.asStateFlow()

    private val _encryptConfirmPassword = MutableStateFlow("")
    val encryptConfirmPassword: StateFlow<String> = _encryptConfirmPassword.asStateFlow()

    private val _useV2Encrypt = MutableStateFlow(false)
    val useV2Encrypt: StateFlow<Boolean> = _useV2Encrypt.asStateFlow()

    private val _encryptKeyfile = MutableStateFlow<KeyfileInfo?>(null)
    val encryptKeyfile: StateFlow<KeyfileInfo?> = _encryptKeyfile.asStateFlow()

    // Decrypt State
    private val _selectedDecryptVaultUri = MutableStateFlow<Uri?>(null)
    val selectedDecryptVaultUri: StateFlow<Uri?> = _selectedDecryptVaultUri.asStateFlow()

    private val _selectedDecryptVaultName = MutableStateFlow("")
    val selectedDecryptVaultName: StateFlow<String> = _selectedDecryptVaultName.asStateFlow()

    private val _selectedDecryptVaultSize = MutableStateFlow(0L)
    val selectedDecryptVaultSize: StateFlow<Long> = _selectedDecryptVaultSize.asStateFlow()

    private val _decryptPassword = MutableStateFlow("")
    val decryptPassword: StateFlow<String> = _decryptPassword.asStateFlow()

    private val _detectedHeader = MutableStateFlow<VaultHeader?>(null)
    val detectedHeader: StateFlow<VaultHeader?> = _detectedHeader.asStateFlow()

    private val _decryptKeyfile = MutableStateFlow<KeyfileInfo?>(null)
    val decryptKeyfile: StateFlow<KeyfileInfo?> = _decryptKeyfile.asStateFlow()

    // Result & Recovery State
    private val _lastRecoveryRecord = MutableStateFlow<PasswordRecoveryRecord?>(null)
    val lastRecoveryRecord: StateFlow<PasswordRecoveryRecord?> = _lastRecoveryRecord.asStateFlow()

    private val _showRecoveryDialog = MutableStateFlow(false)
    val showRecoveryDialog: StateFlow<Boolean> = _showRecoveryDialog.asStateFlow()

    private val _decryptionResult = MutableStateFlow<DecryptionResult?>(null)
    val decryptionResult: StateFlow<DecryptionResult?> = _decryptionResult.asStateFlow()

    init {
        addLog("ZevSafe security kernel initialized.", LogType.INFO)
        addLog("AES-256-GCM authenticated cipher loaded.", LogType.INFO)
    }

    fun addLog(message: String, type: LogType = LogType.INFO) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.update { it + LogEntry(timestamp = time, message = message, type = type) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // Encrypt setters
    fun setEncryptPassword(pw: String) { _encryptPassword.value = pw }
    fun setEncryptConfirmPassword(pw: String) { _encryptConfirmPassword.value = pw }
    fun setUseV2Encrypt(useV2: Boolean) { _useV2Encrypt.value = useV2 }

    fun setEncryptFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            val folderName = rootDoc.name ?: "secured_folder"
            val items = mutableListOf<SelectedItem>()

            fun traverse(dir: DocumentFile, currentPath: String) {
                val files = dir.listFiles()
                for (file in files) {
                    val relativePath = if (currentPath.isEmpty()) file.name ?: "file" else "$currentPath/${file.name}"
                    if (file.isDirectory) {
                        items.add(
                            SelectedItem(
                                uri = file.uri,
                                name = file.name ?: "dir",
                                relativePath = "$relativePath/",
                                sizeBytes = 0L,
                                isDirectory = true
                            )
                        )
                        traverse(file, relativePath)
                    } else {
                        items.add(
                            SelectedItem(
                                uri = file.uri,
                                name = file.name ?: "file",
                                relativePath = relativePath,
                                sizeBytes = file.length(),
                                isDirectory = false
                            )
                        )
                    }
                }
            }

            traverse(rootDoc, "")

            _selectedEncryptFolderName.value = folderName
            _selectedEncryptItems.value = items
            val totalBytes = items.sumOf { it.sizeBytes }
            addLog("Selected folder \"$folderName\" (${items.size} files, ${CryptoEngine.formatBytes(totalBytes)}).", LogType.INFO)
        }
    }

    fun setEncryptFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<SelectedItem>()
            for (uri in uris) {
                val doc = DocumentFile.fromSingleUri(context, uri) ?: continue
                val name = doc.name ?: "file_${items.size}"
                items.add(
                    SelectedItem(
                        uri = uri,
                        name = name,
                        relativePath = name,
                        sizeBytes = doc.length(),
                        isDirectory = false
                    )
                )
            }
            val folderName = if (items.size == 1) items[0].name.substringBeforeLast('.') else "secured_files"
            _selectedEncryptFolderName.value = folderName
            _selectedEncryptItems.value = items
            val totalBytes = items.sumOf { it.sizeBytes }
            addLog("Selected ${items.size} file(s) for vault packaging (${CryptoEngine.formatBytes(totalBytes)}).", LogType.INFO)
        }
    }

    fun clearEncryptSelection() {
        _selectedEncryptItems.value = emptyList()
        _selectedEncryptFolderName.value = ""
        _encryptKeyfile.value = null
        addLog("Encrypt selection cleared.", LogType.INFO)
    }

    fun setEncryptKeyfile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = DocumentFile.fromSingleUri(context, uri)
                val name = doc?.name ?: "keyfile.bin"
                val size = doc?.length() ?: 0L
                val (hashBytes, hashHex) = CryptoEngine.hashKeyfile(context, uri)
                _encryptKeyfile.value = KeyfileInfo(uri = uri, name = name, sizeBytes = size, sha256Hex = hashHex)
                addLog("🗝️ Keyfile configured: \"$name\" (SHA-256: ${hashHex.take(12)}...)", LogType.INFO)
            } catch (e: Exception) {
                addLog("Failed to read keyfile: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun clearEncryptKeyfile() {
        _encryptKeyfile.value = null
        addLog("Keyfile removed.", LogType.INFO)
    }

    // Decrypt setters
    fun setDecryptPassword(pw: String) { _decryptPassword.value = pw }

    fun setDecryptVault(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = DocumentFile.fromSingleUri(context, uri)
                val name = doc?.name ?: "vault.zev"
                val size = doc?.length() ?: 0L
                _selectedDecryptVaultUri.value = uri
                _selectedDecryptVaultName.value = name
                _selectedDecryptVaultSize.value = size

                // Pre-parse header to detect v1 vs v2 and if keyfile is required
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    try {
                        val header = CryptoEngine.parseVaultHeader(inStream)
                        _detectedHeader.value = header
                        addLog("Detected vault format: ${header.version.displayName}" + if (header.requiresKeyfile) " (Keyfile Required)" else "", LogType.INFO)
                    } catch (e: Exception) {
                        _detectedHeader.value = null
                        addLog("Vault header inspection note: ${e.message}", LogType.WARN)
                    }
                }
                addLog("Selected vault file: \"$name\" (${CryptoEngine.formatBytes(size)}).", LogType.INFO)
            } catch (e: Exception) {
                addLog("Error inspecting vault: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun clearDecryptSelection() {
        _selectedDecryptVaultUri.value = null
        _selectedDecryptVaultName.value = ""
        _selectedDecryptVaultSize.value = 0L
        _detectedHeader.value = null
        _decryptKeyfile.value = null
        addLog("Decrypt selection cleared.", LogType.INFO)
    }

    fun setDecryptKeyfile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = DocumentFile.fromSingleUri(context, uri)
                val name = doc?.name ?: "keyfile.bin"
                val size = doc?.length() ?: 0L
                val (_, hashHex) = CryptoEngine.hashKeyfile(context, uri)
                _decryptKeyfile.value = KeyfileInfo(uri = uri, name = name, sizeBytes = size, sha256Hex = hashHex)
                addLog("🗝️ Decryption keyfile attached: \"$name\" (${hashHex.take(12)}...)", LogType.INFO)
            } catch (e: Exception) {
                addLog("Failed to read keyfile: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun clearDecryptKeyfile() {
        _decryptKeyfile.value = null
        addLog("Decryption keyfile removed.", LogType.INFO)
    }

    fun dismissRecoveryDialog() {
        _showRecoveryDialog.value = false
    }

    // Main Actions
    fun executeEncryption(context: Context, onVaultCreated: (File) -> Unit) {
        val items = _selectedEncryptItems.value
        val folderName = _selectedEncryptFolderName.value.ifEmpty { "secured_vault" }
        val password = _encryptPassword.value
        val confirm = _encryptConfirmPassword.value
        val useV2 = _useV2Encrypt.value
        val keyfile = _encryptKeyfile.value

        if (items.isEmpty()) {
            addLog("Please select a folder or files first.", LogType.WARN)
            return
        }
        if (password.length < 8) {
            addLog("Password must be at least 8 characters.", LogType.WARN)
            return
        }
        if (password != confirm) {
            addLog("Passwords do not match.", LogType.WARN)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val totalBytes = items.sumOf { it.sizeBytes }
            val startTime = System.currentTimeMillis()

            _progressState.value = OperationProgressState(
                isActive = true,
                isEncrypting = true,
                currentTitle = "Packaging folder...",
                overallPercent = 5f,
                compressStatus = StageStatus.IN_PROGRESS,
                compressPercent = 0f,
                metrics = StageMetrics(originalSizeBytes = totalBytes)
            )

            val tempDir = File(context.cacheDir, "vault_temp_${System.currentTimeMillis()}").apply { mkdirs() }
            val tempZipFile = File(tempDir, "archive.zip")
            val outputVaultFile = File(context.cacheDir, "$folderName.zev")

            try {
                addLog("Starting ${if (useV2) "v2 Enhanced" else "v1 Standard"} encryption of \"$folderName\"...", LogType.INFO)

                // Stage 1: Zip Packaging
                val compressStart = System.currentTimeMillis()
                CryptoEngine.createZipArchive(context, items, tempZipFile) { pct, _ ->
                    _progressState.update { current ->
                        current.copy(
                            currentTitle = "Packaging: ${pct.toInt()}%",
                            overallPercent = (pct * 0.45f).coerceIn(0f, 45f),
                            compressPercent = pct
                        )
                    }
                }
                val compressTime = System.currentTimeMillis() - compressStart
                val zipSize = tempZipFile.length()
                val compressionRatio = if (totalBytes > 0) ((1.0 - zipSize.toDouble() / totalBytes) * 100.0).toFloat() else 0f

                addLog("Packaging complete (${CryptoEngine.formatBytes(zipSize)} in ${compressTime}ms).", LogType.SUCCESS)

                _progressState.update { current ->
                    current.copy(
                        compressStatus = StageStatus.COMPLETED,
                        compressPercent = 100f,
                        cryptoStatus = StageStatus.IN_PROGRESS,
                        currentTitle = "Deriving key & encrypting AES-256-GCM...",
                        overallPercent = 55f,
                        metrics = current.metrics.copy(
                            compressedSizeBytes = zipSize,
                            compressionRatioPercent = compressionRatio,
                            compressTimeMs = compressTime,
                            cryptoSizeBytes = zipSize
                        )
                    )
                }

                // Stage 2: AES-256-GCM Encryption
                val keyfileHash = if (useV2 && keyfile != null) {
                    val (hashBytes, _) = CryptoEngine.hashKeyfile(context, keyfile.uri)
                    hashBytes
                } else null

                val cryptoStart = System.currentTimeMillis()
                CryptoEngine.encryptZipToVault(
                    zipFile = tempZipFile,
                    outputVaultFile = outputVaultFile,
                    password = password.toCharArray(),
                    useV2 = useV2,
                    keyfileHash = keyfileHash
                ) { pct ->
                    _progressState.update { current ->
                        current.copy(
                            cryptoPercent = pct,
                            overallPercent = (45f + pct * 0.45f).coerceIn(45f, 90f)
                        )
                    }
                }
                val cryptoTime = System.currentTimeMillis() - cryptoStart
                val vaultSize = outputVaultFile.length()

                addLog("AES-256-GCM encryption complete in ${cryptoTime}ms.", LogType.SUCCESS)

                // Stage 3: Save output
                _progressState.update { current ->
                    current.copy(
                        cryptoStatus = StageStatus.COMPLETED,
                        cryptoPercent = 100f,
                        saveStatus = StageStatus.COMPLETED,
                        savePercent = 100f,
                        overallPercent = 100f,
                        currentTitle = "Vault Created Successfully!",
                        metrics = current.metrics.copy(
                            cryptoTimeMs = cryptoTime,
                            outputName = outputVaultFile.name,
                            outputSizeBytes = vaultSize,
                            totalTimeMs = System.currentTimeMillis() - startTime,
                            versionString = if (useV2) "v2 Enhanced" else "v1 Standard"
                        )
                    )
                }

                val recoveryRecord = PasswordRecoveryRecord(
                    folderName = folderName,
                    vaultFilename = outputVaultFile.name,
                    version = if (useV2) "v2 Enhanced" else "v1 Standard",
                    password = password,
                    keyfileRequired = keyfile != null,
                    keyfileName = keyfile?.name ?: "",
                    keyfileFingerprint = keyfile?.sha256Hex ?: ""
                )
                _lastRecoveryRecord.value = recoveryRecord
                _showRecoveryDialog.value = true

                addLog("✅ Vault created: \"${outputVaultFile.name}\" (${CryptoEngine.formatBytes(vaultSize)})", LogType.SUCCESS)
                onVaultCreated(outputVaultFile)

            } catch (e: Exception) {
                addLog("❌ Encryption failed: ${e.message}", LogType.ERROR)
                _progressState.update {
                    it.copy(
                        errorMessage = e.message,
                        compressStatus = if (it.compressStatus == StageStatus.IN_PROGRESS) StageStatus.FAILED else it.compressStatus,
                        cryptoStatus = if (it.cryptoStatus == StageStatus.IN_PROGRESS) StageStatus.FAILED else it.cryptoStatus,
                        currentTitle = "Encryption Failed"
                    )
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    fun executeDecryption(context: Context, onDecrypted: (DecryptionResult) -> Unit) {
        val vaultUri = _selectedDecryptVaultUri.value
        val vaultName = _selectedDecryptVaultName.value
        val password = _decryptPassword.value
        val keyfile = _decryptKeyfile.value

        if (vaultUri == null) {
            addLog("Please select a .zev vault file first.", LogType.WARN)
            return
        }
        if (password.isEmpty()) {
            addLog("Please enter your decryption password.", LogType.WARN)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _progressState.value = OperationProgressState(
                isActive = true,
                isEncrypting = false,
                currentTitle = "Inspecting vault header...",
                overallPercent = 10f,
                compressStatus = StageStatus.IN_PROGRESS,
                metrics = StageMetrics(outputSizeBytes = _selectedDecryptVaultSize.value)
            )

            val tempDir = File(context.cacheDir, "decrypt_temp_${System.currentTimeMillis()}").apply { mkdirs() }
            val decryptedZipFile = File(tempDir, "decrypted.zip")
            val extractedDir = File(context.filesDir, "extracted_${vaultName.substringBeforeLast('.')}_${System.currentTimeMillis()}").apply { mkdirs() }

            try {
                addLog("Reading vault \"$vaultName\"...", LogType.INFO)

                val keyfileHash = if (keyfile != null) {
                    val (hashBytes, _) = CryptoEngine.hashKeyfile(context, keyfile.uri)
                    hashBytes
                } else null

                var detectedVersion = VaultVersion.V1
                val cryptoStart = System.currentTimeMillis()

                _progressState.update {
                    it.copy(
                        compressStatus = StageStatus.COMPLETED,
                        compressPercent = 100f,
                        cryptoStatus = StageStatus.IN_PROGRESS,
                        currentTitle = "Deriving key & verifying AES-GCM tag...",
                        overallPercent = 40f
                    )
                }

                val header = CryptoEngine.decryptVaultToZip(
                    context = context,
                    vaultUri = vaultUri,
                    outputZipFile = decryptedZipFile,
                    password = password.toCharArray(),
                    keyfileHash = keyfileHash,
                    onHeaderParsed = { hdr ->
                        detectedVersion = hdr.version
                        addLog("Authenticated header: ${hdr.version.displayName}", LogType.INFO)
                    },
                    onProgress = { pct ->
                        _progressState.update {
                            it.copy(cryptoPercent = pct, overallPercent = (40f + pct * 0.4f).coerceIn(40f, 80f))
                        }
                    }
                )

                val cryptoTime = System.currentTimeMillis() - cryptoStart
                addLog("✅ AES-256-GCM authenticated & decrypted in ${cryptoTime}ms.", LogType.SUCCESS)

                _progressState.update {
                    it.copy(
                        cryptoStatus = StageStatus.COMPLETED,
                        cryptoPercent = 100f,
                        saveStatus = StageStatus.IN_PROGRESS,
                        currentTitle = "Extracting vault files...",
                        overallPercent = 85f,
                        metrics = it.metrics.copy(
                            authTagVerified = true,
                            cryptoTimeMs = cryptoTime,
                            versionString = header.version.displayName
                        )
                    )
                }

                // Extract Zip contents for in-app browser
                val fileEntries = CryptoEngine.extractZipArchive(decryptedZipFile, extractedDir, context)
                val totalExtractedBytes = fileEntries.filter { !it.isDirectory }.sumOf { it.sizeBytes }

                // Also save persistent zip for export
                val exportZipFile = File(context.cacheDir, "${vaultName.substringBeforeLast('.')}_decrypted.zip")
                decryptedZipFile.copyTo(exportZipFile, overwrite = true)

                val result = DecryptionResult(
                    vaultName = vaultName,
                    outputZipUri = Uri.fromFile(exportZipFile),
                    files = fileEntries,
                    totalSizeBytes = totalExtractedBytes,
                    version = header.version
                )
                _decryptionResult.value = result

                _progressState.update {
                    it.copy(
                        saveStatus = StageStatus.COMPLETED,
                        savePercent = 100f,
                        overallPercent = 100f,
                        currentTitle = "Decryption Complete!",
                        metrics = it.metrics.copy(
                            outputName = exportZipFile.name,
                            outputSizeBytes = exportZipFile.length(),
                            totalTimeMs = System.currentTimeMillis() - startTime
                        )
                    )
                }

                addLog("✅ Restored ${fileEntries.size} items (${CryptoEngine.formatBytes(totalExtractedBytes)}).", LogType.SUCCESS)
                onDecrypted(result)

            } catch (e: Exception) {
                addLog("❌ Decryption failed: ${e.message ?: "Authentication Tag Mismatch or Wrong Password"}", LogType.ERROR)
                _progressState.update {
                    it.copy(
                        errorMessage = e.message ?: "Authentication failed",
                        cryptoStatus = StageStatus.FAILED,
                        currentTitle = "Decryption Failed",
                        metrics = it.metrics.copy(authTagVerified = false)
                    )
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
}
