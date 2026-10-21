package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

// Standard Cybernetic & Chamfered Shapes for MUGEN Mobile
val CyberCutSmall = CutCornerShape(6.dp)
val CyberCutMedium = CutCornerShape(10.dp)
val CyberCutLarge = CutCornerShape(14.dp)
val CyberCutExtraLarge = CutCornerShape(18.dp)

val CyberRoundedCard = RoundedCornerShape(8.dp)
val CyberRoundedBadge = RoundedCornerShape(4.dp)

/**
 * Draws a sharp fighting-game cybernetic border with optional notched corners
 * and tactical corner brackets.
 */
fun Modifier.mugenCyberBorder(
    strokeWidth: Dp = 1.dp,
    color: Color = MugenRedBorderBright,
    cutSize: Dp = 10.dp,
    showBrackets: Boolean = false,
    bracketColor: Color = MugenRedGlow
): Modifier = this.drawBehind {
    val strokePx = strokeWidth.toPx()
    val cutPx = cutSize.toPx()
    val w = size.width
    val h = size.height

    if (cutPx > 0f) {
        val path = Path().apply {
            moveTo(cutPx, 0f)
            lineTo(w - cutPx, 0f)
            lineTo(w, cutPx)
            lineTo(w, h - cutPx)
            lineTo(w - cutPx, h)
            lineTo(cutPx, h)
            lineTo(0f, h - cutPx)
            lineTo(0f, cutPx)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = strokePx))

        if (showBrackets) {
            val bracketLen = 14.dp.toPx()
            val bStroke = 2.5.dp.toPx()
            // Top-left bracket notch
            drawLine(bracketColor, Offset(0f, cutPx), Offset(0f, cutPx + bracketLen), strokeWidth = bStroke)
            drawLine(bracketColor, Offset(cutPx, 0f), Offset(cutPx + bracketLen, 0f), strokeWidth = bStroke)
            // Bottom-right bracket notch
            drawLine(bracketColor, Offset(w, h - cutPx), Offset(w, h - cutPx - bracketLen), strokeWidth = bStroke)
            drawLine(bracketColor, Offset(w - cutPx, h), Offset(w - cutPx - bracketLen, h), strokeWidth = bStroke)
        }
    } else {
        drawRect(color = color, style = Stroke(width = strokePx))
    }
}

/**
 * Draws the cyber divider line seen underneath the TopBar with center mechanical notches.
 */
fun Modifier.mugenTopBarDivider(
    baseColor: Color = MugenBorderSubtle,
    accentColor: Color = MugenRedPrimary
): Modifier = this.drawBehind {
    val h = size.height
    val w = size.width
    val centerY = h - 1.dp.toPx()

    // Base subtle border across full width
    drawLine(
        color = baseColor,
        start = Offset(0f, centerY),
        end = Offset(w, centerY),
        strokeWidth = 1.dp.toPx()
    )

    // Center illuminated red cyber accent with stepped notches
    val center = w / 2f
    val segmentW = 40.dp.toPx()
    val notchW = 12.dp.toPx()
    val notchH = 3.dp.toPx()

    val path = Path().apply {
        moveTo(center - segmentW, centerY)
        lineTo(center - notchW, centerY)
        lineTo(center - notchW + notchH, centerY + notchH)
        lineTo(center + notchW - notchH, centerY + notchH)
        lineTo(center + notchW, centerY)
        lineTo(center + segmentW, centerY)
    }

    drawPath(
        path = path,
        color = accentColor,
        style = Stroke(width = 1.5.dp.toPx())
    )
}

/**
 * Illuminated Japanese Torii Gate icon matching Reference D (Stages screen top badge).
 */
@androidx.compose.runtime.Composable
fun ToriiGateIcon(
    modifier: Modifier = Modifier.size(36.dp),
    color: Color = MugenRedPrimary,
    glowColor: Color = MugenRedGlow
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val strokeThick = 2.5.dp.toPx()
        val strokeMed = 1.8.dp.toPx()

        // 1. Top Kasagi beam (curving slightly up at the ends or extending out)
        val topBeamY = h * 0.22f
        val topPath = Path().apply {
            moveTo(w * 0.05f, topBeamY + h * 0.04f)
            quadraticTo(w * 0.5f, topBeamY - h * 0.03f, w * 0.95f, topBeamY + h * 0.04f)
        }
        drawPath(path = topPath, color = glowColor, style = Stroke(width = strokeThick))

        // 2. Second horizontal beam (Nuki)
        val secondBeamY = h * 0.38f
        drawLine(
            color = color,
            start = Offset(w * 0.14f, secondBeamY),
            end = Offset(w * 0.86f, secondBeamY),
            strokeWidth = strokeMed
        )

        // 3. Center strut (Gakuzuka)
        drawLine(
            color = glowColor,
            start = Offset(w * 0.5f, topBeamY),
            end = Offset(w * 0.5f, secondBeamY),
            strokeWidth = strokeMed
        )

        // 4. Two vertical pillars (Hashira)
        val leftX = w * 0.28f
        val rightX = w * 0.72f
        val pillarBottom = h * 0.88f

        drawLine(
            color = color,
            start = Offset(leftX, topBeamY),
            end = Offset(leftX, pillarBottom),
            strokeWidth = strokeThick
        )
        drawLine(
            color = color,
            start = Offset(rightX, topBeamY),
            end = Offset(rightX, pillarBottom),
            strokeWidth = strokeThick
        )

        // 5. Pillar base pedestals (Daiishi)
        val baseW = w * 0.10f
        val baseH = h * 0.08f
        drawRect(
            color = glowColor,
            topLeft = Offset(leftX - baseW / 2, pillarBottom - baseH / 2),
            size = androidx.compose.ui.geometry.Size(baseW, baseH)
        )
        drawRect(
            color = glowColor,
            topLeft = Offset(rightX - baseW / 2, pillarBottom - baseH / 2),
            size = androidx.compose.ui.geometry.Size(baseW, baseH)
        )
    }
}

