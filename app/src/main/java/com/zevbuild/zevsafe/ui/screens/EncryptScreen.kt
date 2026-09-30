package com.zevbuild.zevsafe.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.zevbuild.zevsafe.crypto.CryptoEngine
import com.zevbuild.zevsafe.ui.components.ActivityTerminal
import com.zevbuild.zevsafe.ui.components.CardAccent
import com.zevbuild.zevsafe.ui.components.CyberCard
import com.zevbuild.zevsafe.ui.components.PasswordRecoveryDialog
import com.zevbuild.zevsafe.ui.components.PasswordStrengthMeter
import com.zevbuild.zevsafe.ui.components.StageProgressTracker
import com.zevbuild.zevsafe.ui.theme.CardBorder
import com.zevbuild.zevsafe.ui.theme.Danger
import com.zevbuild.zevsafe.ui.theme.PurpleLight
import com.zevbuild.zevsafe.ui.theme.PurplePrimary
import com.zevbuild.zevsafe.ui.theme.SurfaceDark
import com.zevbuild.zevsafe.ui.theme.SurfaceVariantDark
import com.zevbuild.zevsafe.ui.theme.TealLight
import com.zevbuild.zevsafe.ui.theme.TealPrimary
import com.zevbuild.zevsafe.ui.theme.TextMuted
import com.zevbuild.zevsafe.ui.theme.TextPrimary
import com.zevbuild.zevsafe.ui.theme.TextSecondary
import com.zevbuild.zevsafe.ui.theme.Warning
import com.zevbuild.zevsafe.viewmodel.VaultViewModel

@Composable
fun EncryptScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val selectedItems by viewModel.selectedEncryptItems.collectAsState()
    val folderName by viewModel.selectedEncryptFolderName.collectAsState()
    val password by viewModel.encryptPassword.collectAsState()
    val confirmPassword by viewModel.encryptConfirmPassword.collectAsState()
    val useV2 by viewModel.useV2Encrypt.collectAsState()
    val keyfile by viewModel.encryptKeyfile.collectAsState()
    val progressState by viewModel.progressState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val showRecoveryDialog by viewModel.showRecoveryDialog.collectAsState()
    val lastRecoveryRecord by viewModel.lastRecoveryRecord.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setEncryptFolder(context, it) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setEncryptFiles(context, uris)
        }
    }

    val keyfilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.setEncryptKeyfile(context, it) }
    }

    // Recovery Modal
    if (showRecoveryDialog && lastRecoveryRecord != null) {
        PasswordRecoveryDialog(
            record = lastRecoveryRecord!!,
            onDismiss = { viewModel.dismissRecoveryDialog() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CyberCard(
            title = "Encrypt Folder",
            subtitle = "Compress & encrypt files into a portable .zev vault",
            tag = "ENCRYPT",
            accent = CardAccent.PURPLE
        ) {
            // Folder / Files Selection Drop Area
            if (selectedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceVariantDark.copy(alpha = 0.5f))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.linearGradient(listOf(PurplePrimary.copy(alpha = 0.4f), TealPrimary.copy(alpha = 0.4f)))
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PurplePrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Select Folder",
                                tint = PurpleLight,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select Folder or Files to Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Preserves complete directory tree structure",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("select_folder_btn")
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Folder", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("select_files_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Multiple Files", fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // Selected item card
                val totalBytes = selectedItems.sumOf { it.sizeBytes }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceVariantDark)
                        .border(1.dp, PurpleLight.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurplePrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = PurpleLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${selectedItems.size} file(s) · ${CryptoEngine.formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TealLight
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.clearEncryptSelection() },
                        modifier = Modifier.testTag("clear_encrypt_selection_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Password Input
            Text(
                text = "Master Password",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.setEncryptPassword(it) },
                placeholder = { Text("Min. 8 characters", color = TextMuted) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = TextSecondary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("encrypt_password_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurplePrimary,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            // Password Strength Indicator
            PasswordStrengthMeter(password = password)

            Spacer(modifier = Modifier.height(14.dp))

            // Confirm Password Input
            Text(
                text = "Confirm Master Password",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { viewModel.setEncryptConfirmPassword(it) },
                placeholder = { Text("Re-enter master password", color = TextMuted) },
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(
                            imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showConfirmPassword) "Hide password" else "Show password",
                            tint = TextSecondary
                        )
                    }
                },
                isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("encrypt_confirm_password_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurplePrimary,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // v3 Low-RAM Streaming Security Mode Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceVariantDark)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TealPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v3 STREAM",
                                color = TealLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Low-RAM Streaming Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PBKDF2-SHA512 (600k) · 4 MB Chunks · Tail Manifest · Keyfile 2FA",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = useV2,
                    onCheckedChange = { viewModel.setUseV2Encrypt(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TealPrimary,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceDark
                    ),
                    modifier = Modifier.testTag("v2_mode_toggle")
                )
            }

            // v2 Keyfile Options Panel
            AnimatedVisibility(visible = useV2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceVariantDark.copy(alpha = 0.6f))
                        .border(1.dp, TealPrimary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Keyfile (Optional 2nd Factor)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealLight
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Any file acts as a physical key. Without it, the vault cannot be unlocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (keyfile == null) {
                        OutlinedButton(
                            onClick = { keyfilePickerLauncher.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("select_keyfile_encrypt_btn")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = TealLight)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Keyfile", color = TealLight, fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "🗝️ ${keyfile!!.name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${keyfile!!.sha256Hex.take(8)}...)",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TealLight
                                )
                            }

                            IconButton(
                                onClick = { viewModel.clearEncryptKeyfile() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Danger Callout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Danger.copy(alpha = 0.12f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Danger,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "If you lose the password or required keyfile, files are permanently lost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Encrypt Action Button
            Button(
                onClick = {
                    viewModel.executeEncryption(context) { vaultFile ->
                        // Offer sharing or saving vault file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "com.aistudio.zevsafe.qvkn.fileprovider",
                            vaultFile
                        )
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(sendIntent, "Share or Save .zev Vault"))
                        } catch (_: Exception) {}
                    }
                },
                enabled = selectedItems.isNotEmpty() && password.length >= 8 && password == confirmPassword && !progressState.isActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_encrypt_vault"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurplePrimary,
                    disabledContainerColor = PurplePrimary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (useV2) "Encrypt Folder (v2 Enhanced)" else "Encrypt Folder (.zev)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Live Stage Progress Tracker
        StageProgressTracker(progressState = progressState)

        // Activity Terminal Log
        ActivityTerminal(
            logs = logs,
            onClearLogs = { viewModel.clearLogs() }
        )
    }
}
