package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.controllerEditor.ControllerButtonState
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
        // Stored value remains preserved or handled safely
        assertEquals("NON_EXISTENT_SHAPE_XYZ", models.first().shape)

        // Verifying that drawing with this shape falls back safely to arcade rendering without exceptions
        val bitmap = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        controller.draw(canvas)
    }

    /**
     * Requirement: Runtime Canvas onDraw renders all supported shapes (CIRCLE, OCTAGON, CYBER, DEFAULT)
     * in both unpressed and pressed states without throwing any runtime or rendering exceptions.
     */
    @Test
    fun testNativeVirtualControllerViewRendersAllShapes() {
        val controller = NativeVirtualControllerView(context)
        val testButtons = listOf(
            ControllerButtonState("dpad_up", 120f, 100f, "↑", shape = "OCTAGON"),
            ControllerButtonState("btn_start", 350f, 380f, "START", shape = "DEFAULT"),
            ControllerButtonState("btn_x", 600f, 150f, "X", shape = "CIRCLE"),
            ControllerButtonState("btn_y", 680f, 120f, "Y", shape = "CYBER"),
            ControllerButtonState("btn_a", 600f, 250f, "A", shape = "DEFAULT")
        )
        controller.applyControllerButtons(testButtons)

        val bitmap = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw unpressed state
        controller.draw(canvas)

        // 2. Simulate multi-touch press on buttons
        val btnX = controller.getButtonModels().first { it.id == "btn_x" }
        val downTime = android.os.SystemClock.uptimeMillis()
        val event = android.view.MotionEvent.obtain(
            downTime, downTime,
            android.view.MotionEvent.ACTION_DOWN,
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
}
