package com.example.ui.screens.controllerEditor

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.settings.ControllerDataStore
import com.example.ui.components.CyberCutSmall
import com.example.ui.theme.*
import com.example.utils.HideSystemBars
import com.example.utils.LiveLogger
import com.example.utils.LockScreenOrientation
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Controller button data model maintaining full backwards compatibility
 * with NativeVirtualControllerView, unit tests, and JSON storage schema.
 */
data class ControllerButtonState(
    val id: String,
    val initialX: Float,
    val initialY: Float,
    val label: String,
    val scale: Float = 1.0f,
    val opacity: Float = 0.7f,
    val isEnabled: Boolean = true,
    val isMacro: Boolean = false,
    val shape: String = "DEFAULT"
)

enum class ControllerPreset(val displayName: String) {
    SIX_BUTTON("6-Button (Standard)"),
    CLASSIC("Classic (4-Button)"),
    ARCADE("Arcade View"),
    CUSTOM("Custom Layout")
}

enum class EditorTool(val title: String, val icon: ImageVector) {
    MOVE("تحريك", Icons.Default.OpenWith),
    SIZE("حجم", Icons.Default.AspectRatio),
    SHAPE("شكل", Icons.Default.Palette),
    OPACITY("شفافية", Icons.Default.Opacity),
    DELETE("حذف", Icons.Default.Delete)
}

data class MacroDefinition(
    val id: String,
    val label: String,
    val name: String,
    val description: String
)

val MACRO_CATALOG = listOf(
    MacroDefinition("macro_charge", "CHG", "شحن الطاقة (Power Charge)", "ضغط مستمر متزامن على B + Y لشحن العداد"),
    MacroDefinition("macro_ab", "A+B", "كومبو ركلات خفيف+متوسط (A + B)", "ضربة مشتركة لركلتي A و B"),
    MacroDefinition("macro_bc", "B+C", "كومبو ركلات متوسط+ثقيل (B + C)", "ضربة مشتركة لركلتي B و C"),
    MacroDefinition("macro_xy", "X+Y", "كومبو لكمات خفيف+متوسط (X + Y)", "ضربة مشتركة للكمتي X و Y"),
    MacroDefinition("macro_abc", "ABC", "الضربة الثلاثية (A + B + C)", "ضغط ثلاثي متزامن لأزرار A و B و C"),
    MacroDefinition("macro_round_reset", "RESET", "إعادة الجولة (Round Reset)", "إعادة الجولة فورياً (مفتاح F4)"),
    MacroDefinition("macro_max_power", "MAX", "طاقة قصوى (Max Power)", "تعبئة عداد الطاقة إلى الحد الأقصى (F3)"),
    MacroDefinition("macro_control_swap", "SWAP", "تبديل التحكم (Player ↔ CPU)", "نقل التحكم اليدوي بين المقاتل النشط وخصم الـ CPU أثناء المباراة"),
    MacroDefinition("macro_team_switch", "TAG", "تبديل عضو الفريق (Switch Team Member)", "تبديل المقاتل النشط بعضو احتياطي من نفس الفريق (مخصص لطور Tag)")
)

