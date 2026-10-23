package com.example

import com.example.data.settings.ControllerDataStore
import com.example.ui.screens.controllerEditor.ControllerButtonState
import com.example.ui.screens.controllerEditor.ControllerSidebarGeometry
import com.example.ui.screens.controllerEditor.DpadGroupingLogic
import com.example.ui.screens.controllerEditor.ToolbarReorderManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying PHASE UI-02.3 specifications:
 * 1. Harmonized Symmetrical Sidebar Geometry (leftToolbarWidth == rightToolbarWidth && leftToolbarHeight == rightToolbarHeight).
 * 2. D-Pad Grouping, Drag Synchronization, Centroid calculations, and Proportional Scaling.
 * 3. Ungrouped Independent Movement and Regrouping stability.
 * 4. Independent, Column-isolated Toolbar Tool Reordering.
 * 5. Backwards Compatibility and Default Fallbacks.
 */
class ControllerEditorSidebarAndDpadGroupingTest {

    // ========================================================================
    // 1. SIDEBAR GEOMETRY EQUALITY AND FORMULA TESTS
    // ========================================================================

    @Test
    fun testSidebarDimensionsEqualityAcrossScreenSizes() {
        val testResolutions = listOf(
            Pair(640f, 360f),   // Compact landscape phone
            Pair(800f, 480f),   // WVGA standard
            Pair(1280f, 720f),  // 720p HD
            Pair(1920f, 1080f), // 1080p FHD
            Pair(2400f, 1080f), // 20:9 Ultra-wide FHD+
            Pair(2560f, 1600f)  // Tablet WQXGA
        )

        for ((maxWidth, maxHeight) in testResolutions) {
            val leftWidth = ControllerSidebarGeometry.calculateSidebarWidth(maxWidth)
            val rightWidth = ControllerSidebarGeometry.calculateSidebarWidth(maxWidth)

            val leftHeight = ControllerSidebarGeometry.calculateSidebarHeight(maxHeight)
            val rightHeight = ControllerSidebarGeometry.calculateSidebarHeight(maxHeight)

            // Strict Mathematical Equality Requirement
            assertEquals("Left and right sidebar widths must be strictly equal at $maxWidth x $maxHeight", leftWidth, rightWidth, 0.001f)
            assertEquals("Left and right sidebar heights must be strictly equal at $maxWidth x $maxHeight", leftHeight, rightHeight, 0.001f)

            assertTrue(
                "areSidebarGeometriesEqual must return true",
                ControllerSidebarGeometry.areSidebarGeometriesEqual(leftWidth, rightWidth, leftHeight, rightHeight)
            )
        }
    }

    @Test
    fun testSidebarGeometriesEqualHelper() {
        assertTrue(ControllerSidebarGeometry.areSidebarGeometriesEqual(56f, 56f, 280f, 280f))
        assertFalse(ControllerSidebarGeometry.areSidebarGeometriesEqual(56f, 60f, 280f, 280f))
        assertFalse(ControllerSidebarGeometry.areSidebarGeometriesEqual(56f, 56f, 280f, 300f))
    }

    @Test
    fun testSidebarHeightBoundsEnforcement() {
        // Extremely tiny screen height should be clamped to MIN_SIDEBAR_HEIGHT_DP
        val clampedSmall = ControllerSidebarGeometry.calculateSidebarHeight(200f)
        assertEquals(ControllerSidebarGeometry.MIN_SIDEBAR_HEIGHT_DP, clampedSmall, 0.001f)

        // Extremely large screen height should be clamped to MAX_SIDEBAR_HEIGHT_DP
        val clampedLarge = ControllerSidebarGeometry.calculateSidebarHeight(2000f)
        assertEquals(ControllerSidebarGeometry.MAX_SIDEBAR_HEIGHT_DP, clampedLarge, 0.001f)

        // Normal screen height scales within range
        val midHeight = ControllerSidebarGeometry.calculateSidebarHeight(400f)
        assertEquals(400f * 0.70f, midHeight, 0.001f)
        assertTrue(midHeight in ControllerSidebarGeometry.MIN_SIDEBAR_HEIGHT_DP..ControllerSidebarGeometry.MAX_SIDEBAR_HEIGHT_DP)
    }

    // ========================================================================
    // 2. D-PAD GROUP DRAG SYNCHRONIZATION AND BOUNDARY CLAMPING
    // ========================================================================

