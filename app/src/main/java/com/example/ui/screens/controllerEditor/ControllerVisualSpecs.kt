package com.example.ui.screens.controllerEditor

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * ControllerVisualSpecs defines the single source of truth for all visual properties,
 * geometric ratios, color definitions, typography, and dimension specifications across both:
 * 1. ControllerEditorScreen (Jetpack Compose UI)
 * 2. NativeVirtualControllerView (In-Game Android Native Canvas)
 *
 * Guarantees pixel-perfect visual parity between what the user configures in the editor
 * and what is displayed during real game runtime.
 */
object ControllerVisualSpecs {

    // --- ACCENT FIGHTING GAME COLORS ---
    val COLOR_BTN_X = Color.rgb(0, 176, 255)       // Electric Blue #00B0FF
    val COLOR_BTN_Y = Color.rgb(255, 179, 0)       // Golden Yellow #FFB300
    val COLOR_BTN_Z = Color.rgb(170, 0, 255)       // Neon Purple #AA00FF
    val COLOR_BTN_A = Color.rgb(255, 23, 68)       // Crimson Red #FF1744
    val COLOR_BTN_B = Color.rgb(0, 230, 118)       // Emerald Green #00E676
    val COLOR_BTN_C = Color.rgb(255, 109, 0)       // Radiant Orange #FF6D00
    val COLOR_BTN_D = Color.rgb(0, 191, 165)       // Mint Teal #00BFA5 (LB bumper)
    val COLOR_BTN_W = Color.rgb(236, 64, 122)      // Neon Rose #EC407A (LT trigger)
    val COLOR_BTN_L3 = Color.rgb(100, 181, 246)    // Sky Blue #64B5F6 (L3 thumbstick)
    val COLOR_BTN_R3 = Color.rgb(255, 138, 101)    // Coral #FF8A65 (R3 thumbstick)
    val COLOR_SYSTEM = Color.rgb(255, 32, 40)      // Fighting Crimson #FF2028
    val COLOR_DPAD = Color.rgb(255, 32, 40)        // Fighting Crimson #FF2028
    val COLOR_MACRO = Color.rgb(255, 145, 0)       // Amber Gold #FF9100
    val COLOR_FALLBACK = Color.rgb(229, 29, 36)    // Fighting Accent #E51D24

    // --- METALLIC & CHASSIS PALETTE ---
    const val COLOR_TITANIUM_BEZEL_TOP = 0xFF323544.toInt()
    const val COLOR_TITANIUM_BEZEL_MID = 0xFF1E202A.toInt()
    const val COLOR_TITANIUM_BEZEL_BOT = 0xFF111218.toInt()
    const val COLOR_TITANIUM_RIM_HIGHLIGHT = 0xFF5A5E75.toInt()

    const val COLOR_DISH_DARK_EDGE = 0xFF0D0E14.toInt()

    const val COLOR_OCTAGON_FILL_TOP = 0xFF282A36.toInt()
    const val COLOR_OCTAGON_FILL_MID = 0xFF16171E.toInt()
    const val COLOR_OCTAGON_FILL_BOT = 0xFF0F1015.toInt()
    const val COLOR_OCTAGON_BEVEL_STROKE = 0xFF4A4E62.toInt()

    const val COLOR_HEX_FILL_TOP = 0xFF2E313E.toInt()
    const val COLOR_HEX_FILL_MID = 0xFF181A22.toInt()
    const val COLOR_HEX_FILL_BOT = 0xFF0F1016.toInt()
    const val COLOR_HEX_PRESSED_TOP = 0xFFD50000.toInt()
    const val COLOR_HEX_PRESSED_BOT = 0xFF8A0E13.toInt()

    const val COLOR_CYBER_FILL_TOP = 0xFF241618.toInt()
    const val COLOR_CYBER_FILL_MID = 0xFF140E10.toInt()
    const val COLOR_CYBER_FILL_BOT = 0xFF0C090A.toInt()
    const val COLOR_CYBER_PRESSED_BOT = 0xFF1E140C.toInt()

    const val COLOR_CIRCLE_BODY = 0xFF181A24.toInt()

