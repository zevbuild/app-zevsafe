package com.zevbuild.zevsafe.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevsafe.crypto.CryptoEngine
import com.zevbuild.zevsafe.crypto.OperationProgressState
import com.zevbuild.zevsafe.crypto.StageStatus
import com.zevbuild.zevsafe.ui.theme.CardBorder
import com.zevbuild.zevsafe.ui.theme.Danger
import com.zevbuild.zevsafe.ui.theme.PurpleLight
import com.zevbuild.zevsafe.ui.theme.PurplePrimary
import com.zevbuild.zevsafe.ui.theme.Success
import com.zevbuild.zevsafe.ui.theme.SurfaceVariantDark
import com.zevbuild.zevsafe.ui.theme.TealLight
import com.zevbuild.zevsafe.ui.theme.TextMuted
import com.zevbuild.zevsafe.ui.theme.TextPrimary
import com.zevbuild.zevsafe.ui.theme.TextSecondary

@Composable
fun StageProgressTracker(
    progressState: OperationProgressState,
    modifier: Modifier = Modifier
) {
    if (!progressState.isActive && progressState.overallPercent <= 0f) return

    val isEncrypt = progressState.isEncrypting

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariantDark.copy(alpha = 0.85f))
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Overall title & percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                progressState.errorMessage != null -> Danger
                                progressState.overallPercent >= 100f -> Success
                                else -> PurpleLight
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = progressState.currentTitle.ifEmpty { if (isEncrypt) "Encrypting Vault..." else "Decrypting Vault..." },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            Text(
                text = "${progressState.overallPercent.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (progressState.overallPercent >= 100f) Success else PurpleLight
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { (progressState.overallPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (progressState.overallPercent >= 100f) Success else PurplePrimary,
            trackColor = Color.White.copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3 Pipeline Stages
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StagePill(
                label = if (isEncrypt) "Compress" else "Inspect",
                icon = Icons.Default.Archive,
                status = progressState.compressStatus,
                modifier = Modifier.weight(1f)
            )

            StageConnector(active = progressState.cryptoStatus != StageStatus.IDLE)

            StagePill(
                label = if (isEncrypt) "Encrypt" else "Decrypt",
                icon = if (isEncrypt) Icons.Default.Lock else Icons.Default.LockOpen,
                status = progressState.cryptoStatus,
                modifier = Modifier.weight(1f)
            )

            StageConnector(active = progressState.saveStatus != StageStatus.IDLE)

            StagePill(
                label = if (isEncrypt) "Save Vault" else "Restore",
                icon = Icons.Default.Save,
                status = progressState.saveStatus,
                modifier = Modifier.weight(1f)
            )
        }

        // Metrics breakdown card
        AnimatedVisibility(visible = progressState.metrics.originalSizeBytes > 0 || progressState.metrics.cryptoSizeBytes > 0 || progressState.metrics.outputSizeBytes > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "CRYPTOGRAPHIC PIPELINE METRICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealLight,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isEncrypt) {
                    if (progressState.metrics.originalSizeBytes > 0) {
                        MetricRow("Source Size", CryptoEngine.formatBytes(progressState.metrics.originalSizeBytes))
                    }
                    if (progressState.metrics.compressedSizeBytes > 0) {
                        MetricRow("Package Size", CryptoEngine.formatBytes(progressState.metrics.compressedSizeBytes))
                        MetricRow("Space Saved", "${"%.1f".format(progressState.metrics.compressionRatioPercent)}%")
                    }
                    if (progressState.metrics.cryptoTimeMs > 0) {
                        MetricRow("AES-GCM Time", "${progressState.metrics.cryptoTimeMs} ms")
                    }
                    if (progressState.metrics.outputSizeBytes > 0) {
                        MetricRow("Final .zev Vault", CryptoEngine.formatBytes(progressState.metrics.outputSizeBytes))
                    }
                } else {
                    if (progressState.metrics.outputSizeBytes > 0) {
                        MetricRow("Vault File Size", CryptoEngine.formatBytes(progressState.metrics.outputSizeBytes))
                    }
                    if (progressState.metrics.authTagVerified != null) {
                        MetricRow(
                            "AES-GCM Auth Tag",
                            if (progressState.metrics.authTagVerified == true) "✅ Authenticated & Verified" else "❌ Authentication Failed"
                        )
                    }
                    if (progressState.metrics.versionString.isNotEmpty()) {
                        MetricRow("Format Detected", progressState.metrics.versionString)
                    }
                }
            }
        }
    }
}

@Composable
private fun StagePill(
    label: String,
    icon: ImageVector,
    status: StageStatus,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = when (status) {
            StageStatus.COMPLETED -> Success.copy(alpha = 0.15f)
            StageStatus.IN_PROGRESS -> PurplePrimary.copy(alpha = 0.2f)
            StageStatus.FAILED -> Danger.copy(alpha = 0.2f)
            StageStatus.IDLE -> Color.White.copy(alpha = 0.04f)
        },
        label = "stage_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = when (status) {
            StageStatus.COMPLETED -> Success.copy(alpha = 0.5f)
            StageStatus.IN_PROGRESS -> PurpleLight
            StageStatus.FAILED -> Danger
            StageStatus.IDLE -> CardBorder
        },
        label = "stage_border"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                StageStatus.COMPLETED -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done",
                    tint = Success,
                    modifier = Modifier.size(16.dp)
                )
                StageStatus.IN_PROGRESS -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = PurpleLight
                )
                StageStatus.FAILED -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Failed",
                    tint = Danger,
                    modifier = Modifier.size(16.dp)
                )
                StageStatus.IDLE -> Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (status == StageStatus.IDLE) TextMuted else TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun StageConnector(active: Boolean) {
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(2.dp)
            .background(if (active) PurpleLight else CardBorder)
    )
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
