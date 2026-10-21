package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun MugenTopBar(
    title: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(58.dp)
            .background(MugenTopBarGradient)
            .mugenTopBarDivider()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Left: Logo
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MugenLogo()
        }

        // Center: Title with illuminated red underline pill
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = MugenWhitePrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MugenRedPrimary)
            )
        }

        // Right: Menu Action Button
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MugenWhitePrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
