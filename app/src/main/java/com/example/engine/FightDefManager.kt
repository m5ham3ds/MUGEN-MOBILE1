package com.example.engine

import android.content.Context
import android.util.Log
import com.example.storage.MugenMobileStorage
import java.io.File
import java.io.FileOutputStream

/**
 * Diagnostic model for fight screenpack face portrait configuration.
 */
data class FightDefFaceConfig(
    val hasFaceSection: Boolean,
    val p1Pos: String?,
    val p2Pos: String?,
    val p1FaceSpr: String?,
    val p2FaceSpr: String?,
    val p1Window: String?,
    val p2Window: String?,
    val p1Scale: String?,
    val p2Scale: String?,
    val hasSafeClipping: Boolean
)

/**
 * Manages lifebar and battle HUD definition files (primarily data/fight.def).
 *
 * Solves the critical HUD portrait blowout issue in MUGEN/IKEMEN-GO:
 * In standard fight.def, the [Face] section specifies:
 *   p1.pos = 14.5, 20
 *   p2.pos = 1262, 20
 *   p1.face.spr = 9000, 0
 *   p2.face.spr = 9000, 0
 * When p1.face.window and p2.face.window are omitted, any fighter with an oversized
 * or high-resolution card sprite in group 9000 (such as anime cards like Cid Kagenou)
 * renders completely unconstrained and unclipped across the top-right of the screen,
 * covering the battle arena and mobile virtual touch controls.
 *
 * Guarantees:
 * 1. Safe non-destructive parsing of data/fight.def preserving all other sections and comments.
 * 2. Automatic pre-modification backup (.fight.def.bak).
 * 3. Enforces clean bounding windows that clip player portraits strictly within the lifebar frame.
 * 4. Completely separates lifebar portrait bounds from character body physics/scale.
 */
object FightDefManager {
    private const val TAG = "FightDefManager"

    // Default safe clipping boundaries for standard 1280x720 MUGEN1 / Ikemen-GO motifs:
    // P1 face begins at x=14.5, y=20, name starts at x=139 -> bounding box [14, 18, 134, 138]
    // P2 face begins at x=1262, y=20 (facing -1), name starts at x=1138 -> bounding box [1146, 18, 1266, 138]
    const val DEFAULT_1280_P1_WINDOW = "14, 18, 134, 138"
    const val DEFAULT_1280_P2_WINDOW = "1146, 18, 1266, 138"
    const val DEFAULT_FACE_SCALE = "1, 1"

