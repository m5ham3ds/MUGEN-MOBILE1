package com.example.engine.scale

import com.example.engine.CharacterData
import com.example.engine.CnsEditor
import com.example.engine.DefEditor
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scale archetype classification to distinguish intentional character designs
 * (such as towering boss titans or miniature mascots) from unintended scale distortion.
 */
enum class ScaleArchetype(val arabicLabel: String, val typicalScaleRange: ClosedFloatingPointRange<Double>) {
    TINY_SMALL("مقاتل صغير / قزم (Tiny/Small)", 0.50..0.85),
    STANDARD("مقاتل قياسي (Standard)", 0.85..1.15),
    TALL_LARGE("مقاتل طويل / ضخم (Tall/Large)", 1.15..1.40),
    GIANT_BOSS("زعيم عملاق (Giant/Boss)", 1.40..2.50),
    SPECIAL_UNCLASSIFIED("خاص / غير مصنف (Special)", 0.35..2.50)
}

/**
 * Diagnostic anomaly flags identifying specific physical or visual discrepancies.
 */
enum class ScaleAnomalyType(val arabicDescription: String) {
    ASPECT_RATIO_DISTORTION("تشوه في تناسب الأبعاد (تفاوت غير مبرر بين مقياس العرض ومقياس الارتفاع)"),
    OVERSIZED_SPRITE("حجم مرئي مفرط يتجاوز النطاق التنافسي المعتاد"),
    UNDERSIZED_SPRITE("حجم مرئي ضئيل للغاية يعيق دقة الاشتباك وتحديد الضربات"),
    PUSHBOX_SCALE_MISMATCH("عدم تطابق بين مساحة الدفع/الارتفاع الفيزيائي ومقياس الرسم المرئي"),
    MISSING_OR_MISMATCHED_LOCALCOORD("دقة العرض المحلية (localcoord) غير محددة أو غير متوافقة مع أبعاد الرسوم"),
    SPEED_SCALE_IMBALANCE("سرعة الحركة الفيزيائية غير متناسقة مع مقياس وحجم الجسم"),
    ROSTER_SCALE_DEVIATION("انحراف في المقياس عن المعيار التنافسي المتوازن (1.0x)"),
    OVERSIZED_PORTRAIT("صورة المقاتل في واجهة القتال (Portrait) غير مقيدة وتتطلب ضبط نافذة العرض")
}

/**
 * Physical collision pushbox and reference dimensions.
 *
 * NOTE: In MUGEN / IKEMEN-GO, `height` is the internal collision/throw height declared in [size] height,
 * NOT the visual sprite pixel height of the character.
 */
data class PhysicalDimensions(
    val height: Int? = null,
    val groundFront: Int? = null,
    val groundBack: Int? = null,
    val airFront: Int? = null,
    val airBack: Int? = null,
    val headPosX: Int? = null,
    val headPosY: Int? = null,
    val midPosX: Int? = null,
    val midPosY: Int? = null,
    val standSizebox: String? = null,
    val airSizebox: String? = null
) {
    val heightLabel: String
        get() = if (height != null) "$height (ارتفاع التصادم الداخلي [size.height])" else "غير محدد (افتراضي)"
}

/**
 * Key movement and velocity physics related to body scale.
 */
data class ScaleMovementMetrics(
    val walkFwd: Double? = null,
    val walkBack: Double? = null,
    val runFwdX: Double? = null,
    val runFwdY: Double? = null,
    val jumpNeuY: Double? = null,
    val yAccel: Double? = null
)

/**
 * Visual scale factors, portrait scale, and declared screen coordinates.
 */
data class VisualScaleMetrics(
    val xScale: Double = 1.0,
    val yScale: Double = 1.0,
    val aspectRatio: Double = 1.0,
    val declaredLocalcoord: String? = null,
    val localcoordWidth: Int? = null,
    val localcoordHeight: Int? = null,
    val declaredPortraitScale: Double? = null
)

/**
 * In-memory physical and visual scale profile of a fighter.
 */
data class CharacterScaleProfile(
    val characterId: String,
    val displayName: String,
    val visualScale: VisualScaleMetrics,
    val physicalDimensions: PhysicalDimensions,
    val movementMetrics: ScaleMovementMetrics,
    val airFilePath: String? = null,
    val hasAirFile: Boolean = false,
    val hasClsnBoxes: Boolean = true,
    val archetype: ScaleArchetype = ScaleArchetype.STANDARD,
    val detectedAnomalies: List<ScaleAnomalyType> = emptyList(),
    val rawWarning: String? = null
)

