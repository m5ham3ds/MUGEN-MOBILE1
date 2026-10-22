package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.*
import com.example.engine.gemini.*
import com.example.engine.scale.*
import com.example.engine.workspace.CharacterWorkspaceManager
import com.example.engine.workspace.CharacterWorkspaceStatus
import com.example.storage.MugenMobileStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Mandatory Unit Test Suite for PHASE 4.3.1:
 * Physical Scale Apply Pipeline & Character Portrait Scaling Repair.
 *
 * Covers 10 Mandatory Scenarios:
 * 1. Direct application of visual body scale (yscale & xscale) to .cns file and verification.
 * 2. Independent horizontal vs vertical scale support (xscale != yscale) without force-squashing.
 * 3. Missing scale properties default safely to 1.0 without crash or unhandled nulls.
 * 4. Extraction and preservation of localcoord (e.g. 426, 240) in .def file info section.
 * 5. Engine reload and cache freshness verification (fresh scan gets updated values).
 * 6. Strict separation of body scale ([size] xscale/yscale) from lifebar portrait scaling.
 * 7. Lifebar portrait clipping window enforcement (fight.def [Face] window preventing HUD blowout).
 * 8. Vault SHA-256 immutability — original vaults and pristine hashes remain untouched during scale balance.
 * 9. Bayonetta classification & mathematical engine vs UI badge statistics parity.
 * 10. Gemini AI integration proposing valid clamped physical scales without breaking schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhysicalScaleAndPortraitRepairTest {

    private lateinit var context: Context
    private lateinit var tempGameDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempGameDir = File(context.cacheDir, "test_scale_repair_${System.currentTimeMillis()}")
        tempGameDir.mkdirs()

        MugenMobileStorage.setCustomBaseDir(context, tempGameDir.absolutePath)

        val vaultDir = CharacterWorkspaceManager.getVaultDir(context)
        if (vaultDir.exists()) vaultDir.deleteRecursively()
        val metaDir = CharacterWorkspaceManager.getMetadataDir(context)
        if (metaDir.exists()) metaDir.deleteRecursively()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun createCharacterFolder(
        charName: String,
        cnsContent: String,
        defContent: String? = null
    ): File {
        val charsRoot = MugenMobileStorage.getCharsDir(context).apply { mkdirs() }
        val charDir = File(charsRoot, charName).apply { mkdirs() }
        val def = defContent ?: """
            [Info]
            name = "$charName"
            displayname = "$charName Fighter"
            
            [Files]
            cns = $charName.cns
            anim = $charName.air
        """.trimIndent()
        File(charDir, "$charName.def").writeText(def)
        File(charDir, "$charName.cns").writeText(cnsContent)
        File(charDir, "$charName.air").writeText("Clsn2: 1\nClsn2[0] = -15, -60, 15, 0\n")
        return charDir
    }

    /**
     * Scenario 1: Direct application of visual body scale (yscale & xscale) to .cns file and verification.
     */
    @Test
    fun test01_DirectApplicationOfVisualBodyScaleToCns() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 0.9
            yscale = 0.9
            height = 40
        """.trimIndent()

        val charDir = createCharacterFolder("bayonetta", cns)
        val cnsFile = File(charDir, "bayonetta.cns")
        val defFile = File(charDir, "bayonetta.def")

        // Initialize snapshot so workspace is valid
        CharacterWorkspaceManager.ensureVaultSnapshot(charDir, context)

        val cnsEditor = CnsEditor(cnsFile)
        val defEditor = DefEditor(defFile)

        // Apply scale changes: 0.9 -> 1.0
        cnsEditor.setProperty("[size]", "xscale", "1.0")
        cnsEditor.setProperty("[size]", "yscale", "1.0")
        cnsEditor.setProperty("[size]", "height", "44")

        val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "bayonetta",
            defEditor = defEditor,
            cnsEditor = cnsEditor,
            context = context
        )

        assertTrue("Save must succeed", saveRes.success)

        // Read directly from disk to verify actual write
        val rawDiskContent = cnsFile.readText()
        assertTrue("Disk file must contain xscale = 1.0", rawDiskContent.contains("xscale = 1.0"))
        assertTrue("Disk file must contain yscale = 1.0", rawDiskContent.contains("yscale = 1.0"))
        assertTrue("Disk file must contain height = 44", rawDiskContent.contains("height = 44"))

        // Verify with fresh CnsEditor instance
        val freshEditor = CnsEditor(cnsFile)
        assertEquals("1.0", freshEditor.getProperty("[size]", "xscale"))
        assertEquals("1.0", freshEditor.getProperty("[size]", "yscale"))
        assertEquals("44", freshEditor.getProperty("[size]", "height"))
    }

    /**
     * Scenario 2: Independent horizontal vs vertical scale support (xscale != yscale) without force-squashing.
     */
    @Test
    fun test02_IndependentHorizontalVsVerticalScaleSupport() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 1.02
            yscale = 1.0
            height = 60
        """.trimIndent()

        val charDir = createCharacterFolder("independent_scale", cns)
        val cnsFile = File(charDir, "independent_scale.cns")
        val defFile = File(charDir, "independent_scale.def")

        val editor = CnsEditor(cnsFile)
        val defEditor = DefEditor(defFile)

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "independent_scale", "Scale Test")
        assertEquals(1.02, profile.visualScale.xScale, 0.001)
        assertEquals(1.0, profile.visualScale.yScale, 0.001)

        // Since difference is only 0.02 (<= maxAllowedAspectRatioDeviation 0.05), no distortion anomaly
        assertFalse("Minor xscale/yscale difference within 5% should not trigger aspect ratio distortion",
            profile.detectedAnomalies.contains(ScaleAnomalyType.ASPECT_RATIO_DISTORTION))

        // Extreme difference (e.g. 1.20 vs 0.90 = 0.30) should trigger ASPECT_RATIO_DISTORTION
        val extremeCns = """
            [size]
            xscale = 1.20
            yscale = 0.90
        """.trimIndent()
        val extremeDir = createCharacterFolder("extreme_aspect", extremeCns)
        val extremeEditor = CnsEditor(File(extremeDir, "extreme_aspect.cns"))
        val extremeProfile = CharacterScaleAnalyzer.extractScaleProfile(extremeEditor, null, "extreme_aspect", "Aspect")
        assertTrue("Major aspect ratio difference must trigger ASPECT_RATIO_DISTORTION",
            extremeProfile.detectedAnomalies.contains(ScaleAnomalyType.ASPECT_RATIO_DISTORTION))
    }

    /**
     * Scenario 3: Missing scale properties default safely to 1.0 without crash or unhandled nulls.
     */
    @Test
    fun test03_MissingScalePropertiesDefaultSafelyTo1_0() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            ; Missing [size] section entirely
        """.trimIndent()

        val charDir = createCharacterFolder("missing_size", cns)
        val editor = CnsEditor(File(charDir, "missing_size.cns"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, null, "missing_size", "No Size")
        assertEquals("Missing xScale must safely default to 1.0", 1.0, profile.visualScale.xScale, 0.001)
        assertEquals("Missing yScale must safely default to 1.0", 1.0, profile.visualScale.yScale, 0.001)
        assertEquals("Missing aspectRatio must safely default to 1.0", 1.0, profile.visualScale.aspectRatio, 0.001)
        assertNull("Missing height should be null without crashing", profile.physicalDimensions.height)

        val proposal = CharacterScaleAnalyzer.generateScaleProposal(profile)
        assertEquals(1.0, proposal.proposedXScale, 0.001)
        assertEquals(1.0, proposal.proposedYScale, 0.001)
        assertFalse("Default 1.0 should not require adjustment", proposal.hasScaleAdjustment)
    }

    /**
     * Scenario 4: Extraction and preservation of localcoord (e.g. 426, 240) in .def file info section.
     */
    @Test
    fun test04_LocalcoordResolutionAndPreservation() {
        val def = """
            [Info]
            name = "hd_fighter"
            displayname = "HD Fighter"
            localcoord = 426, 240
            
            [Files]
            cns = hd_fighter.cns
        """.trimIndent()

        val cns = """
            [size]
            xscale = 1.0
            yscale = 1.0
        """.trimIndent()

        val charDir = createCharacterFolder("hd_char", cns, def)
        val editor = CnsEditor(File(charDir, "hd_char.cns"))
        val defEditor = DefEditor(File(charDir, "hd_char.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "hd_char", "HD Fighter")
        assertEquals("426, 240", profile.visualScale.declaredLocalcoord)
        assertEquals(426, profile.visualScale.localcoordWidth)
        assertEquals(240, profile.visualScale.localcoordHeight)

        val proposal = CharacterScaleAnalyzer.generateScaleProposal(profile)
        assertEquals("426, 240", proposal.proposedLocalcoord)
    }

    /**
     * Scenario 5: Engine reload and cache freshness verification (fresh scan gets updated values).
     */
    @Test
    fun test05_EngineReloadAndCacheFreshnessVerification() {
        val cns = """
            [data]
            life = 1000
            
            [size]
            xscale = 0.85
            yscale = 0.85
            height = 50
        """.trimIndent()

        val charDir = createCharacterFolder("reload_test", cns)
        CharacterWorkspaceManager.ensureVaultSnapshot(charDir, context)

        // 1. Initial scan
        var chars = MugenParser.getCharacters(context)
        val charBefore = chars.firstOrNull { it.folderName == "reload_test" }
        assertNotNull(charBefore)
        val cnsBefore = CnsEditor(charBefore!!.cnsFile!!)
        assertEquals("0.85", cnsBefore.getProperty("[size]", "xscale"))

        // 2. Apply change in workspace
        cnsBefore.setProperty("[size]", "xscale", "1.0")
        cnsBefore.setProperty("[size]", "yscale", "1.0")
        val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "reload_test",
            defEditor = DefEditor(charBefore.defFile!!),
            cnsEditor = cnsBefore,
            context = context
        )
        assertTrue(saveRes.success)

        // 3. Engine reload simulation: fresh getCharacters call
        chars = MugenParser.getCharacters(context)
        val charAfter = chars.firstOrNull { it.folderName == "reload_test" }
        assertNotNull(charAfter)
        val cnsAfter = CnsEditor(charAfter!!.cnsFile!!)
        assertEquals("Engine reload must read fresh updated xscale on disk", "1.0", cnsAfter.getProperty("[size]", "xscale"))
        assertEquals("Engine reload must read fresh updated yscale on disk", "1.0", cnsAfter.getProperty("[size]", "yscale"))
    }

    /**
     * Scenario 6: Strict separation of body scale ([size] xscale/yscale) from lifebar portrait scaling.
     */
    @Test
    fun test06_StrictSeparationOfBodyScaleFromPortraitScaling() {
        val def = """
            [Info]
            name = "cid_kagenou"
            displayname = "Cid Kagenou"
            portraitscale = 0.5
            
            [Files]
            cns = cid_kagenou.cns
        """.trimIndent()

        val cns = """
            [size]
            xscale = 1.0
            yscale = 1.0
            height = 60
        """.trimIndent()

        val charDir = createCharacterFolder("cid_kagenou", cns, def)
        val editor = CnsEditor(File(charDir, "cid_kagenou.cns"))
        val defEditor = DefEditor(File(charDir, "cid_kagenou.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "cid_kagenou", "Cid Kagenou")

        // Body scale is 1.0
        assertEquals(1.0, profile.visualScale.xScale, 0.001)
        assertEquals(1.0, profile.visualScale.yScale, 0.001)

        // Portrait scale is distinct at 0.5
        assertEquals(0.5, profile.visualScale.declaredPortraitScale!!, 0.001)

        // Modifying body scale to 1.1 must NOT alter portrait scale
        editor.setProperty("[size]", "xscale", "1.1")
        val updatedProfile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "cid_kagenou", "Cid Kagenou")
        assertEquals(1.1, updatedProfile.visualScale.xScale, 0.001)
        assertEquals(0.5, updatedProfile.visualScale.declaredPortraitScale!!, 0.001)
    }

    /**
     * Scenario 7: Lifebar portrait clipping window enforcement (fight.def [Face] window preventing HUD blowout).
     */
    @Test
    fun test07_LifebarPortraitClippingWindowEnforcement() {
        val baseDir = MugenMobileStorage.getBaseDir(context)
        val dataDir = File(baseDir, "data").apply { mkdirs() }
        val fightDefFile = File(dataDir, "fight.def")

        // Unclipped fight.def where high-res portraits bleed across the HUD
        fightDefFile.writeText("""
            [Face]
            p1.pos = 14.5, 20
            p1.face.spr = 9000, 0
            p2.pos = 1262, 20
            p2.face.spr = 9000, 0
            p2.face.facing = -1
        """.trimIndent())

        val configBefore = FightDefManager.inspectFightDef(fightDefFile)
        assertTrue(configBefore.hasFaceSection)
        assertFalse("Must detect missing safe clipping windows initially", configBefore.hasSafeClipping)

        // Enforce safe clipping
        val enforced = FightDefManager.ensureSafePortraitClipping(context)
        assertTrue("Enforcement must return true", enforced)

        // Verify fight.def now contains window boundaries
        val configAfter = FightDefManager.inspectFightDef(fightDefFile)
        assertTrue("Must now have safe clipping enabled", configAfter.hasSafeClipping)
        assertEquals(FightDefManager.DEFAULT_1280_P1_WINDOW, configAfter.p1Window)
        assertEquals(FightDefManager.DEFAULT_1280_P2_WINDOW, configAfter.p2Window)

        // Verify backup file was created
        val bakFile = File(dataDir, "fight.def.bak")
        assertTrue("Backup file .fight.def.bak must exist", bakFile.exists())

        // Idempotency: calling again must return true and preserve existing safe config
        val secondEnforce = FightDefManager.ensureSafePortraitClipping(context)
        assertTrue(secondEnforce)
        val configSecond = FightDefManager.inspectFightDef(fightDefFile)
        assertEquals(FightDefManager.DEFAULT_1280_P1_WINDOW, configSecond.p1Window)
    }

    /**
     * Scenario 8: Vault SHA-256 immutability — original vaults and pristine hashes remain untouched during scale balance.
     */
    @Test
    fun test08_VaultSha256ImmutabilityDuringScaleApplication() {
        val cns = """
            [data]
            life = 1000
            
            [size]
            xscale = 0.9
            yscale = 0.9
            height = 40
        """.trimIndent()

        val charDir = createCharacterFolder("vault_test_fighter", cns)
        assertTrue("Vault snapshot must be created successfully", CharacterWorkspaceManager.ensureVaultSnapshot(charDir, context))

        val vaultDir = File(CharacterWorkspaceManager.getVaultDir(context), "vault_test_fighter")
        val vaultCnsFile = File(File(vaultDir, "files"), "vault_test_fighter.cns")
        assertTrue("Vault cns file must exist in files directory", vaultCnsFile.exists())
        val vaultShaBefore = sha256(vaultCnsFile)

        // Modify workspace character
        val workspaceCns = File(charDir, "vault_test_fighter.cns")
        val editor = CnsEditor(workspaceCns)
        editor.setProperty("[size]", "xscale", "1.0")
        editor.setProperty("[size]", "yscale", "1.0")
        editor.setProperty("[size]", "height", "44")

        val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "vault_test_fighter",
            defEditor = DefEditor(File(charDir, "vault_test_fighter.def")),
            cnsEditor = editor,
            context = context
        )
        assertTrue(saveRes.success)

        // Verify workspace changed
        val workspaceShaAfter = sha256(workspaceCns)
        assertNotEquals("Workspace CNS hash must change after edit", vaultShaBefore, workspaceShaAfter)

        // Verify Vault snapshot remains completely untouched (SHA-256 invariant)
        val vaultShaAfter = sha256(vaultCnsFile)
        assertEquals("Vault snapshot SHA-256 MUST be 100% immutable", vaultShaBefore, vaultShaAfter)
        val (isVaultHealthy, _) = CharacterWorkspaceManager.verifyVaultIntegrity("vault_test_fighter", context)
        assertTrue("Vault integrity must remain healthy", isVaultHealthy)
    }

    /**
     * Scenario 9: Bayonetta classification & mathematical engine vs UI badge statistics parity.
     */
    @Test
    fun test09_BayonettaClassificationAndSummaryStatisticsParity() {
        val bayonettaInput = CharacterStatInput(
            characterId = "bayonetta",
            displayName = "Bayonetta",
            life = 1000,
            attack = 100,
            defence = 100,
            xScale = 0.9,
            yScale = 0.9,
            height = 40 // collision height <= 45
        )

        val summary = RosterBalancingEngine.analyzeRoster(listOf(bayonettaInput))
        val proposal = summary.proposals.first()

        // 1. Archetype classification must NOT be TINY_SMALL based on height alone
        val scaleProp = proposal.scaleProposal
        assertNotNull(scaleProp)
        assertEquals("Bayonetta must be classified as STANDARD", ScaleArchetype.STANDARD, scaleProp!!.archetype)

        // 2. Scale 0.9x deviating from target 1.0x by 10% must be detected as ROSTER_SCALE_DEVIATION
        assertTrue("Must detect ROSTER_SCALE_DEVIATION", scaleProp.anomalies.contains(ScaleAnomalyType.ROSTER_SCALE_DEVIATION))
        assertTrue("Must have scale adjustment to 1.0x", scaleProp.hasScaleAdjustment)
        assertEquals(1.0, scaleProp.proposedXScale, 0.001)
        assertEquals(1.0, scaleProp.proposedYScale, 0.001)

        // 3. Summary statistics parity:
        // Life, attack, defence are all 1000, 100, 100 -> combatAdjustmentsCount = 0
        assertEquals("Combat adjustment count must be 0", 0, summary.combatAdjustmentsCount)
        // Physical calibration count must be 1 because scale is calibrated 0.9x -> 1.0x
        assertEquals("Physical calibration count must be 1", 1, summary.physicalCalibrationCount)

        // Proposal hasAdjustments must be true because physical adjustment is needed
        assertTrue("Overall proposal hasAdjustments must be true", proposal.hasAdjustments)
        // balancedCharactersCount = 0, charactersNeedingAdjustmentCount = 1
        assertEquals(0, summary.balancedCharactersCount)
        assertEquals(1, summary.charactersNeedingAdjustmentCount)
    }

    /**
     * Scenario 10: Gemini AI integration proposing valid clamped physical scales without breaking schema.
     */
    @Test
    fun test10_GeminiAiScaleProposalValidationAndClamping() {
        val cidInput = CharacterStatInput(
            characterId = "cid_kagenou",
            displayName = "Cid Kagenou",
            life = 950,
            attack = 105,
            defence = 100,
            xScale = 0.9,
            yScale = 0.9,
            height = 42
        )

        val baselineSummary = RosterBalancingEngine.analyzeRoster(listOf(cidInput))

        // Simulate Gemini proposal with extreme scale to test safety clamping
        val geminiProposals = listOf(
            GeminiCharacterProposal(
                characterId = "cid_kagenou",
                proposedLife = 1000,
                proposedAttack = 100,
                proposedDefence = 100,
                proposedXScale = 4.5, // Extreme out-of-bounds scale
                proposedYScale = 4.5,
                rationale = "Harmonize combat stats",
                scaleRationale = "Clamped to safe boss/standard boundary",
                archetype = "Standard Fighter"
            )
        )

        val mergedSummary = GeminiRosterBalancer.mergeAndValidateGeminiProposals(
            inputs = listOf(cidInput),
            baselineSummary = baselineSummary,
            geminiProposals = geminiProposals
        )

        val mergedProposal = mergedSummary.proposals.first()
        val mergedScale = mergedProposal.scaleProposal
        assertNotNull(mergedScale)

        // Must be clamped to absolute maximum 2.50
        assertTrue("Scale must be clamped <= 2.50", mergedScale!!.proposedXScale <= 2.50)
        assertEquals("Horizontal and vertical scale must match", mergedScale.proposedXScale, mergedScale.proposedYScale, 0.001)
        assertTrue("Proposal must be marked as AI generated", mergedScale.isAiGenerated)
        assertTrue("Arabic rationale must contain [Gemini AI] tag", mergedScale.arabicRationale.contains("[Gemini AI]"))

        // Parity check on summary counters
        assertEquals(1, mergedSummary.combatAdjustmentsCount)
        assertEquals(1, mergedSummary.physicalCalibrationCount)
        assertEquals(1, mergedSummary.charactersNeedingAdjustmentCount)
        assertEquals(0, mergedSummary.balancedCharactersCount)
    }
}