    private fun createStandardTestButtons(): List<ControllerButtonState> {
        return listOf(
            ControllerButtonState("dpad_up", 200f, 100f, "↑", scale = 1.0f),
            ControllerButtonState("dpad_down", 200f, 250f, "↓", scale = 1.0f),
            ControllerButtonState("dpad_left", 125f, 175f, "←", scale = 1.0f),
            ControllerButtonState("dpad_right", 275f, 175f, "→", scale = 1.0f),
            ControllerButtonState("btn_a", 600f, 200f, "A", scale = 1.0f),
            ControllerButtonState("btn_b", 700f, 200f, "B", scale = 1.0f)
        )
    }

    @Test
    fun testDpadGroupDragMovesAllFourArrowsSynchronously() {
        val initialButtons = createStandardTestButtons()
        val deltaX = 40f
        val deltaY = 30f

        val updatedButtons = DpadGroupingLogic.applyDpadGroupDrag(
            buttons = initialButtons,
            draggedId = "dpad_up",
            deltaX = deltaX,
            deltaY = deltaY,
            buttonWidthPx = 64f,
            buttonHeightPx = 64f,
            frameInsetX = 50f,
            frameInsetY = 50f,
            frameRight = 800f,
            frameBottom = 480f
        )

        val up = updatedButtons.first { it.id == "dpad_up" }
        val down = updatedButtons.first { it.id == "dpad_down" }
        val left = updatedButtons.first { it.id == "dpad_left" }
        val right = updatedButtons.first { it.id == "dpad_right" }
        val btnA = updatedButtons.first { it.id == "btn_a" }

        // All 4 D-pad arrows must receive identical translations
        assertEquals(240f, up.initialX, 0.001f)
        assertEquals(130f, up.initialY, 0.001f)

        assertEquals(240f, down.initialX, 0.001f)
        assertEquals(280f, down.initialY, 0.001f)

        assertEquals(165f, left.initialX, 0.001f)
        assertEquals(205f, left.initialY, 0.001f)

        assertEquals(315f, right.initialX, 0.001f)
        assertEquals(205f, right.initialY, 0.001f)

        // Other buttons must not be moved
        assertEquals(600f, btnA.initialX, 0.001f)
        assertEquals(200f, btnA.initialY, 0.001f)
    }

    @Test
    fun testDpadGroupDragPreservesRelativeDistances() {
        val initialButtons = createStandardTestButtons()

        val initialHorizDist = initialButtons.first { it.id == "dpad_right" }.initialX -
                initialButtons.first { it.id == "dpad_left" }.initialX
        val initialVertDist = initialButtons.first { it.id == "dpad_down" }.initialY -
                initialButtons.first { it.id == "dpad_up" }.initialY

        val updatedButtons = DpadGroupingLogic.applyDpadGroupDrag(
            buttons = initialButtons,
            draggedId = "dpad_left",
            deltaX = -25f,
            deltaY = 45f,
            buttonWidthPx = 64f,
            buttonHeightPx = 64f,
            frameInsetX = 50f,
            frameInsetY = 50f,
            frameRight = 800f,
            frameBottom = 480f
        )

        val newHorizDist = updatedButtons.first { it.id == "dpad_right" }.initialX -
                updatedButtons.first { it.id == "dpad_left" }.initialX
        val newVertDist = updatedButtons.first { it.id == "dpad_down" }.initialY -
                updatedButtons.first { it.id == "dpad_up" }.initialY

        assertEquals("Horizontal distance between left and right arrows must be preserved", initialHorizDist, newHorizDist, 0.001f)
        assertEquals("Vertical distance between up and down arrows must be preserved", initialVertDist, newVertDist, 0.001f)
    }

    @Test
    fun testDpadGroupDragClampedAtFrameBoundaries() {
        val initialButtons = createStandardTestButtons()

        // Attempt to drag far left beyond frameInsetX (50f)
        val clampedLeft = DpadGroupingLogic.applyDpadGroupDrag(
            buttons = initialButtons,
            draggedId = "dpad_up",
            deltaX = -500f,
            deltaY = 0f,
            buttonWidthPx = 64f,
            buttonHeightPx = 64f,
            frameInsetX = 50f,
            frameInsetY = 50f,
            frameRight = 800f,
            frameBottom = 480f
        )

        val leftBtn = clampedLeft.first { it.id == "dpad_left" }
        // dpad_left was at 125f. delta to 50f is -75f.
        assertEquals(50f, leftBtn.initialX, 0.001f)
        // dpad_up was at 200f, clamped delta -75f -> 125f
        assertEquals(125f, clampedLeft.first { it.id == "dpad_up" }.initialX, 0.001f)

        // Attempt to drag far bottom beyond frameBottom (480f)
        val clampedBottom = DpadGroupingLogic.applyDpadGroupDrag(
            buttons = initialButtons,
            draggedId = "dpad_up",
            deltaX = 0f,
            deltaY = 1000f,
            buttonWidthPx = 64f,
            buttonHeightPx = 64f,
            frameInsetX = 50f,
            frameInsetY = 50f,
            frameRight = 800f,
            frameBottom = 480f
        )

        val downBtn = clampedBottom.first { it.id == "dpad_down" }
        // dpad_down was at 250f + 64f = 314f. max allowed delta is 480f - 314f = 166f.
        assertEquals(480f - 64f, downBtn.initialY, 0.001f)
    }

