package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.utils.bounceClick

@Composable
fun MugenPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    gradient: Brush = MugenRedButtonVerticalGradient
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .height(height)
            .bounceClick()
            .clip(CyberCutMedium)
            .background(if (enabled) gradient else Brush.linearGradient(listOf(Color(0xFF4A1014), Color(0xFF2A1012))))
            .border(1.5.dp, if (enabled) MugenRedGlow else MugenBorderSubtle, CyberCutMedium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
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
                    tint = MugenWhitePrimary.copy(alpha = alpha),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = MugenWhitePrimary.copy(alpha = alpha),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MugenSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    borderColor: Color = MugenRedBorderBright,
    containerColor: Color = MugenSurfaceCard,
    contentColor: Color = MugenWhitePrimary,
    height: Dp = 48.dp,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(height)
            .bounceClick()
            .clip(CyberCutSmall)
            .background(containerColor)
            .border(1.dp, borderColor, CyberCutSmall)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
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
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MugenIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    borderColor: Color = MugenRedBorderBright,
    containerColor: Color = MugenSurfaceCard,
    tint: Color = MugenRedPrimary,
    isSelected: Boolean = false
) {
    val bg = if (isSelected) MugenRedPrimary else containerColor
    val border = if (isSelected) MugenRedGlow else borderColor
    val iconColor = if (isSelected) MugenWhitePrimary else tint

    Box(
        modifier = modifier
            .size(size)
            .bounceClick()
            .clip(CyberCutSmall)
            .background(bg)
            .border(1.dp, border, CyberCutSmall)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