// Cybernetic chamfer shapes
private val CyberTopBarShape = CutCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
private val CyberSidePanelShape = CutCornerShape(8.dp)
private val CyberPillCut = CutCornerShape(8.dp)
private val CyberButtonCut = CutCornerShape(6.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerEditorScreen(onNavigateBack: () -> Unit) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    HideSystemBars()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedPreset by remember { mutableStateOf(ControllerPreset.SIX_BUTTON) }
    var buttons by remember { mutableStateOf<List<ControllerButtonState>>(emptyList()) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var showMacroDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var selectedButtonId by remember { mutableStateOf<String?>(null) }
    var activeTool by remember { mutableStateOf(EditorTool.MOVE) }

    // Right toolbar toggles
    var isGridVisible by remember { mutableStateOf(true) }
    var isSnapToGrid by remember { mutableStateOf(true) }
    var isPreviewMode by remember { mutableStateOf(false) }

    // Load saved layout on initial entry
    LaunchedEffect(Unit) {
        val savedLayout = ControllerDataStore.getLayout(context).firstOrNull()
        if (!savedLayout.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(savedLayout)
                val loadedButtons = mutableListOf<ControllerButtonState>()
                val loadedIds = mutableSetOf<String>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val isMacro = obj.optBoolean("macro", id.startsWith("macro_"))
                    val isEnabled = if (obj.has("enabled")) obj.getBoolean("enabled") else !isMacro
                    val scale = if (obj.has("scale")) obj.getDouble("scale").toFloat() else 1.0f
                    val opacity = if (obj.has("opacity")) obj.getDouble("opacity").toFloat() else 0.7f
                    val shape = obj.optString("shape", "DEFAULT")

                    loadedButtons.add(
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
                    loadedIds.add(id)
                }

                if (loadedButtons.isNotEmpty()) {
                    // Ensure all macro definitions exist in the pool as disabled if absent
                    for (macro in MACRO_CATALOG) {
                        if (!loadedIds.contains(macro.id)) {
                            loadedButtons.add(
                                ControllerButtonState(
                                    id = macro.id,
                                    initialX = 300f,
                                    initialY = 150f,
                                    label = macro.label,
                                    scale = 0.9f,
                                    opacity = 0.8f,
                                    isEnabled = false,
                                    isMacro = true,
                                    shape = "DEFAULT"
                                )
                            )
                        }
                    }
                    buttons = loadedButtons
                    selectedPreset = ControllerPreset.CUSTOM
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0D))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Canvas frame boundaries (between left & right toolbars, below topbar)
        val density = LocalDensity.current
        val frameInsetX = with(density) { 64.dp.toPx() }
        val frameInsetY = with(density) { 58.dp.toPx() }
        val frameRight = widthPx - with(density) { 68.dp.toPx() }
        val frameBottom = heightPx - with(density) { 14.dp.toPx() }
        val gridSpacingPx = with(density) { 32.dp.toPx() }

        // Initialize preset layout when measurements are available and not custom
        LaunchedEffect(selectedPreset, widthPx, heightPx) {
            if (widthPx > 0 && heightPx > 0 && selectedPreset != ControllerPreset.CUSTOM) {
                val presetButtons = getPresetLayout(selectedPreset, widthPx, heightPx).toMutableList()
                for (macro in MACRO_CATALOG) {
                    if (presetButtons.none { it.id == macro.id }) {
                        val posX = when (macro.id) {
                            "macro_charge" -> widthPx * 0.75f
                            "macro_ab" -> widthPx * 0.65f
                            "macro_bc" -> widthPx * 0.55f
                            "macro_xy" -> widthPx * 0.45f
                            "macro_abc" -> widthPx * 0.35f
                            "macro_round_reset" -> widthPx * 0.40f
                            "macro_max_power" -> widthPx * 0.50f
                            "macro_control_swap" -> widthPx * 0.30f
                            "macro_team_switch" -> widthPx * 0.20f
                            else -> widthPx * 0.50f
                        }
                        presetButtons.add(
                            ControllerButtonState(
                                id = macro.id,
                                initialX = posX,
                                initialY = heightPx * 0.35f,
                                label = macro.label,
                                scale = 0.85f,
                                opacity = 0.8f,
                                isEnabled = false,
                                isMacro = true,
                                shape = "DEFAULT"
                            )
                        )
                    }
                }
                buttons = presetButtons
            }
        }

        // --- LAYER 0: Canvas Background & Blueprint Grid ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Radial dark gradient radiating subtly from center
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF13151D), Color(0xFF08090C)),
                    center = Offset(widthPx / 2f, heightPx / 2f),
                    radius = widthPx * 0.65f
                )
            )

            // Technical Grid Lines strictly constrained inside the editor frame
            if (isGridVisible) {
                clipRect(left = frameInsetX, top = frameInsetY, right = frameRight, bottom = frameBottom) {
                    // Vertical grid lines
                    var x = frameInsetX
                    var colIndex = 0
                    while (x <= frameRight) {
                        val isMajor = (colIndex % 4 == 0)
                        val lineColor = if (isMajor) Color(0xFF384D68).copy(alpha = 0.85f) else Color(0xFF223044).copy(alpha = 0.65f)
                        val strokeWidth = if (isMajor) 1.5f else 1f
                        drawLine(
                            color = lineColor,
                            start = Offset(x, frameInsetY),
                            end = Offset(x, frameBottom),
                            strokeWidth = strokeWidth
                        )
                        x += gridSpacingPx
                        colIndex++
                    }

                    // Horizontal grid lines
                    var y = frameInsetY
                    var rowIndex = 0
                    while (y <= frameBottom) {
                        val isMajor = (rowIndex % 4 == 0)
                        val lineColor = if (isMajor) Color(0xFF384D68).copy(alpha = 0.85f) else Color(0xFF223044).copy(alpha = 0.65f)
                        val strokeWidth = if (isMajor) 1.5f else 1f
                        drawLine(
                            color = lineColor,
                            start = Offset(frameInsetX, y),
                            end = Offset(frameRight, y),
                            strokeWidth = strokeWidth
                        )
                        y += gridSpacingPx
                        rowIndex++
                    }

                    // Precision intersection dots on major blueprint coordinates
                    var ix = frameInsetX
                    var c = 0
                    while (ix <= frameRight) {
                        if (c % 4 == 0) {
                            var iy = frameInsetY
                            var r = 0
                            while (iy <= frameBottom) {
                                if (r % 4 == 0) {
                                    drawCircle(
                                        color = Color(0xFF5B82B0).copy(alpha = 0.90f),
                                        radius = 2.dp.toPx(),
                                        center = Offset(ix, iy)
                                    )
                                }
                                iy += gridSpacingPx
                                r++
                            }
                        }
                        ix += gridSpacingPx
                        c++
                    }
                }
            }

            // Outer Canvas Chamfered Red Frame
            val cut = 14.dp.toPx()
            val framePath = Path().apply {
                moveTo(frameInsetX + cut, frameInsetY)
                lineTo(frameRight - cut, frameInsetY)
                lineTo(frameRight, frameInsetY + cut)
                lineTo(frameRight, frameBottom - cut)
                lineTo(frameRight - cut, frameBottom)
                lineTo(frameInsetX + cut, frameBottom)
                lineTo(frameInsetX, frameBottom - cut)
                lineTo(frameInsetX, frameInsetY + cut)
                close()
            }

            // Frame border with glowing crimson accents
            drawPath(
                path = framePath,
                color = Color(0xFF6B1116).copy(alpha = 0.7f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Corner decorative brackets
            val bracketLen = 16.dp.toPx()
            val bStroke = 2.5.dp.toPx()
            val glowColor = Color(0xFFFF2430)

            // Top-left bracket
            drawLine(glowColor, Offset(frameInsetX, frameInsetY + cut), Offset(frameInsetX, frameInsetY + cut + bracketLen), strokeWidth = bStroke)
            drawLine(glowColor, Offset(frameInsetX + cut, frameInsetY), Offset(frameInsetX + cut + bracketLen, frameInsetY), strokeWidth = bStroke)

            // Top-right bracket
            drawLine(glowColor, Offset(frameRight, frameInsetY + cut), Offset(frameRight, frameInsetY + cut + bracketLen), strokeWidth = bStroke)
            drawLine(glowColor, Offset(frameRight - cut, frameInsetY), Offset(frameRight - cut - bracketLen, frameInsetY), strokeWidth = bStroke)

            // Bottom-left bracket
            drawLine(glowColor, Offset(frameInsetX, frameBottom - cut), Offset(frameInsetX, frameBottom - cut - bracketLen), strokeWidth = bStroke)
            drawLine(glowColor, Offset(frameInsetX + cut, frameBottom), Offset(frameInsetX + cut + bracketLen, frameBottom), strokeWidth = bStroke)

            // Bottom-right bracket
            drawLine(glowColor, Offset(frameRight, frameBottom - cut), Offset(frameRight, frameBottom - cut - bracketLen), strokeWidth = bStroke)
            drawLine(glowColor, Offset(frameRight - cut, frameBottom), Offset(frameRight - cut - bracketLen, frameBottom), strokeWidth = bStroke)
        }

        // --- LAYER 1: Central Watermark "MUGEN MOBILE" ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(0.35f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "M",
                        color = Color(0xFFE51D24),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "UGEN",
                        color = Color(0xFFCCCCCC),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Text(
                    text = "M O B I L E",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.SansSerif
                )
                // Red horizontal slash
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(70.dp)
                        .height(2.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFFE51D24), Color.Transparent)
                            )
                        )
                )
            }
        }

        // --- LAYER 2: D-Pad Decorative Background & Center Hub ---
        val dpadUp = buttons.find { it.id == "dpad_up" }
        val dpadDown = buttons.find { it.id == "dpad_down" }
        val dpadLeft = buttons.find { it.id == "dpad_left" }
        val dpadRight = buttons.find { it.id == "dpad_right" }

        if (dpadUp != null && dpadDown != null && dpadLeft != null && dpadRight != null) {
            val dpadLeftCenter = dpadLeft.initialX + with(density) { (64.dp * dpadLeft.scale).toPx() } / 2f
            val dpadRightCenter = dpadRight.initialX + with(density) { (64.dp * dpadRight.scale).toPx() } / 2f
            val dpadUpCenter = dpadUp.initialY + with(density) { (64.dp * dpadUp.scale).toPx() } / 2f
            val dpadDownCenter = dpadDown.initialY + with(density) { (64.dp * dpadDown.scale).toPx() } / 2f

            val dpadCx = (dpadLeftCenter + dpadRightCenter) / 2f
            val dpadCy = (dpadUpCenter + dpadDownCenter) / 2f
            val avgScale = (dpadLeft.scale + dpadRight.scale + dpadUp.scale + dpadDown.scale) / 4f

            Canvas(modifier = Modifier.fillMaxSize()) {
                val hubRadius = (24.dp * avgScale).toPx()
                val outerDecalRadius = (66.dp * avgScale).toPx()

                // Decorative concentric red arc segments
                drawCircle(
                    color = Color(0xFFE51D24).copy(alpha = 0.18f),
                    radius = outerDecalRadius,
                    center = Offset(dpadCx, dpadCy),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(25f, 15f)))
                )
                drawCircle(
                    color = Color(0xFFE51D24).copy(alpha = 0.40f),
                    radius = outerDecalRadius - 8.dp.toPx(),
                    center = Offset(dpadCx, dpadCy),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                )

                // D-Pad Central Hub (Faceted circular metallic disc with red ticks)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2B2D38), Color(0xFF14151B), Color(0xFF0D0E12)),
                        center = Offset(dpadCx - 4f, dpadCy - 4f),
                        radius = hubRadius
                    ),
                    radius = hubRadius,
                    center = Offset(dpadCx, dpadCy)
                )
                drawCircle(
                    color = Color(0xFF3B3E4C),
                    radius = hubRadius,
                    center = Offset(dpadCx, dpadCy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF121318),
                    radius = hubRadius * 0.65f,
                    center = Offset(dpadCx, dpadCy)
                )
                drawCircle(
                    color = Color(0xFFE51D24).copy(alpha = 0.7f),
                    radius = hubRadius * 0.65f,
                    center = Offset(dpadCx, dpadCy),
                    style = Stroke(width = 1.dp.toPx())
                )

                // 4 Red tick notch indicators (North, South, East, West)
                val tickLen = 5.dp.toPx()
                val tickW = 2.dp.toPx()
                val tickColor = Color(0xFFFF2430)
                drawLine(tickColor, Offset(dpadCx, dpadCy - hubRadius + 2f), Offset(dpadCx, dpadCy - hubRadius + 2f + tickLen), strokeWidth = tickW)
                drawLine(tickColor, Offset(dpadCx, dpadCy + hubRadius - 2f), Offset(dpadCx, dpadCy + hubRadius - 2f - tickLen), strokeWidth = tickW)
                drawLine(tickColor, Offset(dpadCx - hubRadius + 2f, dpadCy), Offset(dpadCx - hubRadius + 2f + tickLen, dpadCy), strokeWidth = tickW)
                drawLine(tickColor, Offset(dpadCx + hubRadius - 2f, dpadCy), Offset(dpadCx + hubRadius - 2f - tickLen, dpadCy), strokeWidth = tickW)
            }
        }

        // --- LAYER 3: Touch Surface for Deselection (Tap Empty Space when not in preview) ---
        if (!isPreviewMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            selectedButtonId = null
                        }
                    }
            )
        }

        // --- LAYER 4: Interactive Controller Buttons ---
        val activeButtons = buttons.filter { it.isEnabled }
        activeButtons.forEach { buttonState ->
            val isSelected = buttonState.id == selectedButtonId

            ArcadeDraggableButton(
                buttonState = buttonState,
                isSelected = isSelected,
                isPreviewMode = isPreviewMode,
                isSnapToGrid = isSnapToGrid,
                gridSpacingPx = gridSpacingPx,
                frameInsetX = frameInsetX,
                frameInsetY = frameInsetY,
                frameRight = frameRight,
                frameBottom = frameBottom,
                onClick = {
                    if (!isPreviewMode) {
                        selectedButtonId = buttonState.id
                    }
                },
                onPositionChanged = { newX, newY ->
                    if (!isPreviewMode) {
                        buttons = buttons.map {
                            if (it.id == buttonState.id) it.copy(initialX = newX, initialY = newY) else it
                        }
                        selectedPreset = ControllerPreset.CUSTOM
                        selectedButtonId = buttonState.id
                    }
                }
            )
        }

        val selectedButton = buttons.find { it.id == selectedButtonId }

        // --- LAYER 5: Floating Adjustment HUD (For Size, Shape, Opacity, Delete) ---
        if (!isPreviewMode && selectedButton != null && activeTool != EditorTool.MOVE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 62.dp)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                CyberFloatingAdjustmentBar(
                    selectedButton = selectedButton,
                    activeTool = activeTool,
                    onScaleChange = { newScale ->
                        buttons = buttons.map {
                            if (it.id == selectedButton.id) it.copy(scale = newScale) else it
                        }
                        selectedPreset = ControllerPreset.CUSTOM
                    },
                    onOpacityChange = { newOpacity ->
                        buttons = buttons.map {
                            if (it.id == selectedButton.id) it.copy(opacity = newOpacity) else it
                        }
                        selectedPreset = ControllerPreset.CUSTOM
                    },
                    onShapeChange = { newShape ->
                        buttons = buttons.map {
                            if (it.id == selectedButton.id) it.copy(shape = newShape) else it
                        }
                        selectedPreset = ControllerPreset.CUSTOM
                    },
                    onDelete = {
                        if (selectedButton.isMacro) {
                            buttons = buttons.map {
                                if (it.id == selectedButton.id) it.copy(isEnabled = false) else it
                            }
                            selectedButtonId = null
                            Toast.makeText(context, "تم إخفاء زر الماكرو (${selectedButton.label})", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "لا يمكن حذف أزرار القتال الأساسية للحفاظ على التحكم بالمباراة", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClose = {
                        activeTool = EditorTool.MOVE
                    }
                )
            }
        }

        // --- LAYER 6: Left Vertical Toolbar (5 Tools: Move, Size, Shape, Opacity, Delete) ---
        AnimatedVisibility(
            visible = !isPreviewMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
        ) {
            CyberLeftToolbar(
                activeTool = activeTool,
                onToolSelect = { tool ->
                    activeTool = tool
                    if (tool == EditorTool.DELETE && selectedButton != null) {
                        if (selectedButton.isMacro) {
                            buttons = buttons.map {
                                if (it.id == selectedButton.id) it.copy(isEnabled = false) else it
                            }
                            selectedButtonId = null
                            Toast.makeText(context, "تم إخفاء زر الماكرو (${selectedButton.label})", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "لا يمكن حذف أزرار القتال الأساسية", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // --- LAYER 7: Right Vertical Toolbar (3 Controls: Grid, Snap, Preview) ---
        AnimatedVisibility(
            visible = !isPreviewMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        ) {
            CyberRightToolbar(
                isGridVisible = isGridVisible,
                onGridToggle = { isGridVisible = it },
                isSnapToGrid = isSnapToGrid,
                onSnapToggle = { isSnapToGrid = it },
                isPreviewMode = isPreviewMode,
                onPreviewToggle = {
                    isPreviewMode = it
                    if (it) {
                        selectedButtonId = null
                    }
                }
            )
        }

        // --- LAYER 8: Top Cyber Toolbar ---
        AnimatedVisibility(
            visible = !isPreviewMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            CyberTopBar(
                selectedPreset = selectedPreset,
                showPresetMenu = showPresetMenu,
                activeMacroCount = buttons.count { it.isMacro && it.isEnabled },
                onNavigateBack = onNavigateBack,
                onResetClick = { showResetConfirmDialog = true },
                onMacroClick = { showMacroDialog = true },
                onPresetMenuToggle = { showPresetMenu = it },
                onPresetSelect = { preset ->
                    selectedPreset = preset
                    showPresetMenu = false
                },
                onSaveClick = {
                    coroutineScope.launch {
                        val jsonArray = JSONArray()
                        buttons.forEach { b ->
                            val obj = JSONObject()
                            obj.put("id", b.id)
                            obj.put("x", b.initialX)
                            obj.put("y", b.initialY)
                            obj.put("scale", b.scale.toDouble())
                            obj.put("opacity", b.opacity.toDouble())
                            obj.put("enabled", b.isEnabled)
                            obj.put("macro", b.isMacro)
                            obj.put("shape", b.shape)
                            jsonArray.put(obj)
                        }
                        ControllerDataStore.saveLayout(context, jsonArray.toString())
                        LiveLogger.i(
                            LiveLogger.Category.CONTROLLER,
                            "LAYOUT_SAVED",
                            "Saved ${buttons.size} buttons (Active=${buttons.count { it.isEnabled }})"
                        )
                        Toast.makeText(context, "تم حفظ تخطيط أزرار التحكم بنجاح!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // --- LAYER 9: Exit Preview Floating Button ---
        if (isPreviewMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF14151C).copy(alpha = 0.92f),
                    shape = CyberPillCut,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF2430)),
                    modifier = Modifier.clickable { isPreviewMode = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFFFF2430),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "معاينة شاشة اللعب (انقر هنا للعودة إلى وضع التعديل)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // --- RESET CONFIRMATION DIALOG ---
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = Color(0xFF14151C),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFFF2430))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استعادة التخطيط الافتراضي", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "هل أنت متأكد من رغبتك في إعادة ضبط مواضع وأحجام وأشكال جميع الأزرار إلى التوزيع القياسي (6-Button)؟",
                    color = Color(0xFFC0C2D0),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPreset = ControllerPreset.SIX_BUTTON
                        selectedButtonId = null
                        showResetConfirmDialog = false
                        Toast.makeText(context, "تمت استعادة الوضع الافتراضي", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    shape = CyberButtonCut
                ) {
                    Text("استعادة الآن", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("إلغاء", color = Color.LightGray)
                }
            }
        )
    }

    // --- CYBER MACRO BUTTON SELECTION DIALOG ---
    if (showMacroDialog) {
        val configuration = LocalConfiguration.current
        val maxDialogHeight = (configuration.screenHeightDp.dp * 0.70f).coerceIn(180.dp, 400.dp)
        val macroScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showMacroDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            containerColor = Color(0xFF101218),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "أزرار الماكرو والأكشن المركبة",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "فعّل الأزرار المركبة التي ترغب بإظهارها على شاشة اللعب (مخفية افتراضياً لحماية الرؤية):",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9FA8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxDialogHeight)
                            .verticalScroll(macroScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MACRO_CATALOG.forEach { macro ->
                            val currentBtn = buttons.find { it.id == macro.id }
                            val isEnabled = currentBtn?.isEnabled == true

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newEnabled = !isEnabled
                                        buttons = buttons.map {
                                            if (it.id == macro.id) it.copy(isEnabled = newEnabled) else it
                                        }
                                        selectedPreset = ControllerPreset.CUSTOM
                                    },
                                shape = CyberCutSmall,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isEnabled) Color(0xFF2A1516) else Color(0xFF181A22)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isEnabled) Color(0xFFFF2430) else Color(0xFF2A2D3A)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (isEnabled) Color(0xFFFF9100) else Color(0xFF383C4C),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    macro.label,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                macro.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            macro.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFA0A3B0),
                                            fontSize = 11.sp
                                        )
                                    }

                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked ->
                                            buttons = buttons.map {
                                                if (it.id == macro.id) it.copy(isEnabled = checked) else it
                                            }
                                            selectedPreset = ControllerPreset.CUSTOM
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFD50000),
                                            checkedBorderColor = Color(0xFFFF2430),
                                            uncheckedThumbColor = Color(0xFF8A8D9B),
                                            uncheckedTrackColor = Color(0xFF22242E),
                                            uncheckedBorderColor = Color(0xFF323544)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showMacroDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    shape = CyberButtonCut
                ) {
                    Text("تم وحفظ الاختيارات", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ============================================================================
// TOP TOOLBAR COMPOSABLE
// ============================================================================

@Composable
fun CyberTopBar(
    selectedPreset: ControllerPreset,
    showPresetMenu: Boolean,
    activeMacroCount: Int,
    onNavigateBack: () -> Unit,
    onResetClick: () -> Unit,
    onMacroClick: () -> Unit,
    onPresetMenuToggle: (Boolean) -> Unit,
    onPresetSelect: (ControllerPreset) -> Unit,
    onSaveClick: () -> Unit
) {
    Surface(
        color = Color(0xFF0F1015).copy(alpha = 0.95f),
        shape = CyberTopBarShape,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val h = size.height
                val w = size.width
                // Glowing crimson bottom contour line with cyber notches
                drawLine(
                    color = Color(0xFF551216),
                    start = Offset(0f, h - 1.dp.toPx()),
                    end = Offset(w, h - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                // Center accent notch
                val cx = w / 2f
                drawLine(
                    color = Color(0xFFFF2028),
                    start = Offset(cx - 30.dp.toPx(), h - 1.dp.toPx()),
                    end = Offset(cx + 30.dp.toPx(), h - 1.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { /* consume taps */ }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Back, Reset, Macros
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button
                Surface(
                    color = Color(0xFF1A1C24),
                    shape = CyberPillCut,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333745)),
                    modifier = Modifier.clickable(onClick = onNavigateBack)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("رجوع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Reset Button
                Surface(
                    color = Color(0xFF171318),
                    shape = CyberPillCut,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF75151B)),
                    modifier = Modifier.clickable(onClick = onResetClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "الافتراضي",
                            tint = Color(0xFFFF3038),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الافتراضي", color = Color(0xFFFF4048), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Macro Buttons Pill
                Surface(
                    color = Color(0xFFC00C14),
                    shape = CyberPillCut,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF3038)),
                    modifier = Modifier.clickable(onClick = onMacroClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "أزرار الماكرو ($activeMacroCount)",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Center Guide Instructions
            Text(
                text = "اضغط على أي زر لتعديل حجمه وشفافيته وموضعه",
                color = Color(0xFFD0D3DF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            // Right Group: Preset Dropdown & Save Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preset Dropdown
                Box {
                    Surface(
                        color = Color(0xFF14080B),
                        shape = CyberPillCut,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD61821)),
                        modifier = Modifier.clickable { onPresetMenuToggle(true) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedPreset.displayName.substringBefore(" "),
                                color = Color(0xFFFF4048),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFFFF4048),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showPresetMenu,
                        onDismissRequest = { onPresetMenuToggle(false) },
                        modifier = Modifier.background(Color(0xFF14151E))
                    ) {
                        ControllerPreset.values().forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        preset.displayName,
                                        color = if (preset == selectedPreset) Color(0xFFFF3038) else Color.White,
                                        fontWeight = if (preset == selectedPreset) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = { onPresetSelect(preset) }
                            )
                        }
                    }
                }

                // Save Button
                Surface(
                    color = Color(0xFFD50000),
                    shape = CyberPillCut,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF3038)),
                    modifier = Modifier.clickable(onClick = onSaveClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "حفظ",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// LEFT VERTICAL TOOLBAR (5 TOOLS)
// ============================================================================

@Composable
fun CyberLeftToolbar(
    activeTool: EditorTool,
    onToolSelect: (EditorTool) -> Unit
) {
    Surface(
        color = Color(0xFF0F1016).copy(alpha = 0.95f),
        shape = CyberSidePanelShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF551216)),
        modifier = Modifier.wrapContentSize()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorTool.values().forEach { tool ->
                val isActive = tool == activeTool

                val bgColor = if (isActive) Color(0xFFD50000) else Color(0xFF171922)
                val borderColor = if (isActive) Color(0xFFFF3038) else Color(0xFF2C2F3C)
                val contentColor = if (isActive) Color.White else Color(0xFFB0B3C2)

                Surface(
                    color = bgColor,
                    shape = CyberButtonCut,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .size(width = 44.dp, height = 48.dp)
                        .clickable { onToolSelect(tool) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tool.title,
                            color = contentColor,
                            fontSize = 10.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// RIGHT VERTICAL TOOLBAR (3 TOGGLE CONTROLS)
// ============================================================================

@Composable
fun CyberRightToolbar(
    isGridVisible: Boolean,
    onGridToggle: (Boolean) -> Unit,
    isSnapToGrid: Boolean,
    onSnapToggle: (Boolean) -> Unit,
    isPreviewMode: Boolean,
    onPreviewToggle: (Boolean) -> Unit
) {
    Surface(
        color = Color(0xFF0F1016).copy(alpha = 0.95f),
        shape = CyberSidePanelShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF551216)),
        modifier = Modifier.wrapContentSize()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Grid Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = null,
                    tint = if (isGridVisible) Color.White else Color(0xFF888B98),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "شبكة",
                    color = if (isGridVisible) Color.White else Color(0xFF888B98),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                Switch(
                    checked = isGridVisible,
                    onCheckedChange = onGridToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFD50000),
                        checkedBorderColor = Color(0xFFFF2430),
                        uncheckedThumbColor = Color(0xFFB0B3C1),
                        uncheckedTrackColor = Color(0xFF1E2028),
                        uncheckedBorderColor = Color(0xFF353844)
                    ),
                    modifier = Modifier.scale(0.70f)
                )
            }

            // 2. Snap Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Grain,
                    contentDescription = null,
                    tint = if (isSnapToGrid) Color.White else Color(0xFF888B98),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "محاذاة",
                    color = if (isSnapToGrid) Color.White else Color(0xFF888B98),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                Switch(
                    checked = isSnapToGrid,
                    onCheckedChange = onSnapToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFD50000),
                        checkedBorderColor = Color(0xFFFF2430),
                        uncheckedThumbColor = Color(0xFFB0B3C1),
                        uncheckedTrackColor = Color(0xFF1E2028),
                        uncheckedBorderColor = Color(0xFF353844)
                    ),
                    modifier = Modifier.scale(0.70f)
                )
            }

            // 3. Preview Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    tint = if (isPreviewMode) Color(0xFFFF3038) else Color(0xFF888B98),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "معاينة",
                    color = if (isPreviewMode) Color(0xFFFF3038) else Color(0xFF888B98),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                Switch(
                    checked = isPreviewMode,
                    onCheckedChange = onPreviewToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFD50000),
                        checkedBorderColor = Color(0xFFFF2430),
                        uncheckedThumbColor = Color(0xFFB0B3C1),
                        uncheckedTrackColor = Color(0xFF1E2028),
                        uncheckedBorderColor = Color(0xFF353844)
                    ),
                    modifier = Modifier.scale(0.70f)
                )
            }
        }
    }
}

