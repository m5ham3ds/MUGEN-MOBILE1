package com.example.ui.screens.runtime

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.ui.screens.controllerEditor.ControllerButtonState
import com.example.ui.screens.controllerEditor.ControllerPreset
import com.example.ui.screens.controllerEditor.getLabelForId
import com.example.ui.screens.controllerEditor.getPresetLayout
import org.json.JSONArray
import kotlin.math.hypot

/**
 * Native Android View implementing the multi-touch on-screen arcade controller for MUGEN Mobile.
 *
 * Designed to completely replace ComposeView inside SDLActivity's view tree:
 * 1. Zero Compose runtime overhead and zero ViewTreeLifecycleOwner dependencies.
 * 2. High-performance multi-touch handling for fighting game combos and diagonals.
 * 3. Direct dispatch to SDLActivity.onNativeKeyDown / onNativeKeyUp.
 * 4. Full runtime support for custom button shapes (DEFAULT, CIRCLE, OCTAGON, CYBER)
 *    and per-button fighting game accent colors (matching ControllerEditorScreen).
 */
class NativeVirtualControllerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class ButtonModel(
        val id: String,
        var centerX: Float,
        var centerY: Float,
        var radius: Float,
        val label: String,
        var opacity: Float = 0.7f,
        var isEnabled: Boolean = true,
        var isMacro: Boolean = false,
        var shape: String = "DEFAULT"
    )

    var onButtonPressed: ((String) -> Unit)? = null
    var onButtonReleased: ((String) -> Unit)? = null

    private val buttonModels = mutableListOf<ButtonModel>()
    private val pointerButtonMap = HashMap<Int, String>()
    private val pressedButtonIds = HashSet<String>()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * context.resources.displayMetrics.density
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * context.resources.displayMetrics.density
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        isClickable = true
        isFocusable = false
        // Setup initial default layout based on screen metrics
        val dm = context.resources.displayMetrics
        loadDefaultPreset(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && buttonModels.isEmpty()) {
            loadDefaultPreset(w.toFloat(), h.toFloat())
        }
    }

    private fun loadDefaultPreset(width: Float, height: Float) {
        val preset = getPresetLayout(ControllerPreset.SIX_BUTTON, width, height)
        applyControllerButtons(preset)
    }

    /**
     * Updates button layout from saved JSON or defaults.
     * Restores id, coordinates, scale, opacity, enabled, macro, and custom button shapes.
     */
    fun updateLayoutFromJson(jsonStr: String?, width: Float = this.width.toFloat(), height: Float = this.height.toFloat()) {
        val w = if (width > 0) width else context.resources.displayMetrics.widthPixels.toFloat()
        val h = if (height > 0) height else context.resources.displayMetrics.heightPixels.toFloat()

        if (!jsonStr.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                val loaded = mutableListOf<ControllerButtonState>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val isMacro = id.startsWith("macro_")
                    val isEnabled = if (obj.has("enabled")) obj.getBoolean("enabled") else !isMacro
                    val scale = if (obj.has("scale")) obj.getDouble("scale").toFloat() else 1.0f
                    val opacity = if (obj.has("opacity")) obj.getDouble("opacity").toFloat() else 0.7f
                    val shape = obj.optString("shape", "DEFAULT")

                    loaded.add(
                        ControllerButtonState(
                            id = id,
                            initialX = obj.getDouble("x").toFloat(),
                            initialY = obj.getDouble("y").toFloat(),
                            label = getLabelForId(id),
                            scale = scale,
                            opacity = opacity,
                            isEnabled = isEnabled,
                            isMacro = isMacro,
                            shape = shape
                        )
                    )
                }
                if (loaded.isNotEmpty()) {
                    applyControllerButtons(loaded)
                    return
                }
            } catch (e: Exception) {
                // Fall back to default preset on error
            }
        }
        loadDefaultPreset(w, h)
    }

    fun applyControllerButtons(buttons: List<ControllerButtonState>) {
        val density = context.resources.displayMetrics.density
        buttonModels.clear()
        for (btn in buttons) {
            // Only add enabled buttons to active touch and render models
            if (!btn.isEnabled) continue

            val baseDp = if (btn.label.length > 2) 80f else if (btn.label.length > 1) 70f else 64f
            val sizePx = baseDp * density * btn.scale
            val radius = sizePx / 2f
            val cx = btn.initialX + radius
            val cy = btn.initialY + radius
            buttonModels.add(
                ButtonModel(
                    id = btn.id,
                    centerX = cx,
                    centerY = cy,
                    radius = radius,
                    label = btn.label,
                    opacity = btn.opacity,
                    isEnabled = btn.isEnabled,
                    isMacro = btn.isMacro,
                    shape = btn.shape
                )
            )
        }
        invalidate()
    }

    fun findButtonAt(x: Float, y: Float): ButtonModel? {
        var closestBtn: ButtonModel? = null
        var minNormalizedDist = Float.MAX_VALUE

        // Select the button whose center is closest to the touch point, preventing accidental cross-talk
        for (b in buttonModels) {
            val dist = hypot((x - b.centerX).toDouble(), (y - b.centerY).toDouble()).toFloat()
            val maxAllowed = b.radius * 1.15f
            if (dist <= maxAllowed) {
                val normalizedDist = dist / b.radius
                if (normalizedDist < minNormalizedDist) {
                    minNormalizedDist = normalizedDist
                    closestBtn = b
                }
            }
        }
        return closestBtn
    }

    fun getPressedButtonIds(): Set<String> = HashSet(pressedButtonIds)

    fun getButtonModels(): List<ButtonModel> = ArrayList(buttonModels)

    fun releaseAllPressedButtons() {
        for (buttonId in ArrayList(pressedButtonIds)) {
            onButtonReleased?.invoke(buttonId)
        }
        pointerButtonMap.clear()
        pressedButtonIds.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        releaseAllPressedButtons()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = event.getPointerId(actionIndex)
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val btn = findButtonAt(x, y)
                if (btn != null) {
                    pointerButtonMap[pointerId] = btn.id
                    if (pressedButtonIds.add(btn.id)) {
                        onButtonPressed?.invoke(btn.id)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                var changed = false
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val prevButtonId = pointerButtonMap[pointerId]
                    val currentBtn = findButtonAt(x, y)
                    val newButtonId = currentBtn?.id

                    if (prevButtonId != newButtonId) {
                        if (prevButtonId != null) {
                            pointerButtonMap.remove(pointerId)
                            if (!pointerButtonMap.values.contains(prevButtonId)) {
                                pressedButtonIds.remove(prevButtonId)
                                onButtonReleased?.invoke(prevButtonId)
                            }
                        }
                        if (newButtonId != null) {
                            pointerButtonMap[pointerId] = newButtonId
                            if (pressedButtonIds.add(newButtonId)) {
                                onButtonPressed?.invoke(newButtonId)
                            }
                        }
                        changed = true
                    }
                }
                if (changed) invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                val buttonId = pointerButtonMap.remove(pointerId)
                if (buttonId != null) {
                    if (!pointerButtonMap.values.contains(buttonId)) {
                        pressedButtonIds.remove(buttonId)
                        onButtonReleased?.invoke(buttonId)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseAllPressedButtons()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * Resolves the button's accent fighting color.
     */
    private fun getButtonAccentColor(id: String): Int {
        return when (id) {
            "btn_x" -> Color.rgb(0, 176, 255)       // Electric Blue
            "btn_y" -> Color.rgb(255, 179, 0)       // Golden Yellow
            "btn_z" -> Color.rgb(170, 0, 255)       // Neon Purple
            "btn_a" -> Color.rgb(255, 23, 68)       // Crimson Red
            "btn_b" -> Color.rgb(0, 230, 118)       // Emerald Green
            "btn_c" -> Color.rgb(255, 109, 0)       // Radiant Orange
            "btn_start", "btn_menu" -> Color.rgb(255, 32, 40)
            "dpad_up", "dpad_down", "dpad_left", "dpad_right" -> Color.rgb(255, 32, 40)
            else -> if (id.startsWith("macro_")) Color.rgb(255, 145, 0) else Color.rgb(237, 37, 45)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (btn in buttonModels) {
            val isPressed = pressedButtonIds.contains(btn.id)
            val alphaMult = btn.opacity.coerceIn(0.1f, 1.0f)
            val accentColor = getButtonAccentColor(btn.id)

            // Determine effective shape (DEFAULT, CIRCLE, OCTAGON, CYBER, or CYBER_HEX for system)
            val effectiveShape = when (btn.shape.uppercase()) {
                "CIRCLE" -> "CIRCLE"
                "OCTAGON" -> "OCTAGON"
                "CYBER" -> "CYBER"
                "DEFAULT" -> when {
                    btn.id.startsWith("dpad_") -> "OCTAGON"
                    btn.id == "btn_start" || btn.id == "btn_menu" -> "CYBER_HEX"
                    btn.isMacro -> "CYBER"
                    else -> "ARCADE"
                }
                else -> "ARCADE"
            }

            val cx = btn.centerX
            val cy = btn.centerY
            val r = btn.radius

            when (effectiveShape) {
                "OCTAGON" -> {
                    drawOctagonButton(canvas, cx, cy, r, btn.label, accentColor, isPressed, alphaMult)
                }
                "CYBER" -> {
                    drawCyberButton(canvas, cx, cy, r, btn.label, accentColor, isPressed, alphaMult)
                }
                "CYBER_HEX" -> {
                    drawCyberHexButton(canvas, cx, cy, r, btn.label, accentColor, isPressed, alphaMult)
                }
                "CIRCLE", "ARCADE" -> {
                    drawCircleButton(canvas, cx, cy, r, btn.label, accentColor, isPressed, alphaMult)
                }
            }
        }
    }

    /**
     * Renders a circular arcade-style button with titanium bezel and glowing neon ring.
     */
    private fun drawCircleButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        label: String,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        // 1. Titanium Bezel
        fillPaint.color = Color.argb((180 * alphaMult).toInt(), 30, 32, 42)
        canvas.drawCircle(cx, cy, r, fillPaint)

        // Bezel rim highlight
        borderPaint.color = Color.argb((140 * alphaMult).toInt(), 80, 85, 105)
        canvas.drawCircle(cx, cy, r, borderPaint)

        // 2. Colored Neon Ring
        val ringR = r * 0.88f
        val neonAlpha = if (isPressed) 255 else (180 * alphaMult).toInt()
        glowPaint.color = Color.argb(neonAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawCircle(cx, cy, ringR, glowPaint)

        // 3. Concave Colored Dish
        val dishR = r * 0.78f
        val dishFillAlpha = if (isPressed) (230 * alphaMult).toInt() else (120 * alphaMult).toInt()
        fillPaint.color = Color.argb(dishFillAlpha, Color.red(accentColor) / 2, Color.green(accentColor) / 2, Color.blue(accentColor) / 2)
        canvas.drawCircle(cx, cy, dishR, fillPaint)

        // 4. Text Label
        drawCenteredText(canvas, cx, cy, r, label, alphaMult)
    }

    /**
     * Renders a 3D faceted octagonal button.
     */
    private fun drawOctagonButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        label: String,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val cut = r * 0.32f
        val octPath = Path().apply {
            moveTo(cx - r + cut, cy - r)
            lineTo(cx + r - cut, cy - r)
            lineTo(cx + r, cy - r + cut)
            lineTo(cx + r, cy + r - cut)
            lineTo(cx + r - cut, cy + r)
            lineTo(cx - r + cut, cy + r)
            lineTo(cx - r, cy + r - cut)
            lineTo(cx - r, cy - r + cut)
            close()
        }

        // Dark graphite metallic body fill
        val bodyAlpha = if (isPressed) (220 * alphaMult).toInt() else (160 * alphaMult).toInt()
        val fillR = if (isPressed) Color.red(accentColor) / 2 else 28
        val fillG = if (isPressed) Color.green(accentColor) / 2 else 30
        val fillB = if (isPressed) Color.blue(accentColor) / 2 else 38
        fillPaint.color = Color.argb(bodyAlpha, fillR, fillG, fillB)
        canvas.drawPath(octPath, fillPaint)

        // Specular outer border
        borderPaint.color = Color.argb((120 * alphaMult).toInt(), 80, 85, 105)
        canvas.drawPath(octPath, borderPaint)

        // Glowing perimeter neon line
        val neonAlpha = if (isPressed) 255 else (200 * alphaMult).toInt()
        glowPaint.color = Color.argb(neonAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawPath(octPath, glowPaint)

        // Text Label
        drawCenteredText(canvas, cx, cy, r, label, alphaMult)
    }

    /**
     * Renders a chamfered cyber polygon with red glowing accents.
     */
    private fun drawCyberButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        label: String,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val cut = r * 0.35f
        val cyberPath = Path().apply {
            moveTo(cx - r + cut, cy - r)
            lineTo(cx + r - cut, cy - r)
            lineTo(cx + r, cy - r + cut)
            lineTo(cx + r, cy + r - cut)
            lineTo(cx + r - cut, cy + r)
            lineTo(cx - r + cut, cy + r)
            lineTo(cx - r, cy + r - cut)
            lineTo(cx - r, cy - r + cut)
            close()
        }

        val bodyAlpha = if (isPressed) (220 * alphaMult).toInt() else (150 * alphaMult).toInt()
        val fillR = if (isPressed) Color.red(accentColor) / 2 else 22
        val fillG = if (isPressed) Color.green(accentColor) / 2 else 24
        val fillB = if (isPressed) Color.blue(accentColor) / 2 else 32
        fillPaint.color = Color.argb(bodyAlpha, fillR, fillG, fillB)
        canvas.drawPath(cyberPath, fillPaint)

        // Cyber neon border
        val neonAlpha = if (isPressed) 255 else (210 * alphaMult).toInt()
        borderPaint.color = Color.argb(neonAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawPath(cyberPath, borderPaint)

        // Corner tick brackets
        val bracketLen = r * 0.30f
        val bPaint = Paint(borderPaint).apply {
            strokeWidth = 3f * context.resources.displayMetrics.density
            color = Color.argb(neonAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        }
        canvas.drawLine(cx - r, cy - r + cut, cx - r, cy - r + cut + bracketLen, bPaint)
        canvas.drawLine(cx + r, cy - r + cut, cx + r, cy - r + cut + bracketLen, bPaint)
        canvas.drawLine(cx - r, cy + r - cut, cx - r, cy + r - cut - bracketLen, bPaint)
        canvas.drawLine(cx + r, cy + r - cut, cx + r, cy + r - cut - bracketLen, bPaint)

        // Text Label
        drawCenteredText(canvas, cx, cy, r, label, alphaMult)
    }

    /**
     * Renders an elongated horizontal hex button (for START / MENU).
     */
    private fun drawCyberHexButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        label: String,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val hw = r * 1.35f
        val hh = r * 0.75f
        val cut = hh * 0.40f

        val hexPath = Path().apply {
            moveTo(cx - hw + cut, cy - hh)
            lineTo(cx + hw - cut, cy - hh)
            lineTo(cx + hw, cy)
            lineTo(cx + hw - cut, cy + hh)
            lineTo(cx - hw + cut, cy + hh)
            lineTo(cx - hw, cy)
            close()
        }

        val bodyAlpha = if (isPressed) (220 * alphaMult).toInt() else (160 * alphaMult).toInt()
        val fillR = if (isPressed) 180 else 24
        val fillG = if (isPressed) 30 else 26
        val fillB = if (isPressed) 35 else 34
        fillPaint.color = Color.argb(bodyAlpha, fillR, fillG, fillB)
        canvas.drawPath(hexPath, fillPaint)

        // Neon border
        val neonAlpha = if (isPressed) 255 else (220 * alphaMult).toInt()
        borderPaint.color = Color.argb(neonAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawPath(hexPath, borderPaint)

        // Text Label
        drawCenteredText(canvas, cx, cy, r * 0.85f, label, alphaMult)
    }

    /**
     * Draws centered high-contrast typography with drop shadow.
     */
    private fun drawCenteredText(canvas: Canvas, cx: Float, cy: Float, r: Float, label: String, alphaMult: Float) {
        val textPx = when {
            label.length > 4 -> (r * 0.45f).coerceIn(12f, 26f)
            label.length > 2 -> (r * 0.55f).coerceIn(14f, 32f)
            label.length > 1 -> (r * 0.65f).coerceIn(16f, 36f)
            else -> (r * 0.75f).coerceIn(18f, 44f)
        }

        textPaint.textSize = textPx
        textPaint.alpha = (255 * alphaMult).toInt()
        textShadowPaint.textSize = textPx
        textShadowPaint.alpha = (200 * alphaMult).toInt()

        val fontMetrics = textPaint.fontMetrics
        val baseline = cy - (fontMetrics.descent + fontMetrics.ascent) / 2f

        // Drop shadow
        canvas.drawText(label, cx + 1.5f, baseline + 1.5f, textShadowPaint)
        // Foreground
        canvas.drawText(label, cx, baseline, textPaint)
    }
}
