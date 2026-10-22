package com.example.engine.gemini

import com.example.engine.*
import com.example.engine.scale.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Result of roster balance analysis, containing either AI-enhanced or purely mathematical proposals.
 */
data class AiRosterBalancingResult(
    val summary: RosterBalancingSummary,
    val isAiPowered: Boolean,
    val aiModelName: String?,
    val aiErrorMessage: String? = null
)

/**
 * Bridges `RosterBalancingEngine` with Google Gemini AI.
 *
 * Enforces Strict Invariants:
 * 1. Zero Trust: Every proposed value from Gemini is strictly clamped and validated against
 *    `BalancingConfig` floor and cap bounds.
 * 2. Fallback Guarantee: If Gemini fails (network, quota, auth, invalid JSON, or missing key),
 *    the system seamlessly falls back to the deterministic `RosterBalancingEngine` calculations.
 * 3. Never writes to disk: analysis is strictly in-memory.
 * 4. Safe explanations: AI rationales are clearly tagged and sanitized.
 */
object GeminiRosterBalancer {

    /**
     * Analyzes roster stats with optional Gemini AI enhancement.
     */
    suspend fun analyze(
        inputs: List<CharacterStatInput>,
        apiKey: String?,
        config: BalancingConfig = RosterBalancingEngine.DEFAULT_CONFIG
    ): AiRosterBalancingResult {
        // Step 1: Compute deterministic baseline
        val baselineSummary = RosterBalancingEngine.analyzeRoster(inputs, config)

        if (apiKey.isNullOrBlank()) {
            return AiRosterBalancingResult(
                summary = baselineSummary,
                isAiPowered = false,
                aiModelName = null,
                aiErrorMessage = null
            )
        }

        // Step 2: Request Gemini AI proposals
        val aiResult = GeminiApiClient.analyzeRosterWithGemini(apiKey, inputs, config)

        return when (aiResult) {
            is GeminiResult.Error -> {
                // Fallback cleanly to baseline summary with error message
                AiRosterBalancingResult(
                    summary = baselineSummary,
                    isAiPowered = false,
                    aiModelName = null,
                    aiErrorMessage = aiResult.message
                )
            }
            is GeminiResult.Success -> {
                val validatedSummary = mergeAndValidateGeminiProposals(
                    inputs = inputs,
                    baselineSummary = baselineSummary,
                    geminiProposals = aiResult.data,
                    config = config
                )
                AiRosterBalancingResult(
                    summary = validatedSummary,
                    isAiPowered = true,
                    aiModelName = aiResult.modelName,
                    aiErrorMessage = null
                )
            }
        }
    }

