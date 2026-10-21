package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun MugenCard(
    modifier: Modifier = Modifier,
    shape: Shape = CyberCutMedium,
    borderColor: Color = MugenRedBorder,
    borderWidth: Dp = 1.dp,
    containerColor: Color = MugenSurfaceCard,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(borderWidth, borderColor, shape)
            .then(clickModifier)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun MugenSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    borderColor: Color = MugenRedBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    color = MugenRedPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) {
                    badge()
                }
                if (action != null) {
                    if (badge != null) Spacer(modifier = Modifier.width(8.dp))
                    action()
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Card Body
        MugenCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = borderColor,
            content = content
        )
    }
}
