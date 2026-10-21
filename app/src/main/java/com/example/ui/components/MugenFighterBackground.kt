package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.MugenBgDeep
import com.example.ui.theme.MugenRedGlow
import com.example.ui.theme.MugenRedPrimary

@Composable
fun MugenFighterBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0.35f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MugenBgDeep)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-center intense red fighting-energy radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MugenRedPrimary.copy(alpha = 0.28f * intensity),
                        MugenRedGlow.copy(alpha = 0.12f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.25f),
                    radius = w * 0.85f
                )
            )

            // Bottom-right secondary energy flare
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8A0E13).copy(alpha = 0.22f * intensity),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.9f, h * 0.8f),
                    radius = w * 0.65f
                )
            )

            // Subtle dark diagonal slash lines across the background
            val strokeW = 1.5f
            for (i in -2..6) {
                val startX = i * (w / 3.5f)
                drawLine(
                    color = Color.White.copy(alpha = 0.015f),
                    start = Offset(startX, 0f),
                    end = Offset(startX + h * 0.45f, h),
                    strokeWidth = strokeW
                )
            }
        }

        content()
    }
}