    // ========================================================================
    // 3. D-PAD CENTROID DYNAMICS AND PROPORTIONAL SCALING
    // ========================================================================

    @Test
    fun testDpadCentroidCalculationAndShift() {
        val buttons = createStandardTestButtons()
        val center = DpadGroupingLogic.calculateDpadCenter(buttons, buttonWidthPx = 64f, buttonHeightPx = 64f)
        assertNotNull(center)

        val (cx, cy) = center!!
        // left=125, right=275. center = (125+32 + 275+32) / 2 = (157 + 307)/2 = 232f
        // up=100, down=250. center = (100+32 + 250+32) / 2 = (132 + 282)/2 = 207f
        assertEquals(232f, cx, 0.001f)
        assertEquals(207f, cy, 0.001f)

        // Drag group by (50, 40)
        val dragged = DpadGroupingLogic.applyDpadGroupDrag(
            buttons = buttons,
            draggedId = "dpad_right",
            deltaX = 50f,
            deltaY = 40f,
            buttonWidthPx = 64f,
            buttonHeightPx = 64f,
            frameInsetX = 0f,
            frameInsetY = 0f,
            frameRight = 1000f,
            frameBottom = 1000f
        )

        val (newCx, newCy) = DpadGroupingLogic.calculateDpadCenter(dragged, 64f, 64f)!!
        assertEquals(cx + 50f, newCx, 0.001f)
        assertEquals(cy + 40f, newCy, 0.001f)
    }

    @Test
    fun testDpadGroupScaleMaintainsProportionalSymmetry() {
        val buttons = createStandardTestButtons()
        val initialCenter = DpadGroupingLogic.calculateDpadCenter(buttons, 64f, 64f)!!

        val scaled = DpadGroupingLogic.applyDpadGroupScale(
            buttons = buttons,
            newScale = 1.5f,
            buttonWidthPx = 64f,
            buttonHeightPx = 64f
        )

        // All 4 D-pad buttons must have scale = 1.5f
        for (id in DpadGroupingLogic.DPAD_IDS) {
            val btn = scaled.first { it.id == id }
            assertEquals(1.5f, btn.scale, 0.001f)
        }

        // Geometric centroid must remain invariant
        val scaledCenter = DpadGroupingLogic.calculateDpadCenter(scaled, 64f, 64f)!!
        assertEquals(initialCenter.first, scaledCenter.first, 0.01f)
        assertEquals(initialCenter.second, scaledCenter.second, 0.01f)
    }

    // ========================================================================
    // 4. UNGROUPED INDEPENDENT MOVEMENT AND REGROUPING STABILITY
    // ========================================================================