// ============================================================================
// FLOATING ADJUSTMENT BAR (SIZE, SHAPE, OPACITY, DELETE)
// ============================================================================

@Composable
fun CyberFloatingAdjustmentBar(
    selectedButton: ControllerButtonState,
    activeTool: EditorTool,
    onScaleChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onShapeChange: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = Color(0xFF14151E).copy(alpha = 0.95f),
        shape = CyberPillCut,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF3038)),
        modifier = Modifier.wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selected Button Identifier
            Text(
                text = "${selectedButton.label}:",
                color = Color(0xFFFFEB3B),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )

            when (activeTool) {
                EditorTool.SIZE -> {
                    Text(
                        text = "الحجم: ${(selectedButton.scale * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = selectedButton.scale,
                        onValueChange = onScaleChange,
                        valueRange = 0.5f..2.2f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF2430),
                            activeTrackColor = Color(0xFFD50000),
                            inactiveTrackColor = Color(0xFF2C2F3C)
                        ),
                        modifier = Modifier.width(130.dp)
                    )
                }

                EditorTool.OPACITY -> {
                    Text(
                        text = "الشفافية: ${(selectedButton.opacity * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = selectedButton.opacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.2f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF2430),
                            activeTrackColor = Color(0xFFD50000),
                            inactiveTrackColor = Color(0xFF2C2F3C)
                        ),
                        modifier = Modifier.width(130.dp)
                    )
                }

                EditorTool.SHAPE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DEFAULT" to "افتراضي", "CIRCLE" to "دائري", "OCTAGON" to "ثماني", "CYBER" to "سايبر").forEach { (shapeKey, shapeLabel) ->
                            val isCurrent = selectedButton.shape.uppercase() == shapeKey
                            Surface(
                                color = if (isCurrent) Color(0xFFD50000) else Color(0xFF222430),
                                shape = RoundedCornerShape(4.dp),
                                border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3038)) else null,
                                modifier = Modifier.clickable { onShapeChange(shapeKey) }
                            ) {
                                Text(
                                    text = shapeLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                EditorTool.DELETE -> {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedButton.isMacro) "إخفاء الماكرو" else "حذف الزر", fontSize = 11.sp)
                    }
                }

                EditorTool.MOVE -> { /* Handled on canvas */ }
            }

            // Close Adjustment Bar
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ============================================================================
// DRAGGABLE CONTROLLER BUTTON COMPOSABLE
// ============================================================================

