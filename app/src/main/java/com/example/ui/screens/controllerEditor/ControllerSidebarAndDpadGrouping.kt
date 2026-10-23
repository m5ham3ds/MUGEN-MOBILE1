package com.example.ui.screens.controllerEditor

import kotlin.math.roundToInt

/**
 * Controller Editor Sidebar Geometry & D-Pad Grouping Logic.
 *
 * Implements PHASE UI-02.3 specifications:
 * 1. Harmonized Symmetrical Sidebar Geometry (leftToolbarWidth == rightToolbarWidth && leftToolbarHeight == rightToolbarHeight).
 * 2. D-Pad Group Drag, Scaling, and Centroid synchronization.
 * 3. Column-isolated Toolbar Tool Reordering.
 */
object ControllerSidebarGeometry {
    const val SIDEBAR_WIDTH_DP = 56f
    const val MIN_SIDEBAR_HEIGHT_DP = 240f
    const val MAX_SIDEBAR_HEIGHT_DP = 310f
    const val SIDEBAR_HEIGHT_RATIO = 0.70f

    /**
     * Common constraint formula for sidebar width.
     */
    fun calculateSidebarWidth(maxWidthDp: Float): Float {
        return SIDEBAR_WIDTH_DP
    }

    /**
     * Common constraint formula for sidebar height ensuring both left and right
     * sidebars adaptively scale identically without overflowing screen space.
     */
    fun calculateSidebarHeight(maxHeightDp: Float): Float {
        val available = maxHeightDp * SIDEBAR_HEIGHT_RATIO
        return available.coerceIn(MIN_SIDEBAR_HEIGHT_DP, MAX_SIDEBAR_HEIGHT_DP)
    }

    /**
     * Verifies that two toolbar configurations share mathematically identical geometry.
     */
    fun areSidebarGeometriesEqual(
        leftWidth: Float,
        rightWidth: Float,
        leftHeight: Float,
        rightHeight: Float
    ): Boolean {
        return leftWidth == rightWidth && leftHeight == rightHeight
    }
}

/**
 * Handles D-Pad directional arrow grouping, drag synchronization,
 * and proportional scaling around the D-pad geometric center.
 */
object DpadGroupingLogic {
    val DPAD_IDS = setOf("dpad_up", "dpad_down", "dpad_left", "dpad_right")

    fun isDpadButton(id: String): Boolean = id in DPAD_IDS

    /**
     * Calculates the dynamic geometric center of the 4 D-pad arrows.
     * Takes into account individual button offsets and scaling.
     */
    fun calculateDpadCenter(
        buttons: List<ControllerButtonState>,
        buttonWidthPx: Float = 0f,
        buttonHeightPx: Float = 0f
    ): Pair<Float, Float>? {
        val up = buttons.find { it.id == "dpad_up" } ?: return null
        val down = buttons.find { it.id == "dpad_down" } ?: return null
        val left = buttons.find { it.id == "dpad_left" } ?: return null
        val right = buttons.find { it.id == "dpad_right" } ?: return null

        val leftCenterX = left.initialX + (buttonWidthPx * left.scale) / 2f
        val rightCenterX = right.initialX + (buttonWidthPx * right.scale) / 2f
        val upCenterY = up.initialY + (buttonHeightPx * up.scale) / 2f
        val downCenterY = down.initialY + (buttonHeightPx * down.scale) / 2f

        val centerX = (leftCenterX + rightCenterX) / 2f
        val centerY = (upCenterY + downCenterY) / 2f

        return Pair(centerX, centerY)
    }

    /**
     * Applies drag delta across all D-pad buttons when grouped, strictly clamping
     * the entire group within the specified canvas frame boundaries.
     */
    fun applyDpadGroupDrag(
        buttons: List<ControllerButtonState>,
        draggedId: String,
        deltaX: Float,
        deltaY: Float,
        buttonWidthPx: Float,
        buttonHeightPx: Float,
        frameInsetX: Float,
        frameInsetY: Float,
        frameRight: Float,
        frameBottom: Float
    ): List<ControllerButtonState> {
        val dpad = buttons.filter { it.id in DPAD_IDS }
        if (dpad.size != 4) {
            // Fallback: move only the dragged button if D-pad buttons are incomplete
            return buttons.map { btn ->
                if (btn.id == draggedId) {
                    val maxX = (frameRight - buttonWidthPx * btn.scale).coerceAtLeast(frameInsetX)
                    val maxY = (frameBottom - buttonHeightPx * btn.scale).coerceAtLeast(frameInsetY)
                    btn.copy(
                        initialX = (btn.initialX + deltaX).coerceIn(frameInsetX, maxX),
                        initialY = (btn.initialY + deltaY).coerceIn(frameInsetY, maxY)
                    )
                } else {
                    btn
                }
            }
        }

        // Calculate bounding box of the entire D-pad group
        val groupMinX = dpad.minOf { it.initialX }
        val groupMaxX = dpad.maxOf { it.initialX + buttonWidthPx * it.scale }
        val groupMinY = dpad.minOf { it.initialY }
        val groupMaxY = dpad.maxOf { it.initialY + buttonHeightPx * it.scale }

        // Allowed translation deltas so the entire group stays strictly within frame bounds
        val allowedMinDeltaX = frameInsetX - groupMinX
        val allowedMaxDeltaX = frameRight - groupMaxX
        val clampedDeltaX = if (allowedMinDeltaX <= allowedMaxDeltaX) {
            deltaX.coerceIn(allowedMinDeltaX, allowedMaxDeltaX)
        } else 0f

        val allowedMinDeltaY = frameInsetY - groupMinY
        val allowedMaxDeltaY = frameBottom - groupMaxY
        val clampedDeltaY = if (allowedMinDeltaY <= allowedMaxDeltaY) {
            deltaY.coerceIn(allowedMinDeltaY, allowedMaxDeltaY)
        } else 0f

        // Apply clamped delta identically to all 4 arrows
        return buttons.map { btn ->
            if (btn.id in DPAD_IDS) {
                btn.copy(
                    initialX = btn.initialX + clampedDeltaX,
                    initialY = btn.initialY + clampedDeltaY
                )
            } else {
                btn
            }
        }
    }

