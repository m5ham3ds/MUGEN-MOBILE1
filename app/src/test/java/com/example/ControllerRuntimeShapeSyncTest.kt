package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.controllerEditor.ControllerButtonState
import com.example.ui.screens.controllerEditor.ControllerVisualSpecs
import com.example.ui.screens.runtime.NativeVirtualControllerView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControllerRuntimeShapeSyncTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Requirement: Button shapes (DEFAULT, CIRCLE, OCTAGON, CYBER) serialize to and deserialize from JSON.
     */
    @Test
    fun testShapeSerializationAndDeserialization() {
        val originalButtons = listOf(
            ControllerButtonState("btn_a", 100f, 150f, "A", scale = 1.0f, opacity = 0.8f, isEnabled = true, isMacro = false, shape = "OCTAGON"),
            ControllerButtonState("btn_b", 200f, 150f, "B", scale = 1.2f, opacity = 0.9f, isEnabled = true, isMacro = false, shape = "CYBER"),
            ControllerButtonState("btn_x", 300f, 150f, "X", scale = 0.9f, opacity = 0.7f, isEnabled = true, isMacro = false, shape = "CIRCLE"),
            ControllerButtonState("btn_y", 400f, 150f, "Y", scale = 1.0f, opacity = 0.8f, isEnabled = true, isMacro = false, shape = "DEFAULT")
        )

        val jsonArray = JSONArray()
        originalButtons.forEach { b ->
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

        val jsonStr = jsonArray.toString()
        val controller = NativeVirtualControllerView(context)
        controller.updateLayoutFromJson(jsonStr, 800f, 480f)

        val loadedModels = controller.getButtonModels()
        assertEquals(4, loadedModels.size)

        val btnA = loadedModels.first { it.id == "btn_a" }
        val btnB = loadedModels.first { it.id == "btn_b" }
        val btnX = loadedModels.first { it.id == "btn_x" }
        val btnY = loadedModels.first { it.id == "btn_y" }

        assertEquals("OCTAGON", btnA.shape)
        assertEquals("CYBER", btnB.shape)
        assertEquals("CIRCLE", btnX.shape)
        assertEquals("DEFAULT", btnY.shape)
    }

    /**
     * Requirement: Legacy JSON layouts lacking the "shape" property must gracefully default to "DEFAULT".
     */
    @Test
    fun testLegacyLayoutWithoutShapeDefaultsSafely() {
        val legacyJson = """
            [
                {"id":"dpad_up","x":100.0,"y":100.0,"scale":1.0,"opacity":0.7,"enabled":true,"macro":false},
                {"id":"btn_a","x":500.0,"y":200.0,"scale":1.1,"opacity":0.85,"enabled":true,"macro":false}
            ]
        """.trimIndent()

        val controller = NativeVirtualControllerView(context)
        controller.updateLayoutFromJson(legacyJson, 800f, 480f)

        val models = controller.getButtonModels()
        assertEquals(2, models.size)
        assertEquals("DEFAULT", models.first { it.id == "dpad_up" }.shape)
        assertEquals("DEFAULT", models.first { it.id == "btn_a" }.shape)
    }

    /**
     * Requirement: Unknown or invalid shape strings safely fall back without crashing or corrupting data.
     */
    @Test
    fun testInvalidShapeFallback() {
        val testJson = """
            [
                {"id":"btn_a","x":500.0,"y":200.0,"scale":1.0,"opacity":0.8,"enabled":true,"macro":false,"shape":"NON_EXISTENT_SHAPE_XYZ"}
            ]
        """.trimIndent()

        val controller = NativeVirtualControllerView(context)
        controller.updateLayoutFromJson(testJson, 800f, 480f)

        val models = controller.getButtonModels()
        assertEquals(1, models.size)
        assertEquals("NON_EXISTENT_SHAPE_XYZ", models.first().shape)

        // Verifying that drawing with this shape falls back safely to arcade rendering without exceptions
        val bitmap = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        controller.draw(canvas)
    }

    /**
     * Requirement: Runtime Canvas onDraw renders all supported shapes (CIRCLE, OCTAGON, CYBER, DEFAULT)
     * and specialized arcade / system components in both unpressed and pressed states without throwing exceptions.
     */
    @Test
    fun testNativeVirtualControllerViewRendersAllShapes() {
        val controller = NativeVirtualControllerView(context)
        val testButtons = listOf(
            ControllerButtonState("dpad_up", 120f, 100f, "↑", shape = "OCTAGON"),
            ControllerButtonState("dpad_down", 120f, 180f, "↓", shape = "OCTAGON"),
            ControllerButtonState("dpad_left", 60f, 140f, "←", shape = "OCTAGON"),
            ControllerButtonState("dpad_right", 180f, 140f, "→", shape = "OCTAGON"),
            ControllerButtonState("btn_start", 350f, 380f, "START", shape = "DEFAULT"),
            ControllerButtonState("btn_menu", 460f, 380f, "MENU", shape = "DEFAULT"),
            ControllerButtonState("macro_charge", 300f, 200f, "CHG", isMacro = true, shape = "CYBER"),
            ControllerButtonState("btn_x", 600f, 150f, "X", shape = "CIRCLE"),
            ControllerButtonState("btn_y", 680f, 120f, "Y", shape = "CYBER"),
            ControllerButtonState("btn_z", 760f, 150f, "Z", shape = "DEFAULT"),
            ControllerButtonState("btn_a", 600f, 250f, "A", shape = "DEFAULT"),
            ControllerButtonState("btn_b", 680f, 220f, "B", shape = "DEFAULT"),
            ControllerButtonState("btn_c", 760f, 250f, "C", shape = "DEFAULT")
        )
        controller.applyControllerButtons(testButtons)

        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw unpressed state (including D-Pad background hub and decals)
        controller.draw(canvas)

        // 2. Simulate multi-touch press on buttons
        val btnX = controller.getButtonModels().first { it.id == "btn_x" }
        val downTime = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            btnX.centerX, btnX.centerY, 0
        )
        controller.onTouchEvent(event)
        assertTrue("btn_x should be recorded as pressed", controller.getPressedButtonIds().contains("btn_x"))

        // Draw pressed state
        controller.draw(canvas)
        event.recycle()
    }

    /**
     * Requirement: Grid snapping math accurately aligns coordinates with grid step.
     */
    @Test
    fun testGridSnapCalculation() {
        val frameInsetX = 64f
        val frameInsetY = 58f
        val gridSpacing = 32f

        // A raw position between grid lines
        val rawX = frameInsetX + 50f // Should snap to frameInsetX + 64f (closest 32 increment)
        val rawY = frameInsetY + 12f // Should snap to frameInsetY + 0f (closest 32 increment)

        val snappedX = frameInsetX + ((rawX - frameInsetX) / gridSpacing).roundToInt() * gridSpacing
        val snappedY = frameInsetY + ((rawY - frameInsetY) / gridSpacing).roundToInt() * gridSpacing

        assertEquals(frameInsetX + 64f, snappedX, 0.001f)
        assertEquals(frameInsetY, snappedY, 0.001f)
    }

    /**
     * Requirement: Button dimensions and centers match ControllerVisualSpecs between editor and native view.
     */
    @Test
    fun testButtonGeometryAndCenterParity() {
        val density = context.resources.displayMetrics.density
        val controller = NativeVirtualControllerView(context)

        val testButtons = listOf(
            ControllerButtonState("btn_start", 200f, 300f, "START", scale = 1.0f),
            ControllerButtonState("macro_charge", 400f, 150f, "CHG", isMacro = true, scale = 1.0f),
            ControllerButtonState("dpad_up", 100f, 100f, "↑", scale = 1.0f),
            ControllerButtonState("btn_a", 500f, 300f, "A", scale = 1.0f)
        )
        controller.applyControllerButtons(testButtons)

        val models = controller.getButtonModels()

        // START: 92x46dp
        val startModel = models.first { it.id == "btn_start" }
        assertEquals(92f * density, startModel.widthPx, 0.001f)
        assertEquals(46f * density, startModel.heightPx, 0.001f)
        assertEquals(200f + (92f * density) / 2f, startModel.centerX, 0.001f)
        assertEquals(300f + (46f * density) / 2f, startModel.centerY, 0.001f)

        // MACRO: 76x66dp
        val macroModel = models.first { it.id == "macro_charge" }
        assertEquals(76f * density, macroModel.widthPx, 0.001f)
        assertEquals(66f * density, macroModel.heightPx, 0.001f)
        assertEquals(400f + (76f * density) / 2f, macroModel.centerX, 0.001f)
        assertEquals(150f + (66f * density) / 2f, macroModel.centerY, 0.001f)

        // DPAD: 64x64dp
        val dpadModel = models.first { it.id == "dpad_up" }
        assertEquals(64f * density, dpadModel.widthPx, 0.001f)
        assertEquals(64f * density, dpadModel.heightPx, 0.001f)
        assertEquals(100f + (64f * density) / 2f, dpadModel.centerX, 0.001f)
        assertEquals(100f + (64f * density) / 2f, dpadModel.centerY, 0.001f)

        // ACTION A: 66x66dp
        val aModel = models.first { it.id == "btn_a" }
        assertEquals(66f * density, aModel.widthPx, 0.001f)
        assertEquals(66f * density, aModel.heightPx, 0.001f)
        assertEquals(500f + (66f * density) / 2f, aModel.centerX, 0.001f)
        assertEquals(300f + (66f * density) / 2f, aModel.centerY, 0.001f)
    }

    /**
     * Requirement: Fighting game accent colors match ControllerVisualSpecs.
     */
    @Test
    fun testAccentColorsMatchSpecs() {
        assertEquals(android.graphics.Color.rgb(0, 176, 255), ControllerVisualSpecs.getButtonAccentColorInt("btn_x"))
        assertEquals(android.graphics.Color.rgb(255, 179, 0), ControllerVisualSpecs.getButtonAccentColorInt("btn_y"))
        assertEquals(android.graphics.Color.rgb(170, 0, 255), ControllerVisualSpecs.getButtonAccentColorInt("btn_z"))
        assertEquals(android.graphics.Color.rgb(255, 23, 68), ControllerVisualSpecs.getButtonAccentColorInt("btn_a"))
        assertEquals(android.graphics.Color.rgb(0, 230, 118), ControllerVisualSpecs.getButtonAccentColorInt("btn_b"))
        assertEquals(android.graphics.Color.rgb(255, 109, 0), ControllerVisualSpecs.getButtonAccentColorInt("btn_c"))
        assertEquals(android.graphics.Color.rgb(255, 32, 40), ControllerVisualSpecs.getButtonAccentColorInt("btn_start"))
        assertEquals(android.graphics.Color.rgb(255, 32, 40), ControllerVisualSpecs.getButtonAccentColorInt("btn_menu"))
        assertEquals(android.graphics.Color.rgb(255, 145, 0), ControllerVisualSpecs.getButtonAccentColorInt("macro_charge"))
        assertEquals(android.graphics.Color.rgb(255, 145, 0), ControllerVisualSpecs.getButtonAccentColorInt("macro_max_power"))
    }

    /**
     * Requirement: System button sub-icons correctly map to "[-]" for START and "[≡]" for MENU.
     */
    @Test
    fun testSystemButtonSubIcons() {
        assertEquals("[-]", ControllerVisualSpecs.getButtonSubIcon("btn_start"))
        assertEquals("[≡]", ControllerVisualSpecs.getButtonSubIcon("btn_menu"))
        assertNull(ControllerVisualSpecs.getButtonSubIcon("btn_a"))
    }

    /**
     * Requirement: Normalized touch detection successfully registers touches across full width of elongated buttons.
     */
    @Test
    fun testElongatedButtonTouchDetection() {
        val density = context.resources.displayMetrics.density
        val controller = NativeVirtualControllerView(context)
        val buttons = listOf(
            ControllerButtonState("btn_start", 200f, 300f, "START", scale = 1.0f)
        )
        controller.applyControllerButtons(buttons)

        val model = controller.getButtonModels().first()
        // Width is 92dp. Center is 200 + 46 = 246.
        // Touch on the right wing of START at x = 200 + 80 = 280 (34dp away from center)
        val touchX = 200f + 80f * density
        val touchY = model.centerY

        val found = controller.findButtonAt(touchX, touchY)
        assertNotNull("Touch on outer edge of START should be registered", found)
        assertEquals("btn_start", found?.id)
    }
}
