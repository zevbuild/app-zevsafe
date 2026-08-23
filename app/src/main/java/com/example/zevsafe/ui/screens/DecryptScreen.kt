package com.example.zevsafe.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.example.zevsafe.crypto.CryptoEngine
import com.example.zevsafe.crypto.VaultVersion
import com.example.zevsafe.ui.components.ActivityTerminal
import com.example.zevsafe.ui.components.CardAccent
import com.example.zevsafe.ui.components.CyberCard
import com.example.zevsafe.ui.components.StageProgressTracker
import com.example.zevsafe.ui.theme.CardBorder
import com.example.zevsafe.ui.theme.Danger
import com.example.zevsafe.ui.theme.PurpleLight
import com.example.zevsafe.ui.theme.PurplePrimary
import com.example.zevsafe.ui.theme.Success
import com.example.zevsafe.ui.theme.SurfaceDark
import com.example.zevsafe.ui.theme.SurfaceVariantDark
import com.example.zevsafe.ui.theme.TealLight
import com.example.zevsafe.ui.theme.TealPrimary
import com.example.zevsafe.ui.theme.TextMuted
import com.example.zevsafe.ui.theme.TextPrimary
import com.example.zevsafe.ui.theme.TextSecondary
import com.example.zevsafe.ui.theme.Warning
import com.example.zevsafe.viewmodel.VaultViewModel
import java.io.File

@Composable
fun DecryptScreen(
    viewModel: VaultViewModel,
    onNavigateToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val selectedVaultUri by viewModel.selectedDecryptVaultUri.collectAsState()
    val selectedVaultName by viewModel.selectedDecryptVaultName.collectAsState()
    val selectedVaultSize by viewModel.selectedDecryptVaultSize.collectAsState()
    val password by viewModel.decryptPassword.collectAsState()
    val detectedHeader by viewModel.detectedHeader.collectAsState()
    val keyfile by viewModel.decryptKeyfile.collectAsState()
    val progressState by viewModel.progressState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val decryptionResult by viewModel.decryptionResult.collectAsState()

    var showPassword by remember { mutableStateOf(false) }

    val vaultPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.setDecryptVault(context, it) }
    }

    val keyfilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.setDecryptKeyfile(context, it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CyberCard(
            title = "Decrypt Vault",
            subtitle = "Restore and extract portable .zev encrypted files",
            tag = "DECRYPT",
            accent = CardAccent.TEAL
        ) {
            // Vault File Selection Area
            if (selectedVaultUri == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceVariantDark.copy(alpha = 0.5f))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.linearGradient(listOf(TealPrimary.copy(alpha = 0.4f), PurplePrimary.copy(alpha = 0.4f)))
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
                                .background(TealPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Select Vault",
                                tint = TealLight,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select .zev Vault File",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Auto-detects v1 Standard & v2 Enhanced formats",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { vaultPickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("select_vault_file_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Vault File", fontSize = 13.sp)
                        }
                    }
                }
            } else {
                // Selected Vault Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceVariantDark)
                        .border(1.dp, TealLight.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
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
                                .background(TealPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Vault",
                                tint = TealLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = selectedVaultName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = CryptoEngine.formatBytes(selectedVaultSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = TealLight
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.clearDecryptSelection() },
                        modifier = Modifier.testTag("clear_decrypt_selection_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary
                        )
                    }
                }

                // Format inspection banner
                if (detectedHeader != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (detectedHeader!!.version == VaultVersion.V2) TealPrimary.copy(alpha = 0.2f) else PurplePrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = detectedHeader!!.version.displayName,
                                color = if (detectedHeader!!.version == VaultVersion.V2) TealLight else PurpleLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (detectedHeader!!.requiresKeyfile) "Requires Master Password + Keyfile 2FA" else "Standard Password Protected",
                            fontSize = 12.sp,
                            color = if (detectedHeader!!.requiresKeyfile) Warning else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Password Input
            Text(
                text = "Decryption Password",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.setDecryptPassword(it) },
                placeholder = { Text("Enter your master vault password", color = TextMuted) },
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
                    .testTag("decrypt_password_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            // Keyfile Section (shown if v2 or manually optional)
            if (detectedHeader?.requiresKeyfile == true || detectedHeader?.version == VaultVersion.V2) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Keyfile (Physical Second Factor)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TealLight
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Select the exact keyfile used during encryption",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (keyfile == null) {
                        OutlinedButton(
                            onClick = { keyfilePickerLauncher.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("select_keyfile_decrypt_btn")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = TealLight)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Keyfile", color = TealLight, fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "🗝️ ${keyfile!!.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearDecryptKeyfile() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Decrypt Action Button
            Button(
                onClick = {
                    viewModel.executeDecryption(context) { result ->
                        Toast.makeText(context, "✅ Decrypted ${result.files.size} items", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = selectedVaultUri != null && password.isNotEmpty() && !progressState.isActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_decrypt_vault"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    disabledContainerColor = TealPrimary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Decrypt & Restore Vault",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Decryption Success Card
        AnimatedVisibility(visible = decryptionResult != null) {
            decryptionResult?.let { res ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, Success.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Success.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Success, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Vault Successfully Restored",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${res.files.size} files restored · ${CryptoEngine.formatBytes(res.totalSizeBytes)}",
                                    fontSize = 12.sp,
                                    color = Success
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onNavigateToBrowser,
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("browse_decrypted_btn")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Files", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val zipFile = File(context.cacheDir, "${res.vaultName.substringBeforeLast('.')}_decrypted.zip")
                                    if (zipFile.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "com.aistudio.zevsafe.qvkn.fileprovider",
                                            zipFile
                                        )
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Export Decrypted ZIP"))
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_zip_btn")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export ZIP", fontSize = 13.sp)
                            }
                        }
                    }
                }
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
