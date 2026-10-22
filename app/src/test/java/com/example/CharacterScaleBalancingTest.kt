package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.*
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
import kotlin.math.abs

/**
 * Comprehensive Unit Test Suite for PHASE 4.3 — Character Physical Scale & Compatibility Balancing.
 *
 * Verifies:
 * 1. Standard scale character produces no adjustments and standard archetype.
 * 2. Oversized character is damped towards balance with proportional pushbox adjustment.
 * 3. Asymmetric aspect ratio distortion (xscale != yscale) is harmonized to equal values.
 * 4. Boss and tiny archetypes are correctly classified and preserve intentional identity.
 * 5. Modern and legacy CNS size, velocity, and movement properties are correctly parsed.
 * 6. Missing or empty size properties fallback safely to 1.0 without crashing.
 * 7. Localcoord resolution from .def file and mismatch detection.
 * 8. Pushbox vs visual scale mismatch anomaly detection.
 * 9. Absolute disk immutability during in-memory analysis (SHA-256 invariant).
 * 10. Atomic save, verification, and rollback via CharacterWorkspaceManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CharacterScaleBalancingTest {

    private lateinit var context: Context
    private lateinit var tempGameDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempGameDir = File(context.cacheDir, "test_scale_game_${System.currentTimeMillis()}")
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

    private fun createCharacterFolder(charName: String, cnsContent: String, defContent: String? = null): File {
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

    @Test
    fun test1_StandardScaleCharacter() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 1.0
            yscale = 1.0
            height = 60
            ground.front = 16
            ground.back = 15
        """.trimIndent()

        val charDir = createCharacterFolder("ryu_std", cns)
        val cnsFile = File(charDir, "ryu_std.cns")
        val defFile = File(charDir, "ryu_std.def")

        val editor = CnsEditor(cnsFile)
        val defEditor = DefEditor(defFile)

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "ryu_std", "Ryu")
        assertEquals(ScaleArchetype.STANDARD, profile.archetype)
        assertEquals(1.0, profile.visualScale.xScale, 0.001)
        assertEquals(1.0, profile.visualScale.yScale, 0.001)
        assertEquals(1.0, profile.visualScale.aspectRatio, 0.001)
        assertTrue("Standard character should have no detected anomalies", profile.detectedAnomalies.isEmpty())

        val proposal = CharacterScaleAnalyzer.generateScaleProposal(profile)
        assertFalse("Standard character should need no scale adjustment", proposal.hasScaleAdjustment)
        assertEquals(1.0, proposal.proposedXScale, 0.001)
        assertEquals(1.0, proposal.proposedYScale, 0.001)
        assertEquals(60, proposal.proposedHeight)
    }

    @Test
    fun test2_OversizedCharacterDampingAndProportionalPushbox() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 1.5
            yscale = 1.5
            height = 80
            ground.front = 20
            ground.back = 18
        """.trimIndent()

        val charDir = createCharacterFolder("oversized_fighter", cns)
        val editor = CnsEditor(File(charDir, "oversized_fighter.cns"))
        val defEditor = DefEditor(File(charDir, "oversized_fighter.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "oversized_fighter", "Ken")
        assertEquals(ScaleArchetype.STANDARD, profile.archetype)
        assertTrue("Should detect oversized sprite anomaly", profile.detectedAnomalies.contains(ScaleAnomalyType.OVERSIZED_SPRITE))

        val proposal = CharacterScaleAnalyzer.generateScaleProposal(profile)
        assertTrue("Oversized character must have scale adjustment", proposal.hasScaleAdjustment)
        assertTrue("Proposed scale should be damped below 1.5", proposal.proposedXScale < 1.5)
        assertTrue("Proposed scale should remain >= 1.0", proposal.proposedXScale >= 1.0)
        assertEquals("Horizontal and vertical scale must match", proposal.proposedXScale, proposal.proposedYScale, 0.001)

        // Verify proportional pushbox adjustment
        assertNotNull(proposal.proposedHeight)
        assertTrue("Height should be proportionally reduced", proposal.proposedHeight!! < 80)
        assertTrue("Arabic rationale must mention scale correction", proposal.arabicRationale.contains("معايرة وتصغير الحجم"))
    }

    @Test
    fun test3_AspectRatioDistortionHarmonization() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 1.0
            yscale = 1.35
            height = 70
        """.trimIndent()

        val charDir = createCharacterFolder("distorted_char", cns)
        val editor = CnsEditor(File(charDir, "distorted_char.cns"))
        val defEditor = DefEditor(File(charDir, "distorted_char.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "distorted_char", "Chun-Li")
        assertTrue("Should detect aspect ratio distortion", profile.detectedAnomalies.contains(ScaleAnomalyType.ASPECT_RATIO_DISTORTION))

        val proposal = CharacterScaleAnalyzer.generateScaleProposal(profile)
        assertTrue(proposal.hasScaleAdjustment)
        assertEquals("Proposed X and Y scale must be harmonized", proposal.proposedXScale, proposal.proposedYScale, 0.001)
        assertTrue("Arabic rationale must address aspect ratio repair", proposal.arabicRationale.contains("تشوه نسبة الأبعاد"))
    }

    @Test
    fun test4_BossAndTinyArchetypeTolerance() {
        // Boss character (Apocalypse)
        val bossCns = """
            [data]
            life = 1500
            attack = 130
            defence = 120
            
            [size]
            xscale = 1.5
            yscale = 1.5
            height = 160
            ground.front = 35
            ground.back = 30
        """.trimIndent()

        val bossDir = createCharacterFolder("apocalypse_boss", bossCns)
        val bossEditor = CnsEditor(File(bossDir, "apocalypse_boss.cns"))
        val bossDef = DefEditor(File(bossDir, "apocalypse_boss.def"))

        val bossProfile = CharacterScaleAnalyzer.extractScaleProfile(bossEditor, bossDef, "apocalypse_boss", "Apocalypse Boss")
        assertEquals("Should classify as GIANT_BOSS archetype", ScaleArchetype.GIANT_BOSS, bossProfile.archetype)

        val bossProposal = CharacterScaleAnalyzer.generateScaleProposal(bossProfile)
        assertTrue("Boss should preserve large scale without aggressive flattening", bossProposal.proposedXScale >= 1.40)

        // Tiny character (Servbot)
        val tinyCns = """
            [data]
            life = 850
            attack = 85
            defence = 90
            
            [size]
            xscale = 0.65
            yscale = 0.65
            height = 35
            ground.front = 10
            ground.back = 10
        """.trimIndent()

        val tinyDir = createCharacterFolder("servbot_mini", tinyCns)
        val tinyEditor = CnsEditor(File(tinyDir, "servbot_mini.cns"))
        val tinyDef = DefEditor(File(tinyDir, "servbot_mini.def"))

        val tinyProfile = CharacterScaleAnalyzer.extractScaleProfile(tinyEditor, tinyDef, "servbot_mini", "Servbot")
        assertEquals("Should classify as TINY_SMALL archetype", ScaleArchetype.TINY_SMALL, tinyProfile.archetype)

        val tinyProposal = CharacterScaleAnalyzer.generateScaleProposal(tinyProfile)
        assertTrue("Tiny character should retain small proportions", tinyProposal.proposedXScale <= 0.85)
    }

    @Test
    fun test5_ModernAndLegacySizePropertiesParsing() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 1.05
            yscale = 1.05
            height = 65
            ground.front = 18
            ground.back = 16
            air.front = 14
            air.back = 12
            head.pos = -4, -85
            mid.pos = -4, -55
            stand.sizebox = -18, -65, 18, 0
            air.sizebox = -14, -65, 14, 0
            
            [velocity]
            walk.fwd = 2.8
            walk.back = -2.4
            run.fwd = 7.0, 0
            jump.neu = 0, -8.6
            
            [movement]
            yaccel = 0.45
        """.trimIndent()

        val charDir = createCharacterFolder("complete_props", cns)
        val editor = CnsEditor(File(charDir, "complete_props.cns"))
        val defEditor = DefEditor(File(charDir, "complete_props.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "complete_props", "Fighter")
        val dim = profile.physicalDimensions
        assertEquals(65, dim.height)
        assertEquals(18, dim.groundFront)
        assertEquals(16, dim.groundBack)
        assertEquals(14, dim.airFront)
        assertEquals(12, dim.airBack)
        assertEquals(-4, dim.headPosX)
        assertEquals(-85, dim.headPosY)
        assertEquals(-4, dim.midPosX)
        assertEquals(-55, dim.midPosY)
        assertEquals("-18, -65, 18, 0", dim.standSizebox)
        assertEquals("-14, -65, 14, 0", dim.airSizebox)

        val mov = profile.movementMetrics
        assertEquals(2.8, mov.walkFwd!!, 0.001)
        assertEquals(-2.4, mov.walkBack!!, 0.001)
        assertEquals(7.0, mov.runFwdX!!, 0.001)
        assertEquals(-8.6, mov.jumpNeuY!!, 0.001)
        assertEquals(0.45, mov.yAccel!!, 0.001)
    }

    @Test
    fun test6_MissingPropertiesFallbackSafety() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
        """.trimIndent()

        val charDir = createCharacterFolder("minimal_char", cns)
        val editor = CnsEditor(File(charDir, "minimal_char.cns"))
        val defEditor = DefEditor(File(charDir, "minimal_char.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "minimal_char", "Minimal")
        assertEquals("Missing xscale should fallback to 1.0", 1.0, profile.visualScale.xScale, 0.001)
        assertEquals("Missing yscale should fallback to 1.0", 1.0, profile.visualScale.yScale, 0.001)
        assertNull(profile.physicalDimensions.height)

        val proposal = CharacterScaleAnalyzer.generateScaleProposal(profile)
        assertNotNull(proposal)
        assertFalse(proposal.hasScaleAdjustment)
    }

    @Test
    fun test7_LocalcoordResolutionAndMismatchDetection() {
        val def = """
            [Info]
            name = "hd_char"
            displayname = "HD Fighter"
            localcoord = 640, 480
            
            [Files]
            cns = hd_char.cns
            anim = hd_char.air
        """.trimIndent()

        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 1.4
            yscale = 1.4
            height = 60
        """.trimIndent()

        val charDir = createCharacterFolder("hd_char", cns, def)
        val editor = CnsEditor(File(charDir, "hd_char.cns"))
        val defEditor = DefEditor(File(charDir, "hd_char.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "hd_char", "HD Fighter")
        assertEquals("640, 480", profile.visualScale.declaredLocalcoord)
        assertEquals(640, profile.visualScale.localcoordWidth)
        assertEquals(480, profile.visualScale.localcoordHeight)

        assertTrue(
            "Should flag localcoord mismatch when high-res declared with excessive scale",
            profile.detectedAnomalies.contains(ScaleAnomalyType.MISSING_OR_MISMATCHED_LOCALCOORD) ||
                    profile.detectedAnomalies.contains(ScaleAnomalyType.OVERSIZED_SPRITE)
        )
    }

    @Test
    fun test8_PushboxScaleMismatchDetection() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 0.5
            yscale = 0.5
            height = 135
            ground.front = 45
            ground.back = 40
        """.trimIndent()

        val charDir = createCharacterFolder("pushbox_mismatch", cns)
        val editor = CnsEditor(File(charDir, "pushbox_mismatch.cns"))
        val defEditor = DefEditor(File(charDir, "pushbox_mismatch.def"))

        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "pushbox_mismatch", "Mismatch")
        assertTrue(
            "Must detect pushbox scale mismatch",
            profile.detectedAnomalies.contains(ScaleAnomalyType.PUSHBOX_SCALE_MISMATCH)
        )
    }

    @Test
    fun test9_WorkspaceIsolationAndImmutabilityDuringAnalysis() {
        val cns = """
            [data]
            life = 1200
            attack = 110
            defence = 90
            
            [size]
            xscale = 1.6
            yscale = 1.2
            height = 85
        """.trimIndent()

        val charDir = createCharacterFolder("immutable_char", cns)
        val cnsFile = File(charDir, "immutable_char.cns")
        val defFile = File(charDir, "immutable_char.def")

        val cnsShaBefore = sha256(cnsFile)
        val defShaBefore = sha256(defFile)

        // Perform multi-pass scale analysis and roster scanning
        val editor = CnsEditor(cnsFile)
        val defEditor = DefEditor(defFile)
        val profile = CharacterScaleAnalyzer.extractScaleProfile(editor, defEditor, "immutable_char", "Immutable")
        val proposal = CharacterScaleAnalyzer.generateScaleProposal(profile)

        val charData = CharacterData(
            folderName = "immutable_char",
            displayName = "Immutable",
            imageUri = null,
            cnsFile = cnsFile,
            defFile = defFile
        )
        val summary = RosterBalancingEngine.analyzeCharacterDataRoster(listOf(charData))

        assertEquals("Zero disk writes: CNS SHA-256 must remain identical", cnsShaBefore, sha256(cnsFile))
        assertEquals("Zero disk writes: DEF SHA-256 must remain identical", defShaBefore, sha256(defFile))
        assertTrue(proposal.hasScaleAdjustment)
        assertEquals(1, summary.scaleProposals.size)
    }

    @Test
    fun test10_AtomicSaveReloadAndRollbackViaWorkspaceManager() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            xscale = 1.5
            yscale = 1.5
            height = 80
            ground.front = 20
            ground.back = 18
        """.trimIndent()

        val charDir = createCharacterFolder("save_rollback_char", cns)
        val cnsFile = File(charDir, "save_rollback_char.cns")
        val defFile = File(charDir, "save_rollback_char.def")

        val origSha = sha256(cnsFile)

        val editor = CnsEditor(cnsFile, context)
        val defEditor = DefEditor(defFile, context)

        // Modify scale properties safely
        editor.setProperty("[size]", "xscale", "1.05")
        editor.setProperty("[size]", "yscale", "1.05")
        editor.setProperty("[size]", "height", "56")
        editor.setProperty("[size]", "ground.front", "14")
        editor.setProperty("[size]", "ground.back", "13")

        val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "save_rollback_char",
            defEditor = defEditor,
            cnsEditor = editor,
            context = context
        )

        assertTrue("Atomic save must succeed", saveRes.success)
        assertNotEquals("CNS file should have updated SHA-256 after save", origSha, sha256(cnsFile))

        // Reload and verify persisted properties
        val reloadedEditor = CnsEditor(cnsFile)
        assertEquals("1.05", reloadedEditor.getProperty("[size]", "xscale"))
        assertEquals("1.05", reloadedEditor.getProperty("[size]", "yscale"))
        assertEquals("56", reloadedEditor.getProperty("[size]", "height"))

        // Rollback to pristine state
        val resetResult = CharacterWorkspaceManager.restoreSingleCharacter("save_rollback_char", context)
        assertTrue("Reset to pristine must succeed: ${resetResult.errorMessage}", resetResult.success)

        // Verify restored file matches original sha256 exactly
        val restoredSha = sha256(cnsFile)
        assertEquals("Restored file must match original SHA-256 exactly", origSha, restoredSha)

        val pristineEditor = CnsEditor(cnsFile)
        assertEquals("1.5", pristineEditor.getProperty("[size]", "xscale"))
        assertEquals("80", pristineEditor.getProperty("[size]", "height"))
    }
}