/**
 * Balanced proposal for character physical scale and pushbox calibration.
 */
data class CharacterScaleProposal(
    val characterId: String,
    val displayName: String,
    val originalXScale: Double,
    val proposedXScale: Double,
    val originalYScale: Double,
    val proposedYScale: Double,
    val originalHeight: Int?,
    val proposedHeight: Int?,
    val originalGroundFront: Int?,
    val proposedGroundFront: Int?,
    val originalGroundBack: Int?,
    val proposedGroundBack: Int?,
    val declaredLocalcoord: String?,
    val proposedLocalcoord: String?,
    val declaredPortraitScale: Double? = null,
    val proposedPortraitScale: Double? = null,
    val archetype: ScaleArchetype,
    val anomalies: List<ScaleAnomalyType>,
    val hasScaleAdjustment: Boolean,
    val arabicRationale: String,
    val isAiGenerated: Boolean = false,
    val collisionHeightNote: String = "ارتفاع التصادم الداخلي [size.height] ولا يمثل الارتفاع المرئي للمجسم"
)

/**
 * Configuration and thresholds for physical scale balancing.
 */
data class ScaleBalancingConfig(
    val standardScaleMin: Double = 0.85,
    val standardScaleMax: Double = 1.15,
    val absoluteFloorScale: Double = 0.35,
    val absoluteCapScale: Double = 2.50,
    val maxAllowedAspectRatioDeviation: Double = 0.05,
    val bossScaleCap: Double = 1.65,
    val standardHeightMin: Int = 50,
    val standardHeightMax: Int = 110,
    val dampingDivisor: Double = 2.2,
    val autoHarmonizeRosterTarget: Double = 1.0
)

/**
 * Pure, standalone engine for analyzing fighter physical scale, visual proportions,
 * pushbox boundaries, coordinate compatibility, and portrait scaling in MUGEN/IKEMEN-GO.
 *
 * Guarantees:
 * 1. Zero disk writes during analysis (strictly in-memory).
 * 2. Deterministic: identical inputs produce identical profiles and proposals.
 * 3. Proportional integrity: maintains horizontal/vertical aspect parity (xscale == yscale).
 * 4. Archetype preservation: does NOT force uniform height on all fighters; preserves boss presence.
 * 5. Strict separation between body scale (xscale/yscale in .cns) and portrait scaling (fight.def / portraitscale in .def).
 * 6. Never conflates internal collision height ([size] height) with visual sprite dimensions.
 */
object CharacterScaleAnalyzer {

    val DEFAULT_CONFIG = ScaleBalancingConfig()

    private val BOSS_KEYWORDS = listOf(
        "boss", "apocalypse", "onslaught", "sentinel", "galactus",
        "juggernaut", "thanos", "abyss", "giant", "titan", "master hand", "valkyrie"
    )

    private val TINY_KEYWORDS = listOf(
        "servbot", "chopper", "pichu", "roll", "mini", "tiny", "small", "chibi", "badtsu"
    )

