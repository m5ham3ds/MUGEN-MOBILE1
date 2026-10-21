package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun createDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    onPrimary = Color.White,
    primaryContainer = MugenRedDark,
    onPrimaryContainer = Color.White,
    secondary = MugenRedGlow,
    onSecondary = Color.White,
    secondaryContainer = MugenSurfaceSecondary,
    onSecondaryContainer = MugenWhitePrimary,
    tertiary = MugenOrangeWarning,
    onTertiary = Color.Black,
    background = MugenBgDeep,
    onBackground = MugenWhitePrimary,
    surface = MugenBgContent,
    onSurface = MugenWhitePrimary,
    surfaceVariant = MugenSurfaceCard,
    onSurfaceVariant = MugenTextSecondary,
    surfaceTint = primaryColor,
    outline = MugenBorderSubtle,
    outlineVariant = MugenRedBorder,
    error = MugenRedGlow,
    onError = Color.White,
    errorContainer = Color(0xFF4A1014),
    onErrorContainer = Color(0xFFFFB4AB)
)

private fun createLightColorScheme(primaryColor: Color) = lightColorScheme(
    primary = primaryColor,
    onPrimary = Color.White,
    primaryContainer = primaryColor.copy(alpha = 0.2f),
    onPrimaryContainer = Color.Black,
    secondary = primaryColor,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F4),
    onSecondaryContainer = Color(0xFF1E1E24),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF15161C),
    surface = Color.White,
    onSurface = Color(0xFF15161C),
    surfaceVariant = Color(0xFFE8E9F0),
    onSurfaceVariant = Color(0xFF464852),
    outline = Color(0xFFD0D1DC),
    outlineVariant = Color(0xFFE5C0C2)
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "dark",
    primaryColorString: String = "red",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val primaryColor = when (primaryColorString) {
        "purple" -> PurplePrimary
        "blue" -> BluePrimary
        "green" -> GreenPrimary
        "yellow" -> YellowPrimary
        else -> MugenRedPrimary
    }

    val colorScheme = if (darkTheme) {
        createDarkColorScheme(primaryColor)
    } else {
        createLightColorScheme(primaryColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