@Composable
fun ArcadeDraggableButton(
    buttonState: ControllerButtonState,
    isSelected: Boolean,
    isPreviewMode: Boolean,
    isSnapToGrid: Boolean,
    gridSpacingPx: Float,
    frameInsetX: Float,
    frameInsetY: Float,
    frameRight: Float,
    frameBottom: Float,
    onClick: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit
) {
    var rawX by remember(buttonState.initialX) { mutableStateOf(buttonState.initialX) }
    var rawY by remember(buttonState.initialY) { mutableStateOf(buttonState.initialY) }
    var offsetX by remember(buttonState.initialX) { mutableStateOf(buttonState.initialX) }
    var offsetY by remember(buttonState.initialY) { mutableStateOf(buttonState.initialY) }
    var isPressedInPreview by remember { mutableStateOf(false) }

    val isDpad = buttonState.id.startsWith("dpad_")
    val isSystem = buttonState.id == "btn_start" || buttonState.id == "btn_menu"
    val isMacro = buttonState.isMacro

    val (baseWidthDp, baseHeightDp) = ControllerVisualSpecs.getBaseDimensionsDp(buttonState.id, buttonState.isMacro)
    val baseWidth = baseWidthDp.dp
    val baseHeight = baseHeightDp.dp

    val finalWidth = baseWidth * buttonState.scale
    val finalHeight = baseHeight * buttonState.scale

    val density = LocalDensity.current
    val finalWidthPx = with(density) { finalWidth.toPx() }
    val finalHeightPx = with(density) { finalHeight.toPx() }

    val accentColor = getButtonAccentColor(buttonState.id)

    // Touch and Gesture modifier based on Preview Mode
    val interactionModifier = if (!isPreviewMode) {
        Modifier
            .pointerInput(buttonState.id, isSnapToGrid, gridSpacingPx, frameInsetX, frameInsetY, frameRight, frameBottom) {
                detectDragGestures(
                    onDragStart = {
                        rawX = offsetX
                        rawY = offsetY
                        onClick()
                    },
                    onDragEnd = {
                        onPositionChanged(offsetX, offsetY)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    rawX += dragAmount.x
                    rawY += dragAmount.y

                    val maxX = (frameRight - finalWidthPx).coerceAtLeast(frameInsetX)
                    val maxY = (frameBottom - finalHeightPx).coerceAtLeast(frameInsetY)

                    val clampedRawX = rawX.coerceIn(frameInsetX, maxX)
                    val clampedRawY = rawY.coerceIn(frameInsetY, maxY)

                    if (isSnapToGrid) {
                        val snappedX = frameInsetX + ((clampedRawX - frameInsetX) / gridSpacingPx).roundToInt() * gridSpacingPx
                        val snappedY = frameInsetY + ((clampedRawY - frameInsetY) / gridSpacingPx).roundToInt() * gridSpacingPx
                        offsetX = snappedX.coerceIn(frameInsetX, maxX)
                        offsetY = snappedY.coerceIn(frameInsetY, maxY)
                    } else {
                        offsetX = clampedRawX
                        offsetY = clampedRawY
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    } else {
        // In Preview Mode: Editing is STRICTLY disabled.
        // Touch provides authentic visual press feedback only without modifying layout or selecting.
        Modifier.pointerInput(buttonState.id) {
            detectTapGestures(
                onPress = {
                    isPressedInPreview = true
                    tryAwaitRelease()
                    isPressedInPreview = false
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(width = finalWidth, height = finalHeight)
            .then(interactionModifier)
            .alpha(buttonState.opacity.coerceIn(0.2f, 1.0f)),
        contentAlignment = Alignment.Center
    ) {
        val effectiveShape = ControllerVisualSpecs.resolveEffectiveShape(buttonState.shape, buttonState.id, buttonState.isMacro)

        when (effectiveShape) {
            "OCTAGON" -> {
                DpadOctagonButton(
                    direction = buttonState.label,
                    accentColor = accentColor,
                    isPressed = isPressedInPreview
                )
            }
            "CYBER" -> {
                CyberChamferButton(
                    label = buttonState.label,
                    accentColor = accentColor,
                    isPressed = isPressedInPreview
                )
            }
            "CYBER_HEX" -> {
                SystemHexButton(
                    label = buttonState.label,
                    subIcon = if (buttonState.id == "btn_start") "[-]" else "[≡]",
                    isPressed = isPressedInPreview
                )
            }
            "CIRCLE" -> {
                CircularButtonContent(
                    label = buttonState.label,
                    accentColor = accentColor,
                    isPressed = isPressedInPreview
                )
            }
            else -> {
                ArcadeConvexButton(
                    label = buttonState.label,
                    accentColor = accentColor,
                    isPressed = isPressedInPreview
                )
            }
        }

        // Selection Reticle with Corner L-Brackets (Hidden during Preview)
        if (isSelected && !isPreviewMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val bracketLen = 12.dp.toPx()
                val reticleColor = Color(0xFFFF2430)

                val w = size.width
                val h = size.height

                // Top-Left L-bracket
                drawLine(reticleColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokeWidth = strokeWidth)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, bracketLen), strokeWidth = strokeWidth)

                // Top-Right L-bracket
                drawLine(reticleColor, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeWidth = strokeWidth)
                drawLine(reticleColor, Offset(w, 0f), Offset(w, bracketLen), strokeWidth = strokeWidth)

                // Bottom-Left L-bracket
                drawLine(reticleColor, Offset(0f, h), Offset(bracketLen, h), strokeWidth = strokeWidth)
                drawLine(reticleColor, Offset(0f, h), Offset(0f, h - bracketLen), strokeWidth = strokeWidth)

                // Bottom-Right L-bracket
                drawLine(reticleColor, Offset(w, h), Offset(w - bracketLen, h), strokeWidth = strokeWidth)
                drawLine(reticleColor, Offset(w, h), Offset(w, h - bracketLen), strokeWidth = strokeWidth)

                // Subtle bounding box
                drawRect(
                    color = reticleColor.copy(alpha = 0.45f),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                )
            }
        }
    }
}

// ============================================================================
// PHOTOREALISTIC ARCADE ATTACK BUTTON (X, Y, Z, A, B, C)
// ============================================================================

@Composable
fun ArcadeConvexButton(
    label: String,
    accentColor: Color,
    isPressed: Boolean = false
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = (w.coerceAtMost(h) / 2f) - 2f
        val center = Offset(w / 2f, h / 2f)

        // 1. Outer Titanium / Gunmetal Bezel
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF323544), Color(0xFF1E202A), Color(0xFF111218)),
                center = Offset(center.x - r * 0.25f, center.y - r * 0.25f),
                radius = r
            ),
            radius = r,
            center = center
        )

        // Specular outer metallic rim highlight
        drawCircle(
            color = Color(0xFF5A5E75),
            radius = r,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 2. Glowing Colored Neon Perimeter Ring
        val neonRadius = r * 0.88f
        val neonAlpha = if (isPressed) 0.85f else 0.35f
        drawCircle(
            color = accentColor.copy(alpha = neonAlpha),
            radius = neonRadius + 2.dp.toPx(),
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )
        drawCircle(
            color = if (isPressed) Color.White else accentColor,
            radius = neonRadius,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // 3. Spherical Translucent Concave Colored Dish
        val innerRadius = r * 0.78f
        val centerAlpha = if (isPressed) 1.0f else 0.90f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = centerAlpha),
                    accentColor.copy(alpha = 0.50f),
                    Color(0xFF0D0E14)
                ),
                center = Offset(center.x, center.y),
                radius = innerRadius
            ),
            radius = innerRadius,
            center = center
        )

        // 4. Glossy Specular Top Crescent Sheen
        val sheenRadius = innerRadius * 0.65f
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.60f), Color.Transparent),
                startY = center.y - innerRadius,
                endY = center.y
            ),
            topLeft = Offset(center.x - sheenRadius, center.y - innerRadius + 2.dp.toPx()),
            size = Size(sheenRadius * 2f, innerRadius * 0.75f)
        )
    }

    // Centered Crisp White Bold Label with Drop Shadow
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = Color.Black.copy(alpha = 0.85f),
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ============================================================================
// CIRCULAR BUTTON CONTENT (SHAPE = CIRCLE)
// ============================================================================