    /**
     * Extracts a complete CharacterScaleProfile from memory-parsed CnsEditor and optional DefEditor.
     */
    fun extractScaleProfile(
        cnsEditor: CnsEditor,
        defEditor: DefEditor?,
        characterId: String,
        displayName: String
    ): CharacterScaleProfile {
        // 1. Visual scale - default safely to 1.0 if not specified in .cns
        val rawXScale = cnsEditor.getProperty("[size]", "xscale")
        val rawYScale = cnsEditor.getProperty("[size]", "yscale")
        val xScale = rawXScale?.toDoubleOrNull() ?: 1.0
        val yScale = rawYScale?.toDoubleOrNull() ?: 1.0
        val aspect = if (yScale > 0.0) xScale / yScale else 1.0

        // Localcoord & Portraitscale from .def if available
        var declaredLocalcoord: String? = null
        var localWidth: Int? = null
        var localHeight: Int? = null
        var declaredPortraitScale: Double? = null

        if (defEditor != null) {
            val lc = defEditor.getProperty("info", "localcoord")
                ?: defEditor.getProperty("", "localcoord")
                ?: defEditor.properties.values.firstOrNull { it.containsKey("localcoord") }?.get("localcoord")?.value

            if (lc != null && lc.isNotBlank()) {
                declaredLocalcoord = lc.trim()
                val parts = declaredLocalcoord.split(",").map { it.trim() }
                if (parts.size >= 2) {
                    localWidth = parts[0].toIntOrNull()
                    localHeight = parts[1].toIntOrNull()
                }
            }

            val ps = defEditor.getProperty("info", "portraitscale")
                ?: defEditor.getProperty("", "portraitscale")
            declaredPortraitScale = ps?.trim()?.toDoubleOrNull()
        }

        val visualScale = VisualScaleMetrics(
            xScale = xScale,
            yScale = yScale,
            aspectRatio = aspect,
            declaredLocalcoord = declaredLocalcoord,
            localcoordWidth = localWidth,
            localcoordHeight = localHeight,
            declaredPortraitScale = declaredPortraitScale
        )

        // 2. Physical dimensions
        fun parsePair(raw: String?): Pair<Int?, Int?> {
            if (raw.isNullOrBlank()) return Pair(null, null)
            val parts = raw.split(",").map { it.trim() }
            val x = parts.getOrNull(0)?.toIntOrNull()
            val y = parts.getOrNull(1)?.toIntOrNull()
            return Pair(x, y)
        }

        val height = cnsEditor.getProperty("[size]", "height")?.toIntOrNull()
        val gFront = cnsEditor.getProperty("[size]", "ground.front")?.toIntOrNull()
        val gBack = cnsEditor.getProperty("[size]", "ground.back")?.toIntOrNull()
        val aFront = cnsEditor.getProperty("[size]", "air.front")?.toIntOrNull()
        val aBack = cnsEditor.getProperty("[size]", "air.back")?.toIntOrNull()

        val headPos = parsePair(cnsEditor.getProperty("[size]", "head.pos"))
        val midPos = parsePair(cnsEditor.getProperty("[size]", "mid.pos"))
        val standSizebox = cnsEditor.getProperty("[size]", "stand.sizebox")
        val airSizebox = cnsEditor.getProperty("[size]", "air.sizebox")

        val dimensions = PhysicalDimensions(
            height = height,
            groundFront = gFront,
            groundBack = gBack,
            airFront = aFront,
            airBack = aBack,
            headPosX = headPos.first,
            headPosY = headPos.second,
            midPosX = midPos.first,
            midPosY = midPos.second,
            standSizebox = standSizebox,
            airSizebox = airSizebox
        )

        // 3. Movement and physics
        val walkFwd = cnsEditor.getProperty("[velocity]", "walk.fwd")?.toDoubleOrNull()
        val walkBack = cnsEditor.getProperty("[velocity]", "walk.back")?.toDoubleOrNull()

        fun parseDoublePair(raw: String?): Pair<Double?, Double?> {
            if (raw.isNullOrBlank()) return Pair(null, null)
            val parts = raw.split(",").map { it.trim() }
            val x = parts.getOrNull(0)?.toDoubleOrNull()
            val y = parts.getOrNull(1)?.toDoubleOrNull()
            return Pair(x, y)
        }

        val runFwd = parseDoublePair(cnsEditor.getProperty("[velocity]", "run.fwd"))
        val jumpNeu = parseDoublePair(cnsEditor.getProperty("[velocity]", "jump.neu"))
        val yAccel = cnsEditor.getProperty("[movement]", "yaccel")?.toDoubleOrNull()

        val movement = ScaleMovementMetrics(
            walkFwd = walkFwd,
            walkBack = walkBack,
            runFwdX = runFwd.first,
            runFwdY = runFwd.second,
            jumpNeuY = jumpNeu.second,
            yAccel = yAccel
        )

        // 4. AIR and collision links
        var airFilePath: String? = null
        var hasAir = false
        var hasClsn = true

        if (defEditor != null) {
            val depFiles = defEditor.getDependentFiles()
            val animFile = depFiles.firstOrNull { it.key.equals("anim", ignoreCase = true) }
            if (animFile != null) {
                airFilePath = animFile.declaredPath
                hasAir = animFile.exists
                if (hasAir && animFile.resolvedFile != null) {
                    hasClsn = checkAirFileCollisionBoxes(animFile.resolvedFile)
                }
            }
        }

        // 5. Archetype Classification
        val archetype = classifyArchetype(characterId, displayName, visualScale, dimensions)

        // 6. Anomaly Detection
        val anomalies = detectAnomalies(visualScale, dimensions, movement, archetype)

        return CharacterScaleProfile(
            characterId = characterId,
            displayName = displayName,
            visualScale = visualScale,
            physicalDimensions = dimensions,
            movementMetrics = movement,
            airFilePath = airFilePath,
            hasAirFile = hasAir,
            hasClsnBoxes = hasClsn,
            archetype = archetype,
            detectedAnomalies = anomalies,
            rawWarning = null
        )
    }

