package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class BadgeStyle {
    SUCCESS,
    WARNING,
    DANGER,
    INFO,
    NEUTRAL
}

@Composable
fun MugenStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    style: BadgeStyle = BadgeStyle.SUCCESS,
    icon: ImageVector? = null
) {
    val (bgColor, borderColor, textColor) = when (style) {
        BadgeStyle.SUCCESS -> Triple(MugenGreenSurface, MugenGreenBorder, MugenGreenSuccess)
        BadgeStyle.WARNING -> Triple(MugenOrangeSurface, MugenOrangeBorder, MugenOrangeWarning)
        BadgeStyle.DANGER -> Triple(Color(0xFF4A1014), MugenRedBorderBright, MugenWhitePrimary)
        BadgeStyle.INFO -> Triple(MugenBlueSurface, Color(0xFF1D4E89), MugenBlueInfo)
        BadgeStyle.NEUTRAL -> Triple(MugenSurfaceSecondary, MugenBorderSubtle, MugenTextSecondary)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 12.sp
            )
        }
    }
}