    const val COLOR_DPAD_DECAL = 0xFFE51D24.toInt()
    const val COLOR_DPAD_HUB_TOP = 0xFF2B2D38.toInt()
    const val COLOR_DPAD_HUB_MID = 0xFF14151B.toInt()
    const val COLOR_DPAD_HUB_BOT = 0xFF0D0E12.toInt()
    const val COLOR_DPAD_HUB_STROKE = 0xFF3B3E4C.toInt()
    const val COLOR_DPAD_HUB_INNER = 0xFF121318.toInt()
    const val COLOR_DPAD_HUB_TICK = 0xFFFF2430.toInt()

    // --- BASE GEOMETRIC DIMENSIONS (in DP) ---
    const val BASE_WIDTH_SYSTEM_DP = 92f
    const val BASE_HEIGHT_SYSTEM_DP = 46f
    const val BASE_WIDTH_DPAD_DP = 64f
    const val BASE_HEIGHT_DPAD_DP = 64f
    const val BASE_WIDTH_MACRO_DP = 76f
    const val BASE_HEIGHT_MACRO_DP = 66f
    const val BASE_WIDTH_ACTION_DP = 66f
    const val BASE_HEIGHT_ACTION_DP = 66f

    /**
     * Resolves the unscaled base width and height in dp for any button.
     */
    fun getBaseDimensionsDp(id: String, isMacro: Boolean): Pair<Float, Float> {
        return when {
            id == "btn_start" || id == "btn_menu" -> Pair(BASE_WIDTH_SYSTEM_DP, BASE_HEIGHT_SYSTEM_DP)
            id.startsWith("dpad_") -> Pair(BASE_WIDTH_DPAD_DP, BASE_HEIGHT_DPAD_DP)
            id == "btn_l3" || id == "btn_r3" -> Pair(56f, 56f)
            isMacro -> Pair(BASE_WIDTH_MACRO_DP, BASE_HEIGHT_MACRO_DP)
            else -> Pair(BASE_WIDTH_ACTION_DP, BASE_HEIGHT_ACTION_DP)
        }
    }

    /**
     * Resolves the effective rendering geometry for a button based on user selection or defaults.
     */
    fun resolveEffectiveShape(shape: String, id: String, isMacro: Boolean): String {
        return when (shape.uppercase()) {
            "CIRCLE" -> "CIRCLE"
            "OCTAGON" -> "OCTAGON"
            "CYBER" -> "CYBER"
            "DEFAULT" -> when {
                id.startsWith("dpad_") -> "OCTAGON"
                id == "btn_start" || id == "btn_menu" -> "CYBER_HEX"
                id == "btn_l3" || id == "btn_r3" -> "OCTAGON"
                isMacro -> "CYBER"
                else -> "ARCADE"
            }
            else -> "ARCADE"
        }
    }

    /**
     * Resolves the button's accent fighting color as an ARGB Int.
     */
    fun getButtonAccentColorInt(id: String): Int {
        return when (id) {
            "btn_x" -> COLOR_BTN_X
            "btn_y" -> COLOR_BTN_Y
            "btn_z" -> COLOR_BTN_Z
            "btn_a" -> COLOR_BTN_A
            "btn_b" -> COLOR_BTN_B
            "btn_c" -> COLOR_BTN_C
            "btn_d" -> COLOR_BTN_D
            "btn_w" -> COLOR_BTN_W
            "btn_l3" -> COLOR_BTN_L3
            "btn_r3" -> COLOR_BTN_R3
            "btn_start", "btn_menu" -> COLOR_SYSTEM
            "dpad_up", "dpad_down", "dpad_left", "dpad_right" -> COLOR_DPAD
            else -> if (id.startsWith("macro_")) COLOR_MACRO else COLOR_FALLBACK
        }
    }

    /**
     * Returns the sub-glyph / sub-icon for system buttons.
     */
    fun getButtonSubIcon(id: String): String? {
        return when (id) {
            "btn_start" -> "[-]"
            "btn_menu" -> "[≡]"
            else -> null
        }
    }

    /**
     * Helper to multiply alpha channel by a factor, keeping RGB components intact.
     */
    fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun adjustAlpha(color: Long, factor: Float): Int {
        return adjustAlpha(color.toInt(), factor)
    }
}
