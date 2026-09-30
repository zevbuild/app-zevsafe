package com.zevbuild.zevsafe.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zevbuild.zevsafe.crypto.CryptoEngine
import com.zevbuild.zevsafe.crypto.DecryptedFileEntry
import com.zevbuild.zevsafe.crypto.DecryptionResult
import com.zevbuild.zevsafe.crypto.KeyfileInfo
import com.zevbuild.zevsafe.crypto.LogEntry
import com.zevbuild.zevsafe.crypto.LogType
import com.zevbuild.zevsafe.crypto.OperationProgressState
import com.zevbuild.zevsafe.crypto.PasswordRecoveryRecord
import com.zevbuild.zevsafe.crypto.SelectedItem
import com.zevbuild.zevsafe.crypto.StageMetrics
import com.zevbuild.zevsafe.crypto.StageStatus
import com.zevbuild.zevsafe.crypto.VaultCatalog
import com.zevbuild.zevsafe.crypto.VaultHeader
import com.zevbuild.zevsafe.crypto.VaultVersion
import com.zevbuild.zevsafe.service.VaultForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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

    private val _useV2Encrypt = MutableStateFlow(true) // Defaults to v3 modern format
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
        addLog("v3 STREAM AEAD 4 MB chunked engine active.", LogType.INFO)
    }

    fun addLog(message: String, type: LogType = LogType.INFO) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.update { it + LogEntry(timestamp = time, message = message, type = type) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun setEncryptPassword(pw: String) {
        _encryptPassword.value = pw
    }

    fun setEncryptConfirmPassword(pw: String) {
        _encryptConfirmPassword.value = pw
    }

    fun setUseV2Encrypt(use: Boolean) {
        _useV2Encrypt.value = use
    }

    fun setDecryptPassword(pw: String) {
        _decryptPassword.value = pw
    }

    fun dismissRecoveryDialog() {
        _showRecoveryDialog.value = false
    }

    fun clearSelectedEncryptItems() {
        _selectedEncryptItems.value = emptyList()
        _selectedEncryptFolderName.value = ""
    }

    fun clearEncryptSelection() {
        clearSelectedEncryptItems()
    }

    fun setEncryptKeyfile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (hashBytes, hex) = CryptoEngine.hashKeyfile(context, uri)
                val doc = DocumentFile.fromSingleUri(context, uri)
                val name = doc?.name ?: "keyfile.bin"
                val size = doc?.length() ?: 0L
                _encryptKeyfile.value = KeyfileInfo(uri, name, size, hex)
                addLog("Keyfile loaded: $name (SHA-256: ${hex.take(12)}...)", LogType.SUCCESS)
            } catch (e: Exception) {
                addLog("Failed to load keyfile: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun clearEncryptKeyfile() {
        _encryptKeyfile.value = null
        addLog("Keyfile removed.", LogType.INFO)
    }

    fun setDecryptKeyfile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (hashBytes, hex) = CryptoEngine.hashKeyfile(context, uri)
                val doc = DocumentFile.fromSingleUri(context, uri)
                val name = doc?.name ?: "keyfile.bin"
                val size = doc?.length() ?: 0L
                _decryptKeyfile.value = KeyfileInfo(uri, name, size, hex)
                addLog("Decryption keyfile loaded: $name", LogType.SUCCESS)
            } catch (e: Exception) {
                addLog("Failed to load keyfile: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun clearDecryptKeyfile() {
        _decryptKeyfile.value = null
    }

    fun setEncryptFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            val folderName = rootDoc.name ?: "Encrypted_Folder"
            _selectedEncryptFolderName.value = folderName

            val items = mutableListOf<SelectedItem>()
            traverseDocumentTree(context, rootDoc, "", items)
            _selectedEncryptItems.value = items
            addLog("Loaded folder \"$folderName\" with ${items.size} files (${CryptoEngine.formatBytes(items.sumOf { it.sizeBytes })}).", LogType.INFO)
        }
    }

    fun setEncryptFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<SelectedItem>()
            for (uri in uris) {
                val doc = DocumentFile.fromSingleUri(context, uri) ?: continue
                val name = doc.name ?: "file"
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
            if (items.isNotEmpty()) {
                _selectedEncryptFolderName.value = if (items.size == 1) items[0].name.substringBeforeLast('.') else "Archive_${items.size}_files"
                _selectedEncryptItems.value = items
                addLog("Loaded ${items.size} files for encryption.", LogType.INFO)
            }
        }
    }

    fun handleIncomingShareUris(context: Context, uris: List<Uri>) {
        setEncryptFiles(context, uris)
    }

    private fun traverseDocumentTree(
        context: Context,
        dir: DocumentFile,
        currentPath: String,
        items: MutableList<SelectedItem>
    ) {
        for (file in dir.listFiles()) {
            val relativePath = if (currentPath.isEmpty()) file.name ?: "" else "$currentPath/${file.name}"
            if (file.isDirectory) {
                items.add(
                    SelectedItem(
                        uri = file.uri,
                        name = file.name ?: "",
                        relativePath = relativePath,
                        sizeBytes = 0,
                        isDirectory = true
                    )
                )
                traverseDocumentTree(context, file, relativePath, items)
            } else {
                items.add(
                    SelectedItem(
                        uri = file.uri,
                        name = file.name ?: "",
                        relativePath = relativePath,
                        sizeBytes = file.length(),
                        isDirectory = false
                    )
                )
            }
        }
    }

    fun setDecryptVault(context: Context, uri: Uri) {
        _selectedDecryptVaultUri.value = uri
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var name = "vault.zev"
                var size = 0L

                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) name = it.getString(nameIndex) ?: name
                        if (sizeIndex != -1) size = it.getLong(sizeIndex)
                    }
                }

                _selectedDecryptVaultName.value = name
                _selectedDecryptVaultSize.value = size

                // Sniff Header
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    val header = CryptoEngine.parseVaultHeader(inStream)
                    _detectedHeader.value = header
                    addLog("Detected vault format: ${header.version.displayName}", LogType.INFO)
                    if (header.requiresKeyfile) {
                        addLog("⚠️ Vault requires a keyfile (2FA) for decryption.", LogType.WARN)
                    }
                }
            } catch (e: Exception) {
                addLog("Failed to inspect vault: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun clearDecryptVault() {
        _selectedDecryptVaultUri.value = null
        _selectedDecryptVaultName.value = ""
        _selectedDecryptVaultSize.value = 0L
        _detectedHeader.value = null
        _decryptionResult.value = null
    }

    fun clearDecryptSelection() {
        clearDecryptVault()
    }

    fun executeEncryption(context: Context, onVaultCreated: (File) -> Unit) {
        val items = _selectedEncryptItems.value
        val folderName = _selectedEncryptFolderName.value.ifEmpty { "ZevSafe_Vault" }
        val password = _encryptPassword.value
        val confirmPassword = _encryptConfirmPassword.value
        val keyfile = _encryptKeyfile.value

        if (items.isEmpty()) {
            addLog("Please select files or a folder to encrypt.", LogType.WARN)
            return
        }
        if (password.isEmpty()) {
            addLog("Password cannot be empty.", LogType.WARN)
            return
        }
        if (password != confirmPassword) {
            addLog("Passwords do not match.", LogType.ERROR)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val totalBytes = items.sumOf { it.sizeBytes }

            _progressState.value = OperationProgressState(
                isActive = true,
                isEncrypting = true,
                currentTitle = "Initializing v3 STREAM AEAD engine...",
                overallPercent = 5f,
                compressStatus = StageStatus.IN_PROGRESS,
                compressPercent = 0f,
                metrics = StageMetrics(originalSizeBytes = totalBytes)
            )

            VaultForegroundService.startService(
                context,
                "ZevSafe Streaming Encryption",
                "Encrypting \"$folderName\" (${CryptoEngine.formatBytes(totalBytes)})..."
            )

            val outputVaultFile = File(context.cacheDir, "$folderName.zev")

            try {
                addLog("Starting v3 STREAM AEAD encryption of \"$folderName\"...", LogType.INFO)

                val keyfileHash = if (keyfile != null) {
                    val (hashBytes, _) = CryptoEngine.hashKeyfile(context, keyfile.uri)
                    hashBytes
                } else null

                val cryptoStart = System.currentTimeMillis()
                CryptoEngine.encryptItemsToV3Vault(
                    context = context,
                    items = items,
                    outputVaultFile = outputVaultFile,
                    password = password.toCharArray(),
                    keyfileHash = keyfileHash
                ) { pct, processed, total ->
                    val elapsed = (System.currentTimeMillis() - cryptoStart) / 1000.0
                    val mbPerSec = if (elapsed > 0) (processed / (1024.0 * 1024.0)) / elapsed else 0.0

                    _progressState.update { current ->
                        current.copy(
                            currentTitle = "Streaming Chunks: ${pct.toInt()}% (${String.format(Locale.US, "%.1f", mbPerSec)} MB/s)",
                            overallPercent = pct,
                            compressPercent = pct,
                            cryptoPercent = pct,
                            compressStatus = if (pct >= 50f) StageStatus.COMPLETED else StageStatus.IN_PROGRESS,
                            cryptoStatus = StageStatus.IN_PROGRESS
                        )
                    }

                    VaultForegroundService.updateProgress(
                        context,
                        "ZevSafe Streaming Encryption",
                        "${pct.toInt()}% · ${String.format(Locale.US, "%.1f", mbPerSec)} MB/s",
                        pct.toInt()
                    )
                }

                val totalTime = System.currentTimeMillis() - startTime
                val vaultSize = outputVaultFile.length()

                _progressState.update { current ->
                    current.copy(
                        compressStatus = StageStatus.COMPLETED,
                        compressPercent = 100f,
                        cryptoStatus = StageStatus.COMPLETED,
                        cryptoPercent = 100f,
                        saveStatus = StageStatus.COMPLETED,
                        savePercent = 100f,
                        overallPercent = 100f,
                        currentTitle = "Vault Created Successfully!",
                        metrics = current.metrics.copy(
                            cryptoTimeMs = totalTime,
                            outputName = outputVaultFile.name,
                            outputSizeBytes = vaultSize,
                            totalTimeMs = totalTime,
                            versionString = "v3 Streaming (ZV3\\0)"
                        )
                    )
                }

                val recoveryRecord = PasswordRecoveryRecord(
                    folderName = folderName,
                    vaultFilename = outputVaultFile.name,
                    version = "v3 Streaming (ZV3\\0)",
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
                        cryptoStatus = StageStatus.FAILED,
                        currentTitle = "Encryption Failed"
                    )
                }
            } finally {
                VaultForegroundService.stopService(context)
            }
        }
    }

    fun loadInstantCatalog(context: Context, onSuccess: () -> Unit) {
        val vaultUri = _selectedDecryptVaultUri.value ?: return
        val password = _decryptPassword.value
        val keyfile = _decryptKeyfile.value

        if (password.isEmpty()) {
            addLog("Please enter your password to browse vault.", LogType.WARN)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog("Reading v3 encrypted tail manifest (<100ms)...", LogType.INFO)
                val keyfileHash = if (keyfile != null) {
                    val (hashBytes, _) = CryptoEngine.hashKeyfile(context, keyfile.uri)
                    hashBytes
                } else null

                val catalog = CryptoEngine.readV3VaultManifest(
                    context = context,
                    vaultUri = vaultUri,
                    password = password.toCharArray(),
                    keyfileHash = keyfileHash
                )

                _decryptionResult.value = DecryptionResult(
                    vaultName = _selectedDecryptVaultName.value,
                    outputZipUri = null,
                    files = catalog.files,
                    totalSizeBytes = catalog.totalSize,
                    version = VaultVersion.V3
                )

                addLog("✅ Instant Explorer ready: ${catalog.fileCount} files cataloged.", LogType.SUCCESS)
                onSuccess()
            } catch (e: Exception) {
                addLog("❌ Instant manifest read failed: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun executeDecryption(context: Context, onDecrypted: (DecryptionResult) -> Unit) {
        val vaultUri = _selectedDecryptVaultUri.value
        val vaultName = _selectedDecryptVaultName.value
        val password = _decryptPassword.value
        val keyfile = _decryptKeyfile.value
        val header = _detectedHeader.value

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
                currentTitle = "Streaming decryption...",
                overallPercent = 10f,
                cryptoStatus = StageStatus.IN_PROGRESS,
                metrics = StageMetrics(outputSizeBytes = _selectedDecryptVaultSize.value)
            )

            VaultForegroundService.startService(
                context,
                "ZevSafe Streaming Decryption",
                "Decrypting \"$vaultName\"..."
            )

            val extractedDir = File(context.filesDir, "extracted_${vaultName.substringBeforeLast('.')}_${System.currentTimeMillis()}").apply { mkdirs() }

            try {
                addLog("Decrypting vault \"$vaultName\"...", LogType.INFO)

                val keyfileHash = if (keyfile != null) {
                    val (hashBytes, _) = CryptoEngine.hashKeyfile(context, keyfile.uri)
                    hashBytes
                } else null

                val fileEntries = if (header?.version == VaultVersion.V3) {
                    CryptoEngine.decryptV3VaultToDirectory(
                        context = context,
                        vaultUri = vaultUri,
                        outputDir = extractedDir,
                        password = password.toCharArray(),
                        keyfileHash = keyfileHash
                    ) { pct, msg ->
                        _progressState.update {
                            it.copy(
                                cryptoPercent = pct,
                                overallPercent = pct,
                                currentTitle = msg
                            )
                        }
                        VaultForegroundService.updateProgress(context, "ZevSafe Streaming Decryption", msg, pct.toInt())
                    }
                } else {
                    CryptoEngine.decryptV1OrV2Vault(
                        context = context,
                        vaultUri = vaultUri,
                        outputDir = extractedDir,
                        password = password.toCharArray(),
                        keyfileHash = keyfileHash
                    ) { pct, msg ->
                        _progressState.update {
                            it.copy(
                                cryptoPercent = pct,
                                overallPercent = pct,
                                currentTitle = msg
                            )
                        }
                    }
                }

                val totalExtractedBytes = fileEntries.filter { !it.isDirectory }.sumOf { it.sizeBytes }

                val result = DecryptionResult(
                    vaultName = vaultName,
                    outputZipUri = null,
                    files = fileEntries,
                    totalSizeBytes = totalExtractedBytes,
                    version = header?.version ?: VaultVersion.V3
                )
                _decryptionResult.value = result

                _progressState.update {
                    it.copy(
                        cryptoStatus = StageStatus.COMPLETED,
                        saveStatus = StageStatus.COMPLETED,
                        cryptoPercent = 100f,
                        savePercent = 100f,
                        overallPercent = 100f,
                        currentTitle = "Decryption Complete!",
                        metrics = it.metrics.copy(
                            authTagVerified = true,
                            outputName = vaultName,
                            outputSizeBytes = totalExtractedBytes,
                            totalTimeMs = System.currentTimeMillis() - startTime,
                            versionString = header?.version?.displayName ?: "v3 Streaming"
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
                VaultForegroundService.stopService(context)
            }
        }
    }
}