    /**
     * Checks if .air file contains collision box declarations (Clsn1 or Clsn2) safely in-memory.
     */
    private fun checkAirFileCollisionBoxes(airFile: File): Boolean {
        return try {
            if (!airFile.exists() || airFile.length() == 0L) return false
            var foundClsn = false
            airFile.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim().lowercase()
                    if (trimmed.startsWith("clsn1") || trimmed.startsWith("clsn2") || trimmed.startsWith("clsn")) {
                        foundClsn = true
                        break
                    }
                }
            }
            foundClsn
        } catch (_: Exception) {
            true // default to true on read error to prevent false negative panic
        }
    }

    /**
     * Classifies character archetype based on identity keywords and structural metrics.
     *
     * CRITICAL FIX: In MUGEN / IKEMEN-GO, `height` in [size] is the collision/throw height,
     * not visual sprite height. Hundreds of standard adult characters have height = 40 or 34.
     * Therefore, characters are NEVER classified as TINY_SMALL based on height alone.
     */
    fun classifyArchetype(
        characterId: String,
        displayName: String,
        visual: VisualScaleMetrics,
        dim: PhysicalDimensions
    ): ScaleArchetype {
        val lowerId = characterId.lowercase()
        val lowerName = displayName.lowercase()

        val specificBosses = listOf("apocalypse", "onslaught", "sentinel", "galactus", "juggernaut", "thanos", "abyss", "master hand")
        val specificTiny = listOf("servbot", "chopper", "pichu", "chibi", "badtsu")

        if (specificBosses.any { lowerId.contains(it) || lowerName.contains(it) }) {
            return ScaleArchetype.GIANT_BOSS
        }
        if (specificTiny.any { lowerId.contains(it) || lowerName.contains(it) }) {
            return ScaleArchetype.TINY_SMALL
        }

        val words = (lowerId.split('_', '-', ' ', '.') + lowerName.split('_', '-', ' ', '.')).filter { it.isNotBlank() }
        val bossTokens = listOf("boss", "giant", "titan")
        if (bossTokens.any { it in words }) {
            return ScaleArchetype.GIANT_BOSS
        }

        val tinyTokens = listOf("tiny", "mini", "small")
        if (tinyTokens.any { it in words }) {
            return ScaleArchetype.TINY_SMALL
        }

        // Only classify as TINY_SMALL if visual scale is genuinely diminutive (both <= 0.65)
        if (visual.xScale <= 0.65 && visual.yScale <= 0.65) {
            return ScaleArchetype.TINY_SMALL
        }

        // Check boss or tall cues
        val height = dim.height
        return when {
            height != null && height >= 140 -> ScaleArchetype.GIANT_BOSS
            height != null && height >= 115 -> ScaleArchetype.TALL_LARGE
            else -> ScaleArchetype.STANDARD
        }
    }

    /**
     * Detects visual, physical, and movement anomalies based on archetype expectations.
     */
    fun detectAnomalies(
        visual: VisualScaleMetrics,
        dim: PhysicalDimensions,
        movement: ScaleMovementMetrics,
        archetype: ScaleArchetype,
        config: ScaleBalancingConfig = DEFAULT_CONFIG
    ): List<ScaleAnomalyType> {
        val anomalies = mutableListOf<ScaleAnomalyType>()

        // 1. Aspect ratio distortion
        val aspectDiff = abs(visual.xScale - visual.yScale)
        if (aspectDiff > config.maxAllowedAspectRatioDeviation) {
            anomalies.add(ScaleAnomalyType.ASPECT_RATIO_DISTORTION)
        }

        // 2. Oversized sprite
        when (archetype) {
            ScaleArchetype.STANDARD -> {
                if (visual.xScale > config.standardScaleMax || visual.yScale > config.standardScaleMax) {
                    anomalies.add(ScaleAnomalyType.OVERSIZED_SPRITE)
                }
            }
            ScaleArchetype.TINY_SMALL -> {
                if (visual.xScale > 0.85 || visual.yScale > 0.85) {
                    anomalies.add(ScaleAnomalyType.OVERSIZED_SPRITE)
                }
            }
            ScaleArchetype.TALL_LARGE -> {
                if (visual.xScale > 1.45 || visual.yScale > 1.45) {
                    anomalies.add(ScaleAnomalyType.OVERSIZED_SPRITE)
                }
            }
            ScaleArchetype.GIANT_BOSS -> {
                if (visual.xScale > config.bossScaleCap || visual.yScale > config.bossScaleCap) {
                    anomalies.add(ScaleAnomalyType.OVERSIZED_SPRITE)
                }
            }
            ScaleArchetype.SPECIAL_UNCLASSIFIED -> {
                if (visual.xScale > 1.80 || visual.yScale > 1.80) {
                    anomalies.add(ScaleAnomalyType.OVERSIZED_SPRITE)
                }
            }
        }

        // 3. Undersized sprite
        when (archetype) {
            ScaleArchetype.STANDARD, ScaleArchetype.TALL_LARGE, ScaleArchetype.GIANT_BOSS -> {
                if (visual.xScale < config.standardScaleMin || visual.yScale < config.standardScaleMin) {
                    anomalies.add(ScaleAnomalyType.UNDERSIZED_SPRITE)
                }
            }
            ScaleArchetype.TINY_SMALL -> {
                if (visual.xScale < 0.40 || visual.yScale < 0.40) {
                    anomalies.add(ScaleAnomalyType.UNDERSIZED_SPRITE)
                }
            }
            else -> {}
        }

        // 4. Roster Scale Deviation (for standard fighters deviating from target 1.0x by more than 8%)
        if (archetype == ScaleArchetype.STANDARD) {
            val avgScale = (visual.xScale + visual.yScale) / 2.0
            if (abs(avgScale - config.autoHarmonizeRosterTarget) >= 0.08 &&
                !anomalies.contains(ScaleAnomalyType.OVERSIZED_SPRITE) &&
                !anomalies.contains(ScaleAnomalyType.UNDERSIZED_SPRITE)
            ) {
                anomalies.add(ScaleAnomalyType.ROSTER_SCALE_DEVIATION)
            }
        }

        // 5. Pushbox scale mismatch
        val h = dim.height
        val avgScale = (visual.xScale + visual.yScale) / 2.0
        if (h != null) {
            if (avgScale > 1.30 && h < 45) {
                anomalies.add(ScaleAnomalyType.PUSHBOX_SCALE_MISMATCH)
            } else if (avgScale < 0.70 && h > 110) {
                anomalies.add(ScaleAnomalyType.PUSHBOX_SCALE_MISMATCH)
            }
        }

        val gf = dim.groundFront ?: 0
        val gb = dim.groundBack ?: 0
        if ((gf + gb) > 65 && avgScale < 0.75) {
            if (!anomalies.contains(ScaleAnomalyType.PUSHBOX_SCALE_MISMATCH)) {
                anomalies.add(ScaleAnomalyType.PUSHBOX_SCALE_MISMATCH)
            }
        }

        // 6. Localcoord mismatch
        val lw = visual.localcoordWidth
        val lh = visual.localcoordHeight
        if (lw != null && lh != null) {
            if (lw >= 640 && avgScale > 1.2) {
                anomalies.add(ScaleAnomalyType.MISSING_OR_MISMATCHED_LOCALCOORD)
            }
        }

        // 7. Speed scale imbalance
        val walk = movement.walkFwd
        if (walk != null) {
            if (avgScale > 1.4 && walk > 4.5) {
                anomalies.add(ScaleAnomalyType.SPEED_SCALE_IMBALANCE)
            } else if (avgScale < 0.7 && walk < 1.0) {
                anomalies.add(ScaleAnomalyType.SPEED_SCALE_IMBALANCE)
            }
        }

        return anomalies
    }

    /**
     * Generates a balanced, identity-preserving CharacterScaleProposal.
     */
    fun generateScaleProposal(
        profile: CharacterScaleProfile,
        config: ScaleBalancingConfig = DEFAULT_CONFIG
    ): CharacterScaleProposal {
        val origX = profile.visualScale.xScale
        val origY = profile.visualScale.yScale
        val anomalies = profile.detectedAnomalies

        if (anomalies.isEmpty()) {
            return CharacterScaleProposal(
                characterId = profile.characterId,
                displayName = profile.displayName,
                originalXScale = origX,
                proposedXScale = origX,
                originalYScale = origY,
                proposedYScale = origY,
                originalHeight = profile.physicalDimensions.height,
                proposedHeight = profile.physicalDimensions.height,
                originalGroundFront = profile.physicalDimensions.groundFront,
                proposedGroundFront = profile.physicalDimensions.groundFront,
                originalGroundBack = profile.physicalDimensions.groundBack,
                proposedGroundBack = profile.physicalDimensions.groundBack,
                declaredLocalcoord = profile.visualScale.declaredLocalcoord,
                proposedLocalcoord = profile.visualScale.declaredLocalcoord,
                declaredPortraitScale = profile.visualScale.declaredPortraitScale,
                proposedPortraitScale = profile.visualScale.declaredPortraitScale,
                archetype = profile.archetype,
                anomalies = emptyList(),
                hasScaleAdjustment = false,
                arabicRationale = "المقياس البصري والأبعاد الفيزيائية متناسقة ومتوافقة تماماً مع معايير القتال."
            )
        }

        // Compute harmonized target scale keeping aspect ratio 1:1
        var targetScale: Double

        when (profile.archetype) {
            ScaleArchetype.STANDARD -> {
                val avg = (origX + origY) / 2.0
                targetScale = when {
                    avg > config.standardScaleMax -> {
                        // Damped reduction towards standard target 1.0
                        1.0 + (avg - 1.0) / config.dampingDivisor
                    }
                    avg < config.standardScaleMin -> {
                        1.0 - (1.0 - avg) / config.dampingDivisor
                    }
                    // For roster deviation within standard range (e.g. 0.85x or 0.9x like Bayonetta/Cid)
                    abs(avg - config.autoHarmonizeRosterTarget) >= 0.08 -> {
                        config.autoHarmonizeRosterTarget
                    }
                    else -> avg
                }
                targetScale = targetScale.coerceIn(config.standardScaleMin, config.standardScaleMax)
            }
            ScaleArchetype.TINY_SMALL -> {
                val avg = (origX + origY) / 2.0
                targetScale = when {
                    avg > 0.85 -> 0.75 + (avg - 0.75) / config.dampingDivisor
                    avg < 0.50 -> 0.55
                    else -> avg
                }.coerceIn(0.50, 0.85)
            }
            ScaleArchetype.TALL_LARGE -> {
                val avg = (origX + origY) / 2.0
                targetScale = when {
                    avg > 1.40 -> 1.25 + (avg - 1.25) / config.dampingDivisor
                    avg < 1.15 -> 1.20
                    else -> avg
                }.coerceIn(1.15, 1.40)
            }
            ScaleArchetype.GIANT_BOSS -> {
                val avg = (origX + origY) / 2.0
                targetScale = if (avg > config.bossScaleCap) {
                    config.bossScaleCap + (avg - config.bossScaleCap) / 3.0
                } else {
                    avg
                }.coerceIn(1.30, config.bossScaleCap)
            }
            ScaleArchetype.SPECIAL_UNCLASSIFIED -> {
                val avg = (origX + origY) / 2.0
                targetScale = avg.coerceIn(config.absoluteFloorScale, config.absoluteCapScale)
            }
        }

        // Round to 3 decimal places for clean floating point representation
        targetScale = (targetScale * 1000.0).roundToInt() / 1000.0

        val hasScaleDiff = abs(targetScale - origX) > 0.005 || abs(targetScale - origY) > 0.005
        val scaleMultiplier = if (origY > 0.0) targetScale / origY else 1.0

        // Proportional physical dimensions adjustment to prevent ghost pushboxes
        val origHeight = profile.physicalDimensions.height
        val propHeight = if (hasScaleDiff && origHeight != null && origHeight > 0) {
            (origHeight * scaleMultiplier).roundToInt().coerceIn(20, 200)
        } else origHeight

        val origGFront = profile.physicalDimensions.groundFront
        val propGFront = if (hasScaleDiff && origGFront != null && origGFront > 0) {
            (origGFront * scaleMultiplier).roundToInt().coerceIn(5, 80)
        } else origGFront

        val origGBack = profile.physicalDimensions.groundBack
        val propGBack = if (hasScaleDiff && origGBack != null && origGBack > 0) {
            (origGBack * scaleMultiplier).roundToInt().coerceIn(5, 80)
        } else origGBack

        // Formulate Arabic rationale
        val rationale = buildArabicRationale(profile, origX, origY, targetScale, propHeight, anomalies)

        return CharacterScaleProposal(
            characterId = profile.characterId,
            displayName = profile.displayName,
            originalXScale = origX,
            proposedXScale = targetScale,
            originalYScale = origY,
            proposedYScale = targetScale,
            originalHeight = origHeight,
            proposedHeight = propHeight,
            originalGroundFront = origGFront,
            proposedGroundFront = propGFront,
            originalGroundBack = origGBack,
            proposedGroundBack = propGBack,
            declaredLocalcoord = profile.visualScale.declaredLocalcoord,
            proposedLocalcoord = profile.visualScale.declaredLocalcoord,
            declaredPortraitScale = profile.visualScale.declaredPortraitScale,
            proposedPortraitScale = profile.visualScale.declaredPortraitScale,
            archetype = profile.archetype,
            anomalies = anomalies,
            hasScaleAdjustment = hasScaleDiff,
            arabicRationale = rationale,
            isAiGenerated = false
        )
    }

    private fun buildArabicRationale(
        profile: CharacterScaleProfile,
        origX: Double,
        origY: Double,
        targetScale: Double,
        propHeight: Int?,
        anomalies: List<ScaleAnomalyType>
    ): String {
        val parts = mutableListOf<String>()

        if (anomalies.contains(ScaleAnomalyType.ASPECT_RATIO_DISTORTION)) {
            parts.add("إصلاح تشوه نسبة الأبعاد بتوحيد مقياسي العرض والارتفاع إلى $targetScale لمنع تمدد أو انضغاط المجسم.")
        }

        if (anomalies.contains(ScaleAnomalyType.OVERSIZED_SPRITE)) {
            parts.add("معايرة وتصغير الحجم من ($origX, $origY) إلى $targetScale ليتوافق مع ساحة القتال وباقي الخصوم دون مظهر مفرط.")
        } else if (anomalies.contains(ScaleAnomalyType.UNDERSIZED_SPRITE)) {
            parts.add("تكبير الحجم من ($origX, $origY) إلى $targetScale ليتناسب مع الحجم التنافسي المعتاد وتمكين إصابة الضربات.")
        } else if (anomalies.contains(ScaleAnomalyType.ROSTER_SCALE_DEVIATION)) {
            parts.add("معايرة المقياس من (${origX}x) إلى (${targetScale}x) لمطابقة المعيار التنافسي المتوازن لباقي المقاتلين القياسيين.")
        }

        if (anomalies.contains(ScaleAnomalyType.PUSHBOX_SCALE_MISMATCH) && propHeight != null) {
            parts.add("مواءمة مربع الاصطدام والارتفاع الفيزيائي إلى ${propHeight} لمنع الضربات الوهمية خارج نطاق الجسم.")
        }

        if (parts.isEmpty()) {
            return "معايرة وقائية لمقياس الشخصية إلى $targetScale لتحقيق التناغم التام مع معايير القائمة."
        }

        return parts.joinToString(" ")
    }

    /**
     * High-level entry point to analyze a list of CharacterData and return all scale proposals.
     */
    fun analyzeCharacterDataRoster(
        characters: List<CharacterData>,
        config: ScaleBalancingConfig = DEFAULT_CONFIG
    ): List<CharacterScaleProposal> {
        val proposals = mutableListOf<CharacterScaleProposal>()

        for (char in characters) {
            val cns = char.cnsFile
            if (cns == null || !cns.isFile || !cns.canRead()) {
                continue
            }

            try {
                val cnsEditor = CnsEditor(cns)
                val defEditor = char.defFile?.let { if (it.isFile && it.canRead()) DefEditor(it) else null }
                val profile = extractScaleProfile(cnsEditor, defEditor, char.folderName, char.displayName)
                proposals.add(generateScaleProposal(profile, config))
            } catch (_: Exception) {
                // Ignore individual unreadable character gracefully
            }
        }

        return proposals
    }
}