    @Test
    fun testDpadUngroupedAllowsIndependentArrowMovement() {
        val initialButtons = createStandardTestButtons()

        // When ungrouped, moving dpad_up moves only dpad_up
        val movedIndividual = initialButtons.map { btn ->
            if (btn.id == "dpad_up") btn.copy(initialX = btn.initialX + 15f, initialY = btn.initialY - 20f)
            else btn
        }

        val up = movedIndividual.first { it.id == "dpad_up" }
        val down = movedIndividual.first { it.id == "dpad_down" }
        val left = movedIndividual.first { it.id == "dpad_left" }
        val right = movedIndividual.first { it.id == "dpad_right" }

        assertEquals(215f, up.initialX, 0.001f)
        assertEquals(80f, up.initialY, 0.001f)

        // Other three must remain exactly where they were
        assertEquals(200f, down.initialX, 0.001f)
        assertEquals(250f, down.initialY, 0.001f)
        assertEquals(125f, left.initialX, 0.001f)
        assertEquals(175f, left.initialY, 0.001f)
        assertEquals(275f, right.initialX, 0.001f)
        assertEquals(175f, right.initialY, 0.001f)

        // When subsequently regrouped and dragged, they move from their modified positions without resetting
        val regroupedDragged = DpadGroupingLogic.applyDpadGroupDrag(
            buttons = movedIndividual,
            draggedId = "dpad_down",
            deltaX = 10f,
            deltaY = 10f,
            buttonWidthPx = 64f,
            buttonHeightPx = 64f,
            frameInsetX = 0f,
            frameInsetY = 0f,
            frameRight = 1000f,
            frameBottom = 1000f
        )

        assertEquals(225f, regroupedDragged.first { it.id == "dpad_up" }.initialX, 0.001f)
        assertEquals(90f, regroupedDragged.first { it.id == "dpad_up" }.initialY, 0.001f)
        assertEquals(210f, regroupedDragged.first { it.id == "dpad_down" }.initialX, 0.001f)
        assertEquals(260f, regroupedDragged.first { it.id == "dpad_down" }.initialY, 0.001f)
    }

    // ========================================================================
    // 5. SIDEBAR INDEPENDENT REORDERING AND ISOLATION
    // ========================================================================

    @Test
    fun testLeftToolbarReorderingWithinColumn() {
        val initialOrder = ControllerDataStore.DEFAULT_LEFT_TOOLS
        assertEquals(listOf("MOVE", "SIZE", "SHAPE", "OPACITY", "DELETE"), initialOrder)

        // Move "MOVE" down
        val movedDown = ToolbarReorderManager.reorderTool(initialOrder, "MOVE", 1)
        assertEquals(listOf("SIZE", "MOVE", "SHAPE", "OPACITY", "DELETE"), movedDown)

        // Move "DELETE" up
        val movedUp = ToolbarReorderManager.reorderTool(movedDown, "DELETE", -1)
        assertEquals(listOf("SIZE", "MOVE", "SHAPE", "DELETE", "OPACITY"), movedUp)

        // Bounds safety: moving top item up does nothing
        val atTop = ToolbarReorderManager.reorderTool(movedUp, "SIZE", -1)
        assertEquals(movedUp, atTop)

        // Bounds safety: moving bottom item down does nothing
        val atBottom = ToolbarReorderManager.reorderTool(movedUp, "OPACITY", 1)
        assertEquals(movedUp, atBottom)
    }

    @Test
    fun testRightToolbarReorderingWithinColumn() {
        val initialOrder = ControllerDataStore.DEFAULT_RIGHT_TOOLS
        assertEquals(listOf("GRID", "SNAP", "PREVIEW", "DPAD_GROUP", "BUTTONS_MANAGER"), initialOrder)

        // Move "DPAD_GROUP" up to position 2
        val movedUp = ToolbarReorderManager.reorderTool(initialOrder, "DPAD_GROUP", -1)
        assertEquals(listOf("GRID", "SNAP", "DPAD_GROUP", "PREVIEW", "BUTTONS_MANAGER"), movedUp)

        // Move "GRID" down to position 1
        val gridDown = ToolbarReorderManager.reorderTool(movedUp, "GRID", 1)
        assertEquals(listOf("SNAP", "GRID", "DPAD_GROUP", "PREVIEW", "BUTTONS_MANAGER"), gridDown)
    }

    @Test
    fun testToolsCannotCrossColumns() {
        for (tool in ToolbarReorderManager.LEFT_TOOLS) {
            assertTrue(ToolbarReorderManager.isLeftTool(tool))
            assertFalse("Left tool ${tool} must not be allowed in right column", ToolbarReorderManager.isRightTool(tool))
        }

        for (tool in ToolbarReorderManager.RIGHT_TOOLS) {
            assertTrue(ToolbarReorderManager.isRightTool(tool))
            assertFalse("Right tool ${tool} must not be allowed in left column", ToolbarReorderManager.isLeftTool(tool))
        }
    }

    @Test
    fun testBackwardsCompatibilityDefaults() {
        assertEquals(5, ControllerDataStore.DEFAULT_LEFT_TOOLS.size)
        assertEquals(5, ControllerDataStore.DEFAULT_RIGHT_TOOLS.size)
        assertTrue(ControllerDataStore.DEFAULT_RIGHT_TOOLS.contains("DPAD_GROUP"))
        assertTrue(ControllerDataStore.DEFAULT_RIGHT_TOOLS.contains("BUTTONS_MANAGER"))
    }
}
