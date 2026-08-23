package com.example.zevsafe.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zevsafe.ui.components.AmbientBackground
import com.example.zevsafe.ui.theme.BgDark
import com.example.zevsafe.ui.theme.CardBorder
import com.example.zevsafe.ui.theme.PurpleLight
import com.example.zevsafe.ui.theme.PurplePrimary
import com.example.zevsafe.ui.theme.Success
import com.example.zevsafe.ui.theme.SurfaceDark
import com.example.zevsafe.ui.theme.TealLight
import com.example.zevsafe.ui.theme.TealPrimary
import com.example.zevsafe.ui.theme.TextMuted
import com.example.zevsafe.ui.theme.TextPrimary
import com.example.zevsafe.ui.theme.TextSecondary
import com.example.zevsafe.viewmodel.VaultViewModel

enum class NavigationTab(val title: String, val icon: ImageVector, val tag: String) {
    ENCRYPT("Encrypt", Icons.Default.Lock, "tab_encrypt"),
    DECRYPT("Decrypt", Icons.Default.LockOpen, "tab_decrypt"),
    BROWSER("Explorer", Icons.Default.Folder, "tab_browser"),
    GUIDE("Guide", Icons.Default.MenuBook, "tab_guide")
}

@Composable
fun HomeScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(NavigationTab.ENCRYPT) }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    AmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    NavigationTab.values().forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (tab == NavigationTab.DECRYPT || tab == NavigationTab.BROWSER) TealLight else PurpleLight,
                                selectedTextColor = if (tab == NavigationTab.DECRYPT || tab == NavigationTab.BROWSER) TealLight else PurpleLight,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = if (tab == NavigationTab.DECRYPT || tab == NavigationTab.BROWSER) TealPrimary.copy(alpha = 0.2f) else PurplePrimary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .padding(top = statusBarPadding)
            ) {
                // Top App Bar
                TopBrandHeader(
                    onOpenGuide = { currentTab = NavigationTab.GUIDE }
                )

                // Main Content Body with smooth Crossfade
                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                        when (tab) {
                            NavigationTab.ENCRYPT -> EncryptScreen(viewModel = viewModel)
                            NavigationTab.DECRYPT -> DecryptScreen(
                                viewModel = viewModel,
                                onNavigateToBrowser = { currentTab = NavigationTab.BROWSER }
                            )
                            NavigationTab.BROWSER -> VaultBrowserScreen(
                                viewModel = viewModel,
                                onNavigateToDecrypt = { currentTab = NavigationTab.DECRYPT }
                            )
                            NavigationTab.GUIDE -> SecurityGuideScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBrandHeader(
    onOpenGuide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(PurplePrimary, TealPrimary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "ZevSafe Shield",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "ZevSafe",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = TextPrimary
                )
                Text(
                    text = "Zero-Knowledge Offline Vault",
                    fontSize = 10.sp,
                    color = TealLight,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Offline Status Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenGuide)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Success)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "100% Offline",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }
    }
}
