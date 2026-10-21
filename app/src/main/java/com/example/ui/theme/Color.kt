package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// MUGEN Mobile Fighting-Game Red & Black Visual Identity Palette
// ============================================================================

// Base Canvases & Surfaces
val MugenBgDeep = Color(0xFF08090C)         // Deepest black-slate background
val MugenBgContent = Color(0xFF101115)      // Content container background
val MugenSurfaceCard = Color(0xFF18191E)    // Card surface
val MugenSurfaceSecondary = Color(0xFF222329) // Secondary card / control surface
val MugenSurfaceElevated = Color(0xFF2A2B34)  // Elevated popups, inputs, hover
val MugenSurfaceOverlay = Color(0xFF0E0F14)   // Drawer and modal overlays

// Fighting Red Palette & Accents
val MugenRedPrimary = Color(0xFFED252D)     // Core fighting red
val MugenRedGlow = Color(0xFFFF3038)        // Radiant neon glow red
val MugenRedDark = Color(0xFF8A0E13)        // Deep blood red
val MugenRedBorder = Color(0xFF551216)      // Subtle cybernetic red border
val MugenRedBorderBright = Color(0xFFED252D)// Sharp focused red border
val MugenRedGlowSoft = Color(0x33ED252D)    // Translucent red aura

// High-Contrast Typography
val MugenWhitePrimary = Color(0xFFF5F5F7)   // Primary crisp white text
val MugenTextSecondary = Color(0xFFB8B9C0)  // Muted secondary text
val MugenTextMuted = Color(0xFF757682)      // Quiet tertiary labels / captions

// Semantic Status & Telemetry
val MugenGreenSuccess = Color(0xFF31C764)   // Operational / registered / verified
val MugenGreenSurface = Color(0xFF132B1A)   // Dark green badge background
val MugenGreenBorder = Color(0xFF246637)    // Dark green badge border

val MugenOrangeWarning = Color(0xFFFF9F32)  // Warning / modified / unready
val MugenOrangeSurface = Color(0xFF382312)  // Dark orange badge background
val MugenOrangeBorder = Color(0xFF7A4517)   // Dark orange badge border

val MugenBlueInfo = Color(0xFF2997FF)       // Restored / system informational
val MugenBlueSurface = Color(0xFF12243A)

// Structural Dividers & Hairline Borders
val MugenBorderSubtle = Color(0xFF252630)   // Non-active card/container borders
val MugenBorderActive = Color(0xFFED252D)   // Active cyber border

// Specialized Gradients
val MugenRedButtonGradient = Brush.horizontalGradient(
    listOf(Color(0xFFD61821), Color(0xFFFF3038), Color(0xFFD61821))
)

val MugenRedButtonVerticalGradient = Brush.verticalGradient(
    listOf(Color(0xFFFF3B43), Color(0xFFED252D), Color(0xFF9E1017))
)

val MugenActiveCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF4A1014), Color(0xFF1F1215), Color(0xFF18191E))
)

val MugenCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF1C1D24), Color(0xFF14151B))
)

val MugenTopBarGradient = Brush.verticalGradient(
    listOf(Color(0xFF15161C), Color(0xFF0C0D11))
)

val MugenDrawerGradient = Brush.verticalGradient(
    listOf(Color(0xFF2B0A0E), Color(0xFF121318), Color(0xFF08090C))
)

// Legacy compatibility aliases for existing ViewModels & settings
val RedPrimary = MugenRedPrimary
val PurplePrimary = Color(0xFF8E24AA)
val BluePrimary = Color(0xFF1E88E5)
val GreenPrimary = Color(0xFF43A047)
val YellowPrimary = Color(0xFFFDD835)