    /**
     * Resolves the authoritative active fight.def file used by the engine.
     * Inspects data/system.def to check if a specific fight definition file is referenced.
     */
    fun resolveActiveFightDef(context: Context? = null): File {
        val baseDir = MugenMobileStorage.getBaseDir(context)
        val dataDir = File(baseDir, "data")
        val systemDef = File(dataDir, "system.def")

        if (systemDef.exists() && systemDef.canRead()) {
            var inFiles = false
            try {
                val lines = systemDef.readLines()
                for (raw in lines) {
                    val clean = raw.substringBefore(";").trim()
                    if (clean.startsWith("[") && clean.endsWith("]")) {
                        inFiles = clean.equals("[files]", ignoreCase = true)
                    } else if (inFiles && clean.contains("=")) {
                        val k = clean.substringBefore("=").trim().lowercase()
                        val v = clean.substringAfter("=").trim().replace("\"", "")
                        if (k == "fight" && v.isNotEmpty()) {
                            val candidate = if (v.startsWith("data/")) File(baseDir, v) else File(dataDir, v)
                            if (candidate.exists()) return candidate
                            val alt = File(baseDir, v)
                            if (alt.exists()) return alt
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notice inspecting system.def for fight path: ${e.message}")
            }
        }

        if (!dataDir.exists()) dataDir.mkdirs()
        return File(dataDir, "fight.def")
    }

    /**
     * Resolves the authoritative data/fight.def file in the active game directory.
     */
    fun getFightDefFile(context: Context? = null): File {
        return resolveActiveFightDef(context)
    }

    /**
     * Validates if a window specification represents a safe, bounded portrait clipping box.
     * Prevents fullscreen, unbound, or inverted coordinates.
     */
    fun isWindowSafe(windowStr: String?): Boolean {
        if (windowStr.isNullOrBlank()) return false
        val parts = windowStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 4) return false
        val x1 = parts[0]
        val y1 = parts[1]
        val x2 = parts[2]
        val y2 = parts[3]
        val width = kotlin.math.abs(x2 - x1)
        val height = kotlin.math.abs(y2 - y1)
        // A safe HUD face box in 1280x720 is between 20px and 220px in width and height
        return width in 20..220 && height in 20..220 && x1 >= 0 && y1 >= 0
    }

    /**
     * Inspects fight.def to analyze face portrait parameters.
     */
    fun inspectFightDef(fightDefFile: File): FightDefFaceConfig {
        if (!fightDefFile.exists() || !fightDefFile.canRead()) {
            return FightDefFaceConfig(
                hasFaceSection = false,
                p1Pos = null,
                p2Pos = null,
                p1FaceSpr = null,
                p2FaceSpr = null,
                p1Window = null,
                p2Window = null,
                p1Scale = null,
                p2Scale = null,
                hasSafeClipping = false
            )
        }

        var inFaceSection = false
        var hasFace = false
        var p1Pos: String? = null
        var p2Pos: String? = null
        var p1FaceSpr: String? = null
        var p2FaceSpr: String? = null
        var p1Window: String? = null
        var p2Window: String? = null
        var p1Scale: String? = null
        var p2Scale: String? = null

        fightDefFile.forEachLine { rawLine ->
            val clean = rawLine.substringBefore(";").trim()
            if (clean.startsWith("[") && clean.endsWith("]")) {
                val secName = clean.substring(1, clean.length - 1).trim().lowercase()
                inFaceSection = (secName == "face")
                if (inFaceSection) hasFace = true
            } else if (inFaceSection && clean.contains("=")) {
                val key = clean.substringBefore("=").trim().lowercase()
                val value = clean.substringAfter("=").trim()

                when (key) {
                    "p1.pos" -> p1Pos = value
                    "p2.pos" -> p2Pos = value
                    "p1.face.spr" -> p1FaceSpr = value
                    "p2.face.spr" -> p2FaceSpr = value
                    "p1.face.window" -> p1Window = value
                    "p2.face.window" -> p2Window = value
                    "p1.face.scale" -> p1Scale = value
                    "p2.face.scale" -> p2Scale = value
                }
            }
        }

        val hasSafe = isWindowSafe(p1Window) && isWindowSafe(p2Window)

        return FightDefFaceConfig(
            hasFaceSection = hasFace,
            p1Pos = p1Pos,
            p2Pos = p2Pos,
            p1FaceSpr = p1FaceSpr,
            p2FaceSpr = p2FaceSpr,
            p1Window = p1Window,
            p2Window = p2Window,
            p1Scale = p1Scale,
            p2Scale = p2Scale,
            hasSafeClipping = hasSafe
        )
    }

    /**
     * Enforces safe portrait window clipping and bounded scale in the active fight.def.
     * Prevents high-resolution character portraits or anime cards from bleeding across
     * the arena and overlapping on-screen touch controls.
     */
    @Synchronized
    fun ensureSafePortraitClipping(
        context: Context? = null,
        targetP1Window: String = DEFAULT_1280_P1_WINDOW,
        targetP2Window: String = DEFAULT_1280_P2_WINDOW,
        targetScale: String = DEFAULT_FACE_SCALE
    ): Boolean {
        val targetFiles = mutableListOf<File>()
        val primary = resolveActiveFightDef(context)
        targetFiles.add(primary)

        val defaultDataFight = File(File(MugenMobileStorage.getBaseDir(context), "data"), "fight.def")
        if (defaultDataFight.absolutePath != primary.absolutePath) {
            targetFiles.add(defaultDataFight)
        }

        var anySuccess = false
        for (fightDefFile in targetFiles) {
            try {
                if (!fightDefFile.exists()) {
                    fightDefFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                    val initialContent = """
                        ; fight.def initialized with safe bounded portrait clipping by MUGEN Mobile
                        [Face]
                        p1.pos = 14, 20
                        p1.face.spr = 9000, 0
                        p1.face.facing = 1
                        p1.face.scale = $targetScale
                        p1.face.window = $targetP1Window
                        
                        p2.pos = 1266, 20
                        p2.face.spr = 9000, 0
                        p2.face.facing = -1
                        p2.face.scale = $targetScale
                        p2.face.window = $targetP2Window
                    """.trimIndent()
                    fightDefFile.writeText(initialContent)
                    Log.i(TAG, "Created initial safe fight.def at ${fightDefFile.absolutePath}")
                    anySuccess = true
                    continue
                }

                val lines = fightDefFile.readLines().toMutableList()
                var faceHeaderIndex = -1
                var nextSectionIndex = lines.size

                // 1. Locate [Face] section
                for (i in lines.indices) {
                    val clean = lines[i].substringBefore(";").trim().lowercase()
                    if (clean == "[face]") {
                        faceHeaderIndex = i
                    } else if (faceHeaderIndex != -1 && clean.startsWith("[") && clean.endsWith("]")) {
                        nextSectionIndex = i
                        break
                    }
                }

                if (faceHeaderIndex == -1) {
                    Log.d(TAG, "[Face] section not found in fight.def, appending safe [Face] configuration.")
                    lines.add("")
                    lines.add("[Face]")
                    lines.add("; Safe bounded portrait window clipping enforced by MUGEN Mobile")
                    lines.add("p1.face.scale = $targetScale")
                    lines.add("p1.face.window = $targetP1Window")
                    lines.add("p2.face.scale = $targetScale")
                    lines.add("p2.face.window = $targetP2Window")
                } else {
                    var foundP1Window = false
                    var foundP2Window = false
                    var foundP1Scale = false
                    var foundP2Scale = false
                    var p1WindowIndex = -1
                    var p2WindowIndex = -1
                    var p1ScaleIndex = -1
                    var p2ScaleIndex = -1

                    for (i in (faceHeaderIndex + 1) until nextSectionIndex) {
                        val clean = lines[i].substringBefore(";").trim().lowercase()
                        if (clean.startsWith("p1.face.window")) {
                            foundP1Window = true
                            p1WindowIndex = i
                        } else if (clean.startsWith("p2.face.window")) {
                            foundP2Window = true
                            p2WindowIndex = i
                        } else if (clean.startsWith("p1.face.scale")) {
                            foundP1Scale = true
                            p1ScaleIndex = i
                        } else if (clean.startsWith("p2.face.scale")) {
                            foundP2Scale = true
                            p2ScaleIndex = i
                        }
                    }

                    // Enforce safe windows
                    if (foundP1Window) {
                        val current = lines[p1WindowIndex].substringAfter("=").substringBefore(";").trim()
                        if (!isWindowSafe(current)) {
                            lines[p1WindowIndex] = "p1.face.window = $targetP1Window"
                        }
                    } else {
                        lines.add(faceHeaderIndex + 1, "p1.face.window = $targetP1Window")
                        nextSectionIndex++
                    }

                    if (foundP2Window) {
                        val current = lines[p2WindowIndex].substringAfter("=").substringBefore(";").trim()
                        if (!isWindowSafe(current)) {
                            lines[p2WindowIndex] = "p2.face.window = $targetP2Window"
                        }
                    } else {
                        lines.add(faceHeaderIndex + 2, "p2.face.window = $targetP2Window")
                        nextSectionIndex++
                    }

                    // Enforce safe scale
                    if (foundP1Scale) {
                        val current = lines[p1ScaleIndex].substringAfter("=").substringBefore(";").trim()
                        if (current.isBlank()) lines[p1ScaleIndex] = "p1.face.scale = $targetScale"
                    } else {
                        lines.add(faceHeaderIndex + 1, "p1.face.scale = $targetScale")
                        nextSectionIndex++
                    }

                    if (foundP2Scale) {
                        val current = lines[p2ScaleIndex].substringAfter("=").substringBefore(";").trim()
                        if (current.isBlank()) lines[p2ScaleIndex] = "p2.face.scale = $targetScale"
                    } else {
                        lines.add(faceHeaderIndex + 2, "p2.face.scale = $targetScale")
                    }
                }

                // Create backup before writing
                val bakFile = File(fightDefFile.parentFile, "${fightDefFile.name}.bak")
                fightDefFile.copyTo(bakFile, overwrite = true)

                // Write updated lines atomically using temporary file
                val tmpFile = File(fightDefFile.parentFile, ".${fightDefFile.name}.tmp_${System.nanoTime()}")
                FileOutputStream(tmpFile).use { fos ->
                    fos.write(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
                    fos.flush()
                    fos.fd.sync()
                }

                if (!tmpFile.renameTo(fightDefFile)) {
                    tmpFile.copyTo(fightDefFile, overwrite = true)
                    tmpFile.delete()
                }

                Log.i(TAG, "Successfully enforced safe face portrait clipping in ${fightDefFile.absolutePath}")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed ensuring safe face clipping in ${fightDefFile.absolutePath}: ${e.message}", e)
            }
        }
        return anySuccess
    }
}