    /**
     * Proportional scaling of D-pad group around its geometric centroid.
     * Keeps relative distances between all 4 arrows perfectly harmonized.
     */
    fun applyDpadGroupScale(
        buttons: List<ControllerButtonState>,
        newScale: Float,
        buttonWidthPx: Float,
        buttonHeightPx: Float,
        frameInsetX: Float = 0f,
        frameInsetY: Float = 0f,
        frameRight: Float = Float.MAX_VALUE,
        frameBottom: Float = Float.MAX_VALUE
    ): List<ControllerButtonState> {
        val center = calculateDpadCenter(buttons, buttonWidthPx, buttonHeightPx) ?: return buttons
        val (cx, cy) = center

        val updated = buttons.map { btn ->
            if (btn.id in DPAD_IDS) {
                val oldScale = btn.scale.coerceAtLeast(0.1f)
                val scaleFactor = newScale / oldScale

                val currentBtnCenterX = btn.initialX + (buttonWidthPx * btn.scale) / 2f
                val currentBtnCenterY = btn.initialY + (buttonHeightPx * btn.scale) / 2f

                val newBtnCenterX = cx + (currentBtnCenterX - cx) * scaleFactor
                val newBtnCenterY = cy + (currentBtnCenterY - cy) * scaleFactor

                val newX = newBtnCenterX - (buttonWidthPx * newScale) / 2f
                val newY = newBtnCenterY - (buttonHeightPx * newScale) / 2f

                btn.copy(
                    scale = newScale,
                    initialX = newX,
                    initialY = newY
                )
            } else {
                btn
            }
        }

        return updated
    }
}

/**
 * Manages independent reordering of tools within left and right sidebars.
 * Ensures tools can never cross over to the opposing column.
 */
object ToolbarReorderManager {
    val LEFT_TOOLS = listOf("MOVE", "SIZE", "SHAPE", "OPACITY", "DELETE")
    val RIGHT_TOOLS = listOf("GRID", "SNAP", "PREVIEW", "DPAD_GROUP", "BUTTONS_MANAGER")

    fun isLeftTool(toolId: String): Boolean = toolId in LEFT_TOOLS
    fun isRightTool(toolId: String): Boolean = toolId in RIGHT_TOOLS

    /**
     * Moves a tool up (direction = -1) or down (direction = +1) within its column.
     * Prevents tools from leaving their respective lists.
     */
    fun reorderTool(
        currentOrder: List<String>,
        toolId: String,
        direction: Int
    ): List<String> {
        val index = currentOrder.indexOf(toolId)
        if (index == -1) return currentOrder

        val targetIndex = index + direction
        if (targetIndex !in currentOrder.indices) return currentOrder

        val mutable = currentOrder.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)
        return mutable
    }
}

/**
 * Coordinate mapping bridge translating between:
 * 1. ControllerEditorScreen's bounded cybernetic editing frame [frameInsetX, frameInsetY, frameRight, frameBottom]
 * 2. NativeVirtualControllerView's full-screen runtime game display [0, 0, screenWidth, screenHeight]
 *
 * Guarantees that placing a button against the top edge of the editor frame (frameInsetY) maps 1:1
 * to the very top edge of the game screen (gameY = 0f), and placing against the left edge maps to gameX = 0f.
 */
object ControllerCoordinateMapper {
    fun mapEditorToGamePosition(
        editorX: Float,
        editorY: Float,
        btnWidthPx: Float,
        btnHeightPx: Float,
        frameInsetX: Float,
        frameInsetY: Float,
        frameRight: Float,
        frameBottom: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): Pair<Float, Float> {
        val availFrameW = (frameRight - frameInsetX - btnWidthPx).coerceAtLeast(1f)
        val availScreenW = (screenWidthPx - btnWidthPx).coerceAtLeast(1f)
        val fracX = ((editorX - frameInsetX) / availFrameW).coerceIn(0f, 1f)
        val gameX = fracX * availScreenW

        val availFrameH = (frameBottom - frameInsetY - btnHeightPx).coerceAtLeast(1f)
        val availScreenH = (screenHeightPx - btnHeightPx).coerceAtLeast(1f)
        val fracY = ((editorY - frameInsetY) / availFrameH).coerceIn(0f, 1f)
        val gameY = fracY * availScreenH

        return Pair(gameX, gameY)
    }

    fun mapGameToEditorPosition(
        gameX: Float,
        gameY: Float,
        btnWidthPx: Float,
        btnHeightPx: Float,
        frameInsetX: Float,
        frameInsetY: Float,
        frameRight: Float,
        frameBottom: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): Pair<Float, Float> {
        val availFrameW = (frameRight - frameInsetX - btnWidthPx).coerceAtLeast(1f)
        val availScreenW = (screenWidthPx - btnWidthPx).coerceAtLeast(1f)
        val fracX = (gameX / availScreenW).coerceIn(0f, 1f)
        val editorX = frameInsetX + fracX * availFrameW

        val availFrameH = (frameBottom - frameInsetY - btnHeightPx).coerceAtLeast(1f)
        val availScreenH = (screenHeightPx - btnHeightPx).coerceAtLeast(1f)
        val fracY = (gameY / availScreenH).coerceIn(0f, 1f)
        val editorY = frameInsetY + fracY * availFrameH

        return Pair(editorX, editorY)
    }
}
