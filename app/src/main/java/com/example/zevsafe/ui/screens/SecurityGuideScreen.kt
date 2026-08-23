package com.example.zevsafe.ui.screens

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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zevsafe.ui.components.CardAccent
import com.example.zevsafe.ui.components.CyberCard
import com.example.zevsafe.ui.theme.CardBorder
import com.example.zevsafe.ui.theme.Danger
import com.example.zevsafe.ui.theme.Info
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

@Composable
fun SecurityGuideScreen(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CyberCard(
            title = "Zero-Knowledge Security Architecture",
            subtitle = "Bank-grade client-side cryptography without remote backdoors",
            tag = "AIR-GAPPED",
            accent = CardAccent.PURPLE
        ) {
            Text(
                text = "ZevSafe operates on a strictly zero-knowledge model. All cryptographic transformations occur in memory on your local hardware. Neither your plaintext files nor master passwords are ever transmitted or logged.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pillar Badges Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillarItem(
                    icon = Icons.Default.Shield,
                    title = "AES-256-GCM Authenticated Encryption",
                    desc = "Galois/Counter Mode provides both 256-bit confidentiality and 128-bit integrity tag verification against tampering."
                )
                PillarItem(
                    icon = Icons.Default.Key,
                    title = "Hardened PBKDF2 Key Derivation",
                    desc = "Up to 600,000 SHA-512 iterations with a cryptographically secure 32-byte salt to resist brute-force and ASIC attacks."
                )
                PillarItem(
                    icon = Icons.Default.Lock,
                    title = "Physical Keyfile 2-Factor Authentication",
                    desc = "Optionally combine any binary file (image, song, key) as a second required factor for unlocking your vault."
                )
                PillarItem(
                    icon = Icons.Default.AirplanemodeActive,
                    title = "100% Offline & Portable",
                    desc = "Cross-compatible with ZevSafe web apps and PowerShell scripts. Safe for air-gapped forensic environments."
                )
            }
        }

        // Format Specifications
        CyberCard(
            title = "Vault Binary Specifications",
            subtitle = "Binary structure of .zev files",
            tag = "SPECS",
            accent = CardAccent.TEAL
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SpecBox(
                    version = "v1 Standard Vault Format",
                    headerBytes = "[ Salt (16B) | IV (12B) | Ciphertext + GCM Tag (16B) ]",
                    notes = "PBKDF2-HMAC-SHA256 with 100,000 iterations."
                )

                SpecBox(
                    version = "v2 Enhanced Vault Format",
                    headerBytes = "[ Magic 'ZV2\\0' (4B) | Ver 0x02 (1B) | Flags (1B) | Salt (32B) | IV (12B) | Ciphertext + Tag ]",
                    notes = "PBKDF2-HMAC-SHA512 with 600,000 iterations + SHA-256 Keyfile 2FA."
                )
            }
        }

        // FAQ Section
        CyberCard(
            title = "Frequently Asked Questions",
            tag = "FAQ",
            accent = CardAccent.PURPLE
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FaqItem(
                    q = "Can ZevSafe restore my password if I forget it?",
                    a = "No. By cryptographic design, zero-knowledge encryption means there is no master recovery key, backdoor, or admin reset. Always export your Password Recovery Sheet."
                )
                FaqItem(
                    q = "Can I open .zev vaults created on my PC?",
                    a = "Yes! .zev vaults created by ZevSafe web, PowerShell, or Android share identical binary formats and are 100% interchangeable."
                )
                FaqItem(
                    q = "How does Smart Compression work?",
                    a = "Pre-compressed media (JPEG, MP4, MKV, ZIP, PDF) is stored uncompressed to maximize speed, while documents and code are compressed via fast DEFLATE."
                )
            }
        }
    }
}

@Composable
private fun PillarItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PurplePrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = desc, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SpecBox(version: String, headerBytes: String, notes: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = version, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealLight)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = headerBytes,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = notes, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column {
        Text(text = "Q: $q", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PurpleLight)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = a, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
    }
}