@Composable
fun CircularButtonContent(
    label: String,
    accentColor: Color,
    isPressed: Boolean = false
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = (w.coerceAtMost(h) / 2f) - 2f
        val center = Offset(w / 2f, h / 2f)

        // Circular Body
        val bodyColor = if (isPressed) accentColor.copy(alpha = 0.85f) else Color(0xFF181A24)
        drawCircle(color = bodyColor, radius = r, center = center)

        // Colored Neon Halo Ring
        val ringColor = if (isPressed) Color.White else accentColor
        drawCircle(color = ringColor, radius = r, center = center, style = Stroke(width = 2.5.dp.toPx()))
    }

    Box(contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = Color.Black.copy(alpha = 0.85f),
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ============================================================================
// 3D FACETED OCTAGON BUTTON (D-PAD & SHAPE = OCTAGON)
// ============================================================================

@Composable
fun DpadOctagonButton(
    direction: String,
    accentColor: Color,
    isPressed: Boolean = false
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cut = w * 0.28f

        val octPath = Path().apply {
            moveTo(cut, 0f)
            lineTo(w - cut, 0f)
            lineTo(w, cut)
            lineTo(w, h - cut)
            lineTo(w - cut, h)
            lineTo(cut, h)
            lineTo(0f, h - cut)
            lineTo(0f, cut)
            close()
        }

        // Base Dark Graphite Fill or Pressed Glow
        val fillColors = if (isPressed) {
            listOf(accentColor.copy(alpha = 0.85f), Color(0xFF282A36))
        } else {
            listOf(Color(0xFF282A36), Color(0xFF16171E), Color(0xFF0F1015))
        }
        drawPath(
            path = octPath,
            brush = Brush.verticalGradient(colors = fillColors)
        )

        // Outer Beveled Chamfer Highlight
        drawPath(
            path = octPath,
            color = Color(0xFF4A4E62),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Glowing Perimeter Accent
        val rimColor = if (isPressed) Color.White else accentColor.copy(alpha = 0.85f)
        drawPath(
            path = octPath,
            color = rimColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }

    Text(
        text = direction,
        color = Color.White,
        fontWeight = FontWeight.Black,
        fontSize = if (direction.length > 2) 13.sp else 22.sp
    )
}

// ============================================================================
// CYBER CHAMFERED BUTTON (SHAPE = CYBER)
// ============================================================================

@Composable
fun CyberChamferButton(
    label: String,
    accentColor: Color,
    isPressed: Boolean = false
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cut = 12.dp.toPx()

        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(w - cut, 0f)
            lineTo(w, cut)
            lineTo(w, h - cut)
            lineTo(w - cut, h)
            lineTo(cut, h)
            lineTo(0f, h - cut)
            lineTo(0f, cut)
            close()
        }

        val fillColors = if (isPressed) {
            listOf(accentColor.copy(alpha = 0.85f), Color(0xFF1E140C))
        } else {
            listOf(Color(0xFF241618), Color(0xFF140E10), Color(0xFF0C090A))
        }

        drawPath(path = path, brush = Brush.verticalGradient(fillColors))

        // Cyber neon border
        val rimColor = if (isPressed) Color.White else accentColor
        drawPath(path = path, color = rimColor, style = Stroke(width = 2.dp.toPx()))

        // Corner tick brackets
        val bracketLen = 8.dp.toPx()
        val bStroke = 2.dp.toPx()
        drawLine(rimColor, Offset(0f, cut), Offset(0f, cut + bracketLen), strokeWidth = bStroke)
        drawLine(rimColor, Offset(w, cut), Offset(w, cut + bracketLen), strokeWidth = bStroke)
        drawLine(rimColor, Offset(0f, h - cut), Offset(0f, h - cut - bracketLen), strokeWidth = bStroke)
        drawLine(rimColor, Offset(w, h - cut), Offset(w, h - cut - bracketLen), strokeWidth = bStroke)
    }

    Text(
        text = label,
        color = Color.White,
        fontWeight = FontWeight.Black,
        fontSize = if (label.length > 3) 10.sp else if (label.length > 1) 13.sp else 18.sp
    )
}

