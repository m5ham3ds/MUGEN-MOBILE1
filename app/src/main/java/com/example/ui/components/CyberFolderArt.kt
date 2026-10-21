package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun CyberFolderArt(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(240.dp, 200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Red glowing atmospheric aura behind
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(MugenRedGlow.copy(alpha = 0.25f), Color.Transparent),
                    center = center,
                    radius = size.width * 0.45f
                )
            )
        }

        // Card 1 (Rotated left)
        Box(
            modifier = Modifier
                .offset(x = (-32).dp, y = (-24).dp)
                .rotate(-14f)
                .size(76.dp, 108.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF14151B))
                .border(1.dp, MugenRedBorder, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsKabaddi,
                contentDescription = null,
                tint = MugenRedPrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(36.dp)
            )
        }

        // Card 2 (Rotated right)
        Box(
            modifier = Modifier
                .offset(x = 32.dp, y = (-24).dp)
                .rotate(14f)
                .size(76.dp, 108.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF14151B))
                .border(1.dp, MugenRedBorder, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsKabaddi,
                contentDescription = null,
                tint = MugenRedPrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(36.dp)
            )
        }

        // Card 3 (Center)
        Box(
            modifier = Modifier
                .offset(y = (-32).dp)
                .size(84.dp, 116.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F2028))
                .border(1.5.dp, MugenRedGlow.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsKabaddi,
                contentDescription = null,
                tint = MugenRedPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(42.dp)
            )
        }

        // Cybernetic Front Folder
        Box(
            modifier = Modifier
                .offset(y = 16.dp)
                .size(175.dp, 110.dp)
                .clip(CyberCutMedium)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF23242E), Color(0xFF14151C), Color(0xFF0C0D12))
                    )
                )
                .border(1.5.dp, MugenRedPrimary, CyberCutMedium),
            contentAlignment = Alignment.Center
        ) {
            // Folder top tab highlight
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, 10.dp.toPx())
                    lineTo(45.dp.toPx(), 10.dp.toPx())
                    lineTo(55.dp.toPx(), 22.dp.toPx())
                    lineTo(size.width, 22.dp.toPx())
                }
                drawPath(
                    path = path,
                    color = MugenRedGlow.copy(alpha = 0.6f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MugenLogo(isLarge = false)
            }
        }
    }
}
