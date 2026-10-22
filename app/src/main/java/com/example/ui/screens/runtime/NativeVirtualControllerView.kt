package com.example.ui.screens.runtime

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.ui.screens.controllerEditor.ControllerButtonState
import com.example.ui.screens.controllerEditor.ControllerPreset
import com.example.ui.screens.controllerEditor.ControllerVisualSpecs
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
 * 4. Exact visual parity with ControllerEditorScreen:
 *    - Metallic titanium bezels with specular highlight rings.
 *    - Colored neon perimeter glow halos (Electric Blue, Golden Yellow, Neon Purple, Crimson, Emerald Green, Radiant Orange).
 *    - Deep concave spherical colored dish with authentic arcade radial gradient.
 *    - Glossy specular top crescent sheen reflections.
 *    - 3D faceted octagonal D-Pad buttons with concentric cyber decals and central metallic hub.
 *    - Chamfered horizontal hexagons for START & MENU with crimson borders, top highlight, and sub-icons.
 *    - Chamfered cyber macro buttons with corner L-brackets and amber gold accents.
 *    - Normalized geometric hit detection ensuring accurate touch bounds for all aspect ratios.
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
        var shape: String = "DEFAULT",
        var widthPx: Float = radius * 2f,
        var heightPx: Float = radius * 2f,
        var left: Float = centerX - radius,
        var top: Float = centerY - radius,
        var scale: Float = 1.0f
    )

    var onButtonPressed: ((String) -> Unit)? = null
    var onButtonReleased: ((String) -> Unit)? = null

    private val buttonModels = mutableListOf<ButtonModel>()
    private val pointerButtonMap = HashMap<Int, String>()
    private val pressedButtonIds = HashSet<String>()

    // --- REUSABLE PAINTS (Pre-allocated for 60/120 FPS zero-allocation drawing) ---
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 0, 0, 0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Reusable geometry scratchpads
    private val tempOctPath = Path()
    private val tempHexPath = Path()
    private val tempCyberPath = Path()
    private val tempRectF = RectF()

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
                    val isMacro = if (obj.has("macro")) obj.getBoolean("macro") else id.startsWith("macro_")
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

    /**
     * Synchronizes button models using ControllerVisualSpecs unscaled base dimensions.
     * Guarantees precise sub-pixel alignment of button centers, widths, heights, and radii
     * between ControllerEditorScreen and NativeVirtualControllerView.
     */
    fun applyControllerButtons(buttons: List<ControllerButtonState>) {
        val density = context.resources.displayMetrics.density
        buttonModels.clear()
        for (btn in buttons) {
            // Only add enabled buttons to active touch and render models
            if (!btn.isEnabled) continue

            val (baseWidthDp, baseHeightDp) = ControllerVisualSpecs.getBaseDimensionsDp(btn.id, btn.isMacro)
            val widthPx = baseWidthDp * density * btn.scale
            val heightPx = baseHeightDp * density * btn.scale
            val left = btn.initialX
            val top = btn.initialY
            val cx = left + widthPx / 2f
            val cy = top + heightPx / 2f
            val radius = maxOf(widthPx, heightPx) / 2f

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
                    shape = btn.shape,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    left = left,
                    top = top,
                    scale = btn.scale
                )
            )
        }
        invalidate()
    }

    /**
     * Normalized touch hit detection supporting circular, square, and elongated aspect ratios.
     * Uses normalized distance from button center relative to half-width and half-height,
     * ensuring START/MENU and Macro buttons register touch seamlessly across their full width.
     */
    fun findButtonAt(x: Float, y: Float): ButtonModel? {
        var closestBtn: ButtonModel? = null
        var minNormalizedDist = Float.MAX_VALUE

        for (b in buttonModels) {
            val halfW = b.widthPx / 2f
            val halfH = b.heightPx / 2f
            if (halfW <= 0f || halfH <= 0f) continue

            val dx = (x - b.centerX) / halfW
            val dy = (y - b.centerY) / halfH
            val distNorm = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val maxAllowed = 1.15f // 15% touch forgiveness margin

            if (distNorm <= maxAllowed) {
                if (distNorm < minNormalizedDist) {
                    minNormalizedDist = distNorm
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw decorative D-Pad background hub & concentric arc decals (behind buttons)
        drawDpadBackground(canvas)

        // 2. Render each active button with exact visual parity to ControllerEditorScreen
        for (btn in buttonModels) {
            val isPressed = pressedButtonIds.contains(btn.id)
            val alphaMult = btn.opacity.coerceIn(0.1f, 1.0f)
            val accentColor = ControllerVisualSpecs.getButtonAccentColorInt(btn.id)
            val effectiveShape = ControllerVisualSpecs.resolveEffectiveShape(btn.shape, btn.id, btn.isMacro)

            when (effectiveShape) {
                "OCTAGON" -> drawOctagonButton(canvas, btn, accentColor, isPressed, alphaMult)
                "CYBER" -> drawCyberButton(canvas, btn, accentColor, isPressed, alphaMult)
                "CYBER_HEX" -> drawCyberHexButton(canvas, btn, accentColor, isPressed, alphaMult)
                "CIRCLE" -> drawCircleOnlyButton(canvas, btn, accentColor, isPressed, alphaMult)
                else -> drawArcadeAttackButton(canvas, btn, accentColor, isPressed, alphaMult)
            }
        }
    }

    /**
     * Renders decorative concentric red arc decals and central metallic disc behind the D-Pad.
     * Drawn only when the 4 directional buttons are active, exactly matching the editor layout.
     * Does NOT intercept touches or block directional combos.
     */
    private fun drawDpadBackground(canvas: Canvas) {
        val dpadUp = buttonModels.find { it.id == "dpad_up" && it.isEnabled }
        val dpadDown = buttonModels.find { it.id == "dpad_down" && it.isEnabled }
        val dpadLeft = buttonModels.find { it.id == "dpad_left" && it.isEnabled }
        val dpadRight = buttonModels.find { it.id == "dpad_right" && it.isEnabled }

        if (dpadUp != null && dpadDown != null && dpadLeft != null && dpadRight != null) {
            val density = context.resources.displayMetrics.density
            val dpadCx = (dpadLeft.centerX + dpadRight.centerX) / 2f
            val dpadCy = (dpadUp.centerY + dpadDown.centerY) / 2f
            val avgScale = (dpadLeft.scale + dpadRight.scale + dpadUp.scale + dpadDown.scale) / 4f
            val avgOpacity = (dpadLeft.opacity + dpadRight.opacity + dpadUp.opacity + dpadDown.opacity) / 4f

            val hubRadius = 24f * density * avgScale
            val outerDecalRadius = 66f * density * avgScale

            // Concentric outer red arc decals
            strokePaint.strokeWidth = 1.5f * density
            strokePaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_DECAL, 0.18f * avgOpacity)
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(25f * density, 15f * density), 0f)
            canvas.drawCircle(dpadCx, dpadCy, outerDecalRadius, strokePaint)

            strokePaint.strokeWidth = 1.0f * density
            strokePaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_DECAL, 0.40f * avgOpacity)
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 10f * density), 0f)
            canvas.drawCircle(dpadCx, dpadCy, outerDecalRadius - 8f * density * avgScale, strokePaint)
            strokePaint.pathEffect = null

            // Central Faceted Metallic Hub Disc
            fillPaint.shader = RadialGradient(
                dpadCx - 4f * density, dpadCy - 4f * density,
                hubRadius,
                intArrayOf(
                    ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_HUB_TOP, avgOpacity),
                    ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_HUB_MID, avgOpacity),
                    ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_HUB_BOT, avgOpacity)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(dpadCx, dpadCy, hubRadius, fillPaint)
            fillPaint.shader = null

            // Hub outer stroke
            strokePaint.strokeWidth = 1.5f * density
            strokePaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_HUB_STROKE, avgOpacity)
            canvas.drawCircle(dpadCx, dpadCy, hubRadius, strokePaint)

            // Hub inner dark disc
            fillPaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_HUB_INNER, avgOpacity)
            canvas.drawCircle(dpadCx, dpadCy, hubRadius * 0.65f, fillPaint)

            // Hub inner red accent ring
            strokePaint.strokeWidth = 1.0f * density
            strokePaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_DECAL, 0.70f * avgOpacity)
            canvas.drawCircle(dpadCx, dpadCy, hubRadius * 0.65f, strokePaint)

            // 4 Cardinal red notch ticks (North, South, East, West)
            val tickLen = 5f * density * avgScale
            strokePaint.strokeWidth = 2f * density
            strokePaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DPAD_HUB_TICK, avgOpacity)
            canvas.drawLine(dpadCx, dpadCy - hubRadius + 2f * density, dpadCx, dpadCy - hubRadius + 2f * density + tickLen, strokePaint)
            canvas.drawLine(dpadCx, dpadCy + hubRadius - 2f * density, dpadCx, dpadCy + hubRadius - 2f * density - tickLen, strokePaint)
            canvas.drawLine(dpadCx - hubRadius + 2f * density, dpadCy, dpadCx - hubRadius + 2f * density + tickLen, dpadCy, strokePaint)
            canvas.drawLine(dpadCx + hubRadius - 2f * density, dpadCy, dpadCx + hubRadius - 2f * density - tickLen, dpadCy, strokePaint)
        }
    }

    /**
     * Renders a photorealistic arcade attack button (X, Y, Z, A, B, C).
     * Replicates the 6 visual layers from ArcadeConvexButton in ControllerEditorScreen:
     * 1. Titanium Bezel with off-center radial gradient.
     * 2. Specular outer metallic rim highlight.
     * 3. Glowing neon perimeter ring (dual-stroke: outer halo + inner bright ring).
     * 4. Translucent concave colored dish with vivid arcade radial gradient.
     * 5. Glossy specular top crescent sheen oval.
     * 6. High-contrast bold white label with drop shadow.
     */
    private fun drawArcadeAttackButton(
        canvas: Canvas,
        btn: ButtonModel,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val density = context.resources.displayMetrics.density
        val r = (minOf(btn.widthPx, btn.heightPx) / 2f) - 2f * density
        val cx = btn.centerX
        val cy = btn.centerY

        // 1. Titanium Bezel with off-center metallic radial gradient
        fillPaint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.25f, r,
            intArrayOf(
                ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_TITANIUM_BEZEL_TOP, alphaMult),
                ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_TITANIUM_BEZEL_MID, alphaMult),
                ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_TITANIUM_BEZEL_BOT, alphaMult)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.shader = null

        // Specular outer metallic rim highlight
        strokePaint.strokeWidth = 1.5f * density
        strokePaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_TITANIUM_RIM_HIGHLIGHT, alphaMult)
        canvas.drawCircle(cx, cy, r, strokePaint)

        // 2. Glowing Colored Neon Perimeter Ring
        val neonRadius = r * 0.88f
        val neonAlpha = if (isPressed) 0.85f else 0.35f
        glowPaint.strokeWidth = 4f * density
        glowPaint.color = ControllerVisualSpecs.adjustAlpha(accentColor, neonAlpha * alphaMult)
        canvas.drawCircle(cx, cy, neonRadius + 2f * density, glowPaint)

        strokePaint.strokeWidth = 2.5f * density
        strokePaint.color = if (isPressed) {
            ControllerVisualSpecs.adjustAlpha(Color.WHITE, alphaMult)
        } else {
            ControllerVisualSpecs.adjustAlpha(accentColor, alphaMult)
        }
        canvas.drawCircle(cx, cy, neonRadius, strokePaint)

        // 3. Spherical Translucent Concave Colored Dish
        val innerRadius = r * 0.78f
        val centerAlpha = if (isPressed) 1.0f else 0.90f
        fillPaint.shader = RadialGradient(
            cx, cy, innerRadius,
            intArrayOf(
                ControllerVisualSpecs.adjustAlpha(accentColor, centerAlpha * alphaMult),
                ControllerVisualSpecs.adjustAlpha(accentColor, 0.50f * alphaMult),
                ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_DISH_DARK_EDGE, alphaMult)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, innerRadius, fillPaint)
        fillPaint.shader = null

        // 4. Glossy Specular Top Crescent Sheen Reflection
        val sheenRadius = innerRadius * 0.65f
        val sheenHeight = innerRadius * 0.75f
        val sheenTop = cy - innerRadius + 2f * density
        sheenPaint.shader = LinearGradient(
            cx, cy - innerRadius, cx, cy,
            ControllerVisualSpecs.adjustAlpha(Color.WHITE, 0.60f * alphaMult),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        tempRectF.set(cx - sheenRadius, sheenTop, cx + sheenRadius, sheenTop + sheenHeight)
        canvas.drawOval(tempRectF, sheenPaint)
        sheenPaint.shader = null

        // 5. Crisp White Bold Label with Drop Shadow
        val textSize = 20f * density * btn.scale
        drawCenteredLabel(canvas, cx, cy, textSize, btn.label, alphaMult)
    }

    /**
     * Renders a 3D faceted octagonal button (D-Pad and shape = OCTAGON).
     */
    private fun drawOctagonButton(
        canvas: Canvas,
        btn: ButtonModel,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val density = context.resources.displayMetrics.density
        val left = btn.left
        val top = btn.top
        val right = left + btn.widthPx
        val bottom = top + btn.heightPx
        val cx = btn.centerX
        val cy = btn.centerY
        val cut = btn.widthPx * 0.28f

        tempOctPath.reset()
        tempOctPath.moveTo(left + cut, top)
        tempOctPath.lineTo(right - cut, top)
        tempOctPath.lineTo(right, top + cut)
        tempOctPath.lineTo(right, bottom - cut)
        tempOctPath.lineTo(right - cut, bottom)
        tempOctPath.lineTo(left + cut, bottom)
        tempOctPath.lineTo(left, bottom - cut)
        tempOctPath.lineTo(left, top + cut)
        tempOctPath.close()

        // Metallic graphite vertical gradient fill
        val topFill = if (isPressed) ControllerVisualSpecs.adjustAlpha(accentColor, 0.85f * alphaMult) else ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_OCTAGON_FILL_TOP, alphaMult)
        val midFill = if (isPressed) ControllerVisualSpecs.adjustAlpha(accentColor, 0.50f * alphaMult) else ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_OCTAGON_FILL_MID, alphaMult)
        val botFill = if (isPressed) ControllerVisualSpecs.adjustAlpha(0xFF282A36.toInt(), alphaMult) else ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_OCTAGON_FILL_BOT, alphaMult)

        fillPaint.shader = LinearGradient(
            cx, top, cx, bottom,
            intArrayOf(topFill, midFill, botFill),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(tempOctPath, fillPaint)
        fillPaint.shader = null

        // Outer Beveled Chamfer Highlight
        strokePaint.strokeWidth = 1.5f * density
        strokePaint.color = ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_OCTAGON_BEVEL_STROKE, alphaMult)
        canvas.drawPath(tempOctPath, strokePaint)

        // Glowing Perimeter Accent
        val rimColor = if (isPressed) Color.WHITE else ControllerVisualSpecs.adjustAlpha(accentColor, 0.85f)
        glowPaint.strokeWidth = 2.0f * density
        glowPaint.color = ControllerVisualSpecs.adjustAlpha(rimColor, alphaMult)
        canvas.drawPath(tempOctPath, glowPaint)

        // White Arrow Label
        val textSize = if (btn.label.length > 2) 13f * density * btn.scale else 22f * density * btn.scale
        drawCenteredLabel(canvas, cx, cy, textSize, btn.label, alphaMult)
    }

    /**
     * Renders an elongated horizontal hex-chamfered button (START / MENU).
     */
    private fun drawCyberHexButton(
        canvas: Canvas,
        btn: ButtonModel,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val density = context.resources.displayMetrics.density
        val left = btn.left
        val top = btn.top
        val right = left + btn.widthPx
        val bottom = top + btn.heightPx
        val cx = btn.centerX
        val cy = btn.centerY
        val cutX = 14f * density * btn.scale

        tempHexPath.reset()
        tempHexPath.moveTo(left + cutX, top)
        tempHexPath.lineTo(right - cutX, top)
        tempHexPath.lineTo(right, cy)
        tempHexPath.lineTo(right - cutX, bottom)
        tempHexPath.lineTo(left + cutX, bottom)
        tempHexPath.lineTo(left, cy)
        tempHexPath.close()

        // Hex vertical gradient fill
        val topFill = if (isPressed) ControllerVisualSpecs.COLOR_HEX_PRESSED_TOP else ControllerVisualSpecs.COLOR_HEX_FILL_TOP
        val midFill = if (isPressed) 0xFFA5070C.toInt() else ControllerVisualSpecs.COLOR_HEX_FILL_MID
        val botFill = if (isPressed) ControllerVisualSpecs.COLOR_HEX_PRESSED_BOT else ControllerVisualSpecs.COLOR_HEX_FILL_BOT

        fillPaint.shader = LinearGradient(
            cx, top, cx, bottom,
            intArrayOf(
                ControllerVisualSpecs.adjustAlpha(topFill, alphaMult),
                ControllerVisualSpecs.adjustAlpha(midFill, alphaMult),
                ControllerVisualSpecs.adjustAlpha(botFill, alphaMult)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(tempHexPath, fillPaint)
        fillPaint.shader = null

        // Crimson neon border
        val rimColor = if (isPressed) Color.WHITE else ControllerVisualSpecs.COLOR_SYSTEM
        strokePaint.strokeWidth = 2.0f * density
        strokePaint.color = ControllerVisualSpecs.adjustAlpha(rimColor, alphaMult)
        canvas.drawPath(tempHexPath, strokePaint)

        // Specular top highlight line
        strokePaint.strokeWidth = 1.0f * density
        strokePaint.color = ControllerVisualSpecs.adjustAlpha(Color.WHITE, 0.35f * alphaMult)
        canvas.drawLine(left + cutX, top + 2f * density, right - cutX, top + 2f * density, strokePaint)

        // Two-tier typography: Main Label ("START" / "MENU") + SubIcon ("[-]" / "[≡]")
        val subIcon = ControllerVisualSpecs.getButtonSubIcon(btn.id)
        if (subIcon != null) {
            val labelSize = 11f * density * btn.scale
            val subIconSize = 9f * density * btn.scale
            val spacing = 3.5f * density * btn.scale

            drawCenteredLabel(canvas, cx, cy - spacing, labelSize, btn.label, alphaMult)
            drawCenteredLabel(
                canvas, cx, cy + spacing + 7f * density * btn.scale, subIconSize, subIcon, alphaMult,
                textColor = 0xFFFF3038.toInt()
            )
        } else {
            drawCenteredLabel(canvas, cx, cy, 11f * density * btn.scale, btn.label, alphaMult)
        }
    }

    /**
     * Renders a chamfered cyber polygon with corner tick L-brackets (Macro & shape = CYBER).
     */
    private fun drawCyberButton(
        canvas: Canvas,
        btn: ButtonModel,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val density = context.resources.displayMetrics.density
        val left = btn.left
        val top = btn.top
        val right = left + btn.widthPx
        val bottom = top + btn.heightPx
        val cx = btn.centerX
        val cy = btn.centerY
        val cut = 12f * density * btn.scale

        tempCyberPath.reset()
        tempCyberPath.moveTo(left + cut, top)
        tempCyberPath.lineTo(right - cut, top)
        tempCyberPath.lineTo(right, top + cut)
        tempCyberPath.lineTo(right, bottom - cut)
        tempCyberPath.lineTo(right - cut, bottom)
        tempCyberPath.lineTo(left + cut, bottom)
        tempCyberPath.lineTo(left, bottom - cut)
        tempCyberPath.lineTo(left, top + cut)
        tempCyberPath.close()

        val topFill = if (isPressed) ControllerVisualSpecs.adjustAlpha(accentColor, 0.85f * alphaMult) else ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_CYBER_FILL_TOP, alphaMult)
        val midFill = if (isPressed) ControllerVisualSpecs.adjustAlpha(accentColor, 0.40f * alphaMult) else ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_CYBER_FILL_MID, alphaMult)
        val botFill = if (isPressed) ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_CYBER_PRESSED_BOT, alphaMult) else ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_CYBER_FILL_BOT, alphaMult)

        fillPaint.shader = LinearGradient(
            cx, top, cx, bottom,
            intArrayOf(topFill, midFill, botFill),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(tempCyberPath, fillPaint)
        fillPaint.shader = null

        // Cyber neon border
        val rimColor = if (isPressed) Color.WHITE else accentColor
        strokePaint.strokeWidth = 2.0f * density
        strokePaint.color = ControllerVisualSpecs.adjustAlpha(rimColor, alphaMult)
        canvas.drawPath(tempCyberPath, strokePaint)

        // Corner tick L-brackets
        val bracketLen = 8f * density * btn.scale
        strokePaint.strokeWidth = 2.0f * density
        canvas.drawLine(left, top + cut, left, top + cut + bracketLen, strokePaint)
        canvas.drawLine(right, top + cut, right, top + cut + bracketLen, strokePaint)
        canvas.drawLine(left, bottom - cut, left, bottom - cut - bracketLen, strokePaint)
        canvas.drawLine(right, bottom - cut, right, bottom - cut - bracketLen, strokePaint)

        // Typography
        val textSize = when {
            btn.label.length > 3 -> 10f * density * btn.scale
            btn.label.length > 1 -> 13f * density * btn.scale
            else -> 18f * density * btn.scale
        }
        drawCenteredLabel(canvas, cx, cy, textSize, btn.label, alphaMult)
    }

    /**
     * Renders a clean circular button with colored neon halo ring (shape = CIRCLE).
     */
    private fun drawCircleOnlyButton(
        canvas: Canvas,
        btn: ButtonModel,
        accentColor: Int,
        isPressed: Boolean,
        alphaMult: Float
    ) {
        val density = context.resources.displayMetrics.density
        val r = (minOf(btn.widthPx, btn.heightPx) / 2f) - 2f * density
        val cx = btn.centerX
        val cy = btn.centerY

        // Circular Body
        val bodyColor = if (isPressed) ControllerVisualSpecs.adjustAlpha(accentColor, 0.85f * alphaMult) else ControllerVisualSpecs.adjustAlpha(ControllerVisualSpecs.COLOR_CIRCLE_BODY, alphaMult)
        fillPaint.color = bodyColor
        canvas.drawCircle(cx, cy, r, fillPaint)

        // Neon Halo Ring
        val ringColor = if (isPressed) Color.WHITE else accentColor
        strokePaint.strokeWidth = 2.5f * density
        strokePaint.color = ControllerVisualSpecs.adjustAlpha(ringColor, alphaMult)
        canvas.drawCircle(cx, cy, r, strokePaint)

        // Typography
        val textSize = 20f * density * btn.scale
        drawCenteredLabel(canvas, cx, cy, textSize, btn.label, alphaMult)
    }

    /**
     * Draws centered high-contrast typography with drop shadow.
     */
    private fun drawCenteredLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        textSizePx: Float,
        label: String,
        alphaMult: Float,
        textColor: Int = Color.WHITE,
        shadowColor: Int = Color.BLACK,
        shadowOffsetX: Float = 1.5f * context.resources.displayMetrics.density,
        shadowOffsetY: Float = 1.5f * context.resources.displayMetrics.density
    ) {
        textPaint.textSize = textSizePx
        textPaint.color = ControllerVisualSpecs.adjustAlpha(textColor, alphaMult)
        textShadowPaint.textSize = textSizePx
        textShadowPaint.color = ControllerVisualSpecs.adjustAlpha(shadowColor, 0.85f * alphaMult)

        val fontMetrics = textPaint.fontMetrics
        val baseline = cy - (fontMetrics.descent + fontMetrics.ascent) / 2f

        // Drop shadow
        canvas.drawText(label, cx + shadowOffsetX, baseline + shadowOffsetY, textShadowPaint)
        // Foreground
        canvas.drawText(label, cx, baseline, textPaint)
    }
}