// ============================================================================
// ELONGATED HEX-CHAMFERED BUTTON (START / MENU)
// ============================================================================

@Composable
fun SystemHexButton(
    label: String,
    subIcon: String,
    isPressed: Boolean = false
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cutX = 14.dp.toPx()

        val hexPath = Path().apply {
            moveTo(cutX, 0f)
            lineTo(w - cutX, 0f)
            lineTo(w, h / 2f)
            lineTo(w - cutX, h)
            lineTo(cutX, h)
            lineTo(0f, h / 2f)
            close()
        }

        val fillColors = if (isPressed) {
            listOf(Color(0xFFD50000), Color(0xFF8A0E13))
        } else {
            listOf(Color(0xFF2E313E), Color(0xFF181A22), Color(0xFF0F1016))
        }

        drawPath(path = hexPath, brush = Brush.verticalGradient(fillColors))

        val rimColor = if (isPressed) Color.White else Color(0xFFFF2028)
        drawPath(path = hexPath, color = rimColor, style = Stroke(width = 2.dp.toPx()))

        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(cutX, 2f),
            end = Offset(w - cutX, 2f),
            strokeWidth = 1.dp.toPx()
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = subIcon,
            color = Color(0xFFFF3038),
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

// ============================================================================
// COLOR AND PRESET CALCULATIONS
// ============================================================================

fun getButtonAccentColor(id: String): Color {
    return Color(ControllerVisualSpecs.getButtonAccentColorInt(id))
}

fun getLabelForId(id: String): String {
    return when (id) {
        "dpad_up" -> "↑"
        "dpad_down" -> "↓"
        "dpad_left" -> "←"
        "dpad_right" -> "→"
        "btn_start" -> "START"
        "btn_menu" -> "MENU"
        "macro_charge" -> "CHG"
        "macro_ab" -> "A+B"
        "macro_bc" -> "B+C"
        "macro_xy" -> "X+Y"
        "macro_abc" -> "ABC"
        "macro_round_reset" -> "RESET"
        "macro_max_power" -> "MAX"
        "macro_control_swap" -> "SWAP"
        "macro_team_switch" -> "TAG"
        else -> id.removePrefix("btn_").removePrefix("macro_").uppercase()
    }
}

fun getPresetLayout(preset: ControllerPreset, widthPx: Float, heightPx: Float): List<ControllerButtonState> {
    val unit = (widthPx * 0.075f).coerceIn(60f, 110f)

    // D-Pad Coordinates (Centered on Left)
    val dpadCx = widthPx * 0.22f
    val dpadCy = heightPx * 0.58f

    val dpad = listOf(
        ControllerButtonState("dpad_up", dpadCx - unit * 0.45f, dpadCy - unit * 1.15f, "↑"),
        ControllerButtonState("dpad_down", dpadCx - unit * 0.45f, dpadCy + unit * 0.35f, "↓"),
        ControllerButtonState("dpad_left", dpadCx - unit * 1.20f, dpadCy - unit * 0.40f, "←"),
        ControllerButtonState("dpad_right", dpadCx + unit * 0.30f, dpadCy - unit * 0.40f, "→")
    )

    // System Buttons Coordinates (Bottom Center)
    val system = listOf(
        ControllerButtonState("btn_start", widthPx * 0.42f - unit * 0.70f, heightPx * 0.80f, "START", scale = 0.85f),
        ControllerButtonState("btn_menu", widthPx * 0.58f - unit * 0.50f, heightPx * 0.80f, "MENU", scale = 0.85f)
    )

    // Attack Action Buttons Coordinates (Arcade Cab Curved Layout on Right)
    val actionCx = widthPx * 0.78f
    val actionCy = heightPx * 0.58f

    val actionButtons = when (preset) {
        ControllerPreset.CLASSIC -> listOf(
            ControllerButtonState("btn_a", actionCx + unit * 0.90f, actionCy, "A"),
            ControllerButtonState("btn_b", actionCx, actionCy + unit * 0.90f, "B"),
            ControllerButtonState("btn_x", actionCx, actionCy - unit * 0.90f, "X"),
            ControllerButtonState("btn_y", actionCx - unit * 0.90f, actionCy, "Y")
        )
        ControllerPreset.ARCADE -> listOf(
            ControllerButtonState("btn_x", actionCx - unit * 1.20f, actionCy - unit * 0.60f, "X"),
            ControllerButtonState("btn_y", actionCx, actionCy - unit * 0.60f, "Y"),
            ControllerButtonState("btn_z", actionCx + unit * 1.20f, actionCy - unit * 0.60f, "Z"),
            ControllerButtonState("btn_a", actionCx - unit * 1.20f, actionCy + unit * 0.60f, "A"),
            ControllerButtonState("btn_b", actionCx, actionCy + unit * 0.60f, "B"),
            ControllerButtonState("btn_c", actionCx + unit * 1.20f, actionCy + unit * 0.60f, "C")
        )
        else -> listOf(
            // Top Row: X (left), Y (center elevated), Z (right)
            ControllerButtonState("btn_x", actionCx - unit * 1.25f, actionCy - unit * 0.45f, "X"),
            ControllerButtonState("btn_y", actionCx, actionCy - unit * 0.85f, "Y"),
            ControllerButtonState("btn_z", actionCx + unit * 1.25f, actionCy - unit * 0.45f, "Z"),
            // Bottom Row: A (left), B (center), C (right)
            ControllerButtonState("btn_a", actionCx - unit * 1.25f, actionCy + unit * 0.95f, "A"),
            ControllerButtonState("btn_b", actionCx, actionCy + unit * 0.55f, "B"),
            ControllerButtonState("btn_c", actionCx + unit * 1.25f, actionCy + unit * 0.95f, "C")
        )
    }

    return dpad + system + actionButtons
}
