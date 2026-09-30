package com.zevbuild.zevsafe.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.zevbuild.zevsafe.ui.theme.BgDark
import com.zevbuild.zevsafe.ui.theme.PurplePrimary
import com.zevbuild.zevsafe.ui.theme.TealPrimary

@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_orbs")
    val animOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1_anim"
    )
    val animOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2_anim"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = BgDark)

            // Orb 1 - Top Left Purple
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PurplePrimary.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.15f + animOffset1, size.height * 0.12f + animOffset2),
                    radius = size.width * 0.85f
                )
            )

            // Orb 2 - Bottom Right Teal
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(TealPrimary.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.85f + animOffset2, size.height * 0.80f + animOffset1),
                    radius = size.width * 0.75f
                )
            )

            // Orb 3 - Center subtle glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PurplePrimary.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.width * 0.6f
                )
            )
        }

        content()
    }
}
