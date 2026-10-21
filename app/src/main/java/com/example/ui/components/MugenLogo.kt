package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MugenRedGlow
import com.example.ui.theme.MugenRedPrimary
import com.example.ui.theme.MugenWhitePrimary

@Composable
fun MugenLogo(
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val mainSize = if (isLarge) 28.sp else 20.sp
    val subSize = if (isLarge) 11.sp else 9.sp
    val letterSpacing = if (isLarge) 4.sp else 3.sp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        val logoText = buildAnnotatedString {
            withStyle(
                style = SpanStyle(
                    color = MugenRedPrimary,
                    fontSize = mainSize,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.SansSerif
                )
            ) {
                append("M")
            }
            withStyle(
                style = SpanStyle(
                    color = MugenWhitePrimary,
                    fontSize = mainSize,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.SansSerif
                )
            ) {
                append("UGEN")
            }
        }

        Text(
            text = logoText,
            lineHeight = mainSize
        )

        Text(
            text = "MOBILE",
            color = MugenWhitePrimary.copy(alpha = 0.85f),
            fontSize = subSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = letterSpacing,
            modifier = Modifier.padding(start = 1.dp, top = 0.dp)
        )
    }
}
