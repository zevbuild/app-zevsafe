package com.zevbuild.zevsafe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zevbuild.zevsafe.ui.theme.Danger
import com.zevbuild.zevsafe.ui.theme.Info
import com.zevbuild.zevsafe.ui.theme.Success
import com.zevbuild.zevsafe.ui.theme.Warning

enum class PasswordStrengthLevel(val label: String, val color: Color, val fraction: Float) {
    VERY_WEAK("Very Weak", Danger, 0.2f),
    WEAK("Weak", Color(0xFFF97316), 0.4f),
    FAIR("Fair", Warning, 0.6f),
    STRONG("Strong", Success, 0.8f),
    VERY_STRONG("Very Strong", Info, 1.0f)
}

object PasswordEvaluator {
    fun evaluate(password: String): PasswordStrengthLevel {
        if (password.isEmpty()) return PasswordStrengthLevel.VERY_WEAK
        var score = 0
        if (password.length >= 6) score++
        if (password.length >= 10) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when (score) {
            0, 1 -> PasswordStrengthLevel.VERY_WEAK
            2 -> PasswordStrengthLevel.WEAK
            3 -> PasswordStrengthLevel.FAIR
            4 -> PasswordStrengthLevel.STRONG
            else -> PasswordStrengthLevel.VERY_STRONG
        }
    }
}

@Composable
fun PasswordStrengthMeter(
    password: CharSequence,
    modifier: Modifier = Modifier
) {
    if (password.isEmpty()) return

    val level = PasswordEvaluator.evaluate(password.toString())
    val animatedProgress by animateFloatAsState(targetValue = level.fraction, label = "pw_strength_pct")
    val animatedColor by animateColorAsState(targetValue = level.color, label = "pw_strength_color")

    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password Strength",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = level.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = animatedColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(animatedColor)
            )
        }
    }
}