    /**
     * Merges Gemini proposals with baseline proposals while enforcing strict validation bounds.
     */
    fun mergeAndValidateGeminiProposals(
        inputs: List<CharacterStatInput>,
        baselineSummary: RosterBalancingSummary,
        geminiProposals: List<GeminiCharacterProposal>,
        config: BalancingConfig = RosterBalancingEngine.DEFAULT_CONFIG
    ): RosterBalancingSummary {
        val geminiMap = geminiProposals.associateBy { it.characterId }
        val inputMap = inputs.associateBy { it.characterId }

        val newProposals = mutableListOf<CharacterBalanceProposal>()

        for (baselineProposal in baselineSummary.proposals) {
            val charId = baselineProposal.characterId
            val geminiProp = geminiMap[charId]
            val input = inputMap[charId]

            if (geminiProp == null || input == null) {
                newProposals.add(baselineProposal)
                continue
            }

            // Strictly validate and clamp Life
            val validatedProposedLife = if (input.life != null && geminiProp.proposedLife != null) {
                clampStat(StatType.LIFE, geminiProp.proposedLife, config)
            } else {
                baselineProposal.proposedLife
            }

            // Strictly validate and clamp Attack
            val validatedProposedAttack = if (input.attack != null && geminiProp.proposedAttack != null) {
                clampStat(StatType.ATTACK, geminiProp.proposedAttack, config)
            } else {
                baselineProposal.proposedAttack
            }

            // Strictly validate and clamp Defence
            val validatedProposedDefence = if (input.defence != null && geminiProp.proposedDefence != null) {
                clampStat(StatType.DEFENCE, geminiProp.proposedDefence, config)
            } else {
                baselineProposal.proposedDefence
            }

            // Construct new StatProposal list based on validated values
            val statProposals = mutableListOf<StatProposal>()

            if (input.life != null && validatedProposedLife != null && validatedProposedLife != input.life) {
                val delta = validatedProposedLife - input.life
                val direction = if (delta > 0) AdjustmentDirection.BUFF else AdjustmentDirection.NERF
                val severity = RosterBalancingEngine.classifySeverity(StatType.LIFE, input.life, config)
                statProposals.add(
                    StatProposal(
                        statType = StatType.LIFE,
                        originalValue = input.life,
                        proposedValue = validatedProposedLife,
                        delta = delta,
                        direction = direction,
                        severity = severity,
                        explanation = "[Gemini AI] ${geminiProp.rationale.ifBlank { baselineProposal.overallExplanation }}"
                    )
                )
            }

            if (input.attack != null && validatedProposedAttack != null && validatedProposedAttack != input.attack) {
                val delta = validatedProposedAttack - input.attack
                val direction = if (delta > 0) AdjustmentDirection.BUFF else AdjustmentDirection.NERF
                val severity = RosterBalancingEngine.classifySeverity(StatType.ATTACK, input.attack, config)
                statProposals.add(
                    StatProposal(
                        statType = StatType.ATTACK,
                        originalValue = input.attack,
                        proposedValue = validatedProposedAttack,
                        delta = delta,
                        direction = direction,
                        severity = severity,
                        explanation = "[Gemini AI] ${geminiProp.rationale.ifBlank { baselineProposal.overallExplanation }}"
                    )
                )
            }

            if (input.defence != null && validatedProposedDefence != null && validatedProposedDefence != input.defence) {
                val delta = validatedProposedDefence - input.defence
                val direction = if (delta > 0) AdjustmentDirection.BUFF else AdjustmentDirection.NERF
                val severity = RosterBalancingEngine.classifySeverity(StatType.DEFENCE, input.defence, config)
                statProposals.add(
                    StatProposal(
                        statType = StatType.DEFENCE,
                        originalValue = input.defence,
                        proposedValue = validatedProposedDefence,
                        delta = delta,
                        direction = direction,
                        severity = severity,
                        explanation = "[Gemini AI] ${geminiProp.rationale.ifBlank { baselineProposal.overallExplanation }}"
                    )
                )
            }

            // Validate and merge scale proposal
            val baseScale = baselineProposal.scaleProposal
            val validatedScaleProposal = if (geminiProp.proposedXScale != null || geminiProp.proposedYScale != null) {
                val origX = baseScale?.originalXScale ?: input.xScale ?: 1.0
                val origY = baseScale?.originalYScale ?: input.yScale ?: 1.0
                val rawX = geminiProp.proposedXScale ?: origX
                val rawY = geminiProp.proposedYScale ?: origY
                // Clamping between 0.35 and 2.50
                val clampedX = rawX.coerceIn(0.35, 2.50)
                val clampedY = rawY.coerceIn(0.35, 2.50)
                // Enforce proportional aspect ratio
                val targetScale = if (abs(clampedX - clampedY) > 0.05) {
                    (clampedX + clampedY) / 2.0
                } else clampedX
                val roundedScale = (targetScale * 1000.0).roundToInt() / 1000.0

                val hasDiff = abs(roundedScale - origX) > 0.005 || abs(roundedScale - origY) > 0.005
                val multiplier = if (origY > 0.0) roundedScale / origY else 1.0

                val origH = baseScale?.originalHeight ?: input.height
                val propH = if (hasDiff && origH != null && origH > 0) (origH * multiplier).roundToInt().coerceIn(20, 200) else origH

                val origGF = baseScale?.originalGroundFront ?: input.groundFront
                val propGF = if (hasDiff && origGF != null && origGF > 0) (origGF * multiplier).roundToInt().coerceIn(5, 80) else origGF

                val origGB = baseScale?.originalGroundBack ?: input.groundBack
                val propGB = if (hasDiff && origGB != null && origGB > 0) (origGB * multiplier).roundToInt().coerceIn(5, 80) else origGB

                val scaleExplanation = if (!geminiProp.scaleRationale.isNullOrBlank()) {
                    "[Gemini AI] ${geminiProp.scaleRationale}"
                } else {
                    baseScale?.arabicRationale ?: "تمت معايرة المقياس والأبعاد الفيزيائية بواسطة الذكاء الاصطناعي."
                }

                CharacterScaleProposal(
                    characterId = charId,
                    displayName = baselineProposal.displayName,
                    originalXScale = origX,
                    proposedXScale = roundedScale,
                    originalYScale = origY,
                    proposedYScale = roundedScale,
                    originalHeight = origH,
                    proposedHeight = propH,
                    originalGroundFront = origGF,
                    proposedGroundFront = propGF,
                    originalGroundBack = origGB,
                    proposedGroundBack = propGB,
                    proposedLocalcoord = baseScale?.proposedLocalcoord ?: input.localcoord,
                    archetype = baseScale?.archetype ?: ScaleArchetype.STANDARD,
                    anomalies = baseScale?.anomalies ?: emptyList(),
                    hasScaleAdjustment = hasDiff,
                    arabicRationale = scaleExplanation,
                    isAiGenerated = true
                )
            } else {
                baseScale
            }

            val hasAdjustments = statProposals.isNotEmpty() || (validatedScaleProposal?.hasScaleAdjustment == true)
            val overallSeverity = baselineProposal.overallSeverity
            val overallExplanation = if (geminiProp.rationale.isNotBlank()) {
                val archetypeStr = if (!geminiProp.archetype.isNullOrBlank()) " [فئة المقاتل: ${geminiProp.archetype}]" else ""
                "[Gemini AI]$archetypeStr ${geminiProp.rationale}"
            } else {
                baselineProposal.overallExplanation
            }

            newProposals.add(
                CharacterBalanceProposal(
                    characterId = baselineProposal.characterId,
                    displayName = baselineProposal.displayName,
                    originalLife = baselineProposal.originalLife,
                    proposedLife = validatedProposedLife,
                    originalAttack = baselineProposal.originalAttack,
                    proposedAttack = validatedProposedAttack,
                    originalDefence = baselineProposal.originalDefence,
                    proposedDefence = validatedProposedDefence,
                    statProposals = statProposals,
                    hasAdjustments = hasAdjustments,
                    overallSeverity = overallSeverity,
                    overallExplanation = overallExplanation,
                    scaleProposal = validatedScaleProposal
                )
            )
        }

        val balancedCount = newProposals.count { !it.hasAdjustments }
        val needingAdjustmentCount = newProposals.count { it.hasAdjustments }
        val allScaleProposals = newProposals.mapNotNull { it.scaleProposal }

        return baselineSummary.copy(
            proposals = newProposals,
            balancedCharactersCount = balancedCount,
            charactersNeedingAdjustmentCount = needingAdjustmentCount,
            scaleProposals = allScaleProposals
        )
    }

    /**
     * Strictly clamps any proposed stat to the allowable hard boundaries defined by BalancingConfig.
     */
    fun clampStat(statType: StatType, proposed: Int, config: BalancingConfig): Int {
        return when (statType) {
            StatType.LIFE -> proposed.coerceIn(config.lifeDampingFloor, config.lifeDampingCap)
            StatType.ATTACK -> proposed.coerceIn(config.attackDampingFloor, config.attackDampingCap)
            StatType.DEFENCE -> proposed.coerceIn(config.defenceDampingFloor, config.defenceDampingCap)
        }
    }
}
