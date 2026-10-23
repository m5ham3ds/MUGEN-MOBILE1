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
 * Forensic Verification Test Suite for PHASE 4.3.2:
 * RUNTIME ASSET PATH AUDIT & PHYSICAL BALANCE APPLICATION REPAIR.
 *
 * Verifies:
 * 1. CnsEditor correctly inserts missing xscale & yscale without in-memory wiping.
 * 2. CnsEditor recognizes section headers with trailing inline comments.
 * 3. Physical scale balance writes to active game files and changes disk SHA-256.
 * 4. Vault immutability: pristine hashes remain untouched during balance application.
 * 5. FightDefManager resolves active fight.def from data/system.def.
 * 6. FightDefManager identifies unsafe/fullscreen windows and replaces them with bounded coordinates.
 * 7. FightDefManager enforces face scale (p1.face.scale, p2.face.scale).
 * 8. FightDefManager auto-creates minimal valid fight.def if missing.
 * 9. GeminiApiClient uses gemini-3.8-flash with fallback and key masking.
 * 10. Dynamic CNS resolution correctly locates .cns from .def when charData.cnsFile is null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeAssetApplicationRepairTest {

    private lateinit var context: Context
    private lateinit var tempGameDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempGameDir = File(context.cacheDir, "test_repair_432_${System.currentTimeMillis()}")
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
     * Test 1: Verifies that CnsEditor correctly inserts missing xscale and yscale
     * into a CNS file that initially has no scale properties, without wiping in-memory state.
     */
    @Test
    fun test01_CnsEditorInsertsMissingXScaleAndYScaleWithoutDiskWipe() {
        val cns = """
            [Data] ; basic fighter stats
            life = 1000
            attack = 100
            defence = 100
            
            [Size] ; collision and dimension settings
            z.width = 3
            height = 60
        """.trimIndent()

        val charDir = createCharacterFolder("cid_kagenou", cns)
        val cnsFile = File(charDir, "cid_kagenou.cns")
        val defFile = File(charDir, "cid_kagenou.def")

        CharacterWorkspaceManager.ensureVaultSnapshot(charDir, context)

        val editor = CnsEditor(cnsFile)

        // Verify initial state has no xscale or yscale
        assertNull("Initial xscale should be null", editor.getProperty("[size]", "xscale"))
        assertNull("Initial yscale should be null", editor.getProperty("[size]", "yscale"))

        // Set life change, then set new scale properties
        editor.setProperty("[data]", "life", "1150")
        editor.setProperty("[size]", "xscale", "0.85")
        editor.setProperty("[size]", "yscale", "0.85")

        // In-memory verification
        assertEquals("1150", editor.getProperty("[data]", "life"))
        assertEquals("0.85", editor.getProperty("[size]", "xscale"))
        assertEquals("0.85", editor.getProperty("[size]", "yscale"))

        val defEditor = DefEditor(defFile)
        val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "cid_kagenou",
            defEditor = defEditor,
            cnsEditor = editor,
            context = context
        )

        assertTrue("Multi-file save must succeed", saveRes.success)

        // Read physical file from disk to verify persistence
        val diskContent = cnsFile.readText()
        assertTrue("Disk content must contain life = 1150", diskContent.contains("life = 1150"))
        assertTrue("Disk content must contain xscale = 0.85", diskContent.contains("xscale = 0.85"))
        assertTrue("Disk content must contain yscale = 0.85", diskContent.contains("yscale = 0.85"))

        // Fresh instance read from disk
        val freshEditor = CnsEditor(cnsFile)
        assertEquals("1150", freshEditor.getProperty("[data]", "life"))
        assertEquals("0.85", freshEditor.getProperty("[size]", "xscale"))
        assertEquals("0.85", freshEditor.getProperty("[size]", "yscale"))
    }

    /**
     * Test 2: Verifies that CnsEditor correctly parses section headers with trailing comments.
     */
    @Test
    fun test02_CnsEditorPreservesSectionWithTrailingComments() {
        val cns = """
            [Data] ; Player combat data
            life = 950
            
            [Size] ; Player physical dimensions
            height = 70
        """.trimIndent()

        val charDir = createCharacterFolder("comment_test", cns)
        val cnsFile = File(charDir, "comment_test.cns")

        val editor = CnsEditor(cnsFile)
        assertEquals("950", editor.getProperty("[data]", "life"))
        assertEquals("70", editor.getProperty("[size]", "height"))

        editor.setProperty("[size]", "xscale", "1.20")
        assertEquals("1.20", editor.getProperty("[size]", "xscale"))
        assertEquals("70", editor.getProperty("[size]", "height"))
    }

    /**
     * Test 3: Verifies that physical scale balancing updates the active file on disk
     * and causes an authentic cryptographic SHA-256 change.
     */
    @Test
    fun test03_PhysicalScaleApplicationPipelineUpdatesDiskAndChangesSha256() {
        val cns = """
            [data]
            life = 1000
            attack = 100
            defence = 100
            
            [size]
            height = 60
        """.trimIndent()

        val charDir = createCharacterFolder("active_fighter", cns)
        val cnsFile = File(charDir, "active_fighter.cns")
        val defFile = File(charDir, "active_fighter.def")

        CharacterWorkspaceManager.ensureVaultSnapshot(charDir, context)
        val initialSha = sha256(cnsFile)

        val cnsEditor = CnsEditor(cnsFile)
        cnsEditor.setProperty("[size]", "xscale", "0.75")
        cnsEditor.setProperty("[size]", "yscale", "0.75")

        val defEditor = DefEditor(defFile)
        val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "active_fighter",
            defEditor = defEditor,
            cnsEditor = cnsEditor,
            context = context
        )

        assertTrue(saveRes.success)
        val finalSha = sha256(cnsFile)
        assertNotEquals("Active file SHA-256 must change after physical scale application", initialSha, finalSha)
        assertEquals(saveRes.newSha256, finalSha)
    }

    /**
     * Test 4: Verifies Vault immutability during character scale balancing.
     */
    @Test
    fun test04_VaultImmutabilityGuaranteedDuringBalanceApplication() {
        val cns = """
            [data]
            life = 1000
            
            [size]
            height = 60
        """.trimIndent()

        val charDir = createCharacterFolder("vault_protected", cns)
        val cnsFile = File(charDir, "vault_protected.cns")

        val snapshotRes = CharacterWorkspaceManager.capturePristineSnapshot("vault_protected", charDir, context)
        assertTrue(snapshotRes.success)
        val initialVaultSha = snapshotRes.fingerprints["vault_protected.cns"]
        assertNotNull(initialVaultSha)

        val editor = CnsEditor(cnsFile)
        editor.setProperty("[size]", "xscale", "1.50")
        editor.setProperty("[size]", "yscale", "1.50")

        CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "vault_protected",
            defEditor = null,
            cnsEditor = editor,
            context = context
        )

        // Verify Vault integrity
        val (isVaultHealthy, vaultError) = CharacterWorkspaceManager.verifyVaultIntegrity("vault_protected", context)
        assertTrue("Vault must remain healthy after balance: $vaultError", isVaultHealthy)

        val vaultFile = File(File(CharacterWorkspaceManager.getVaultDir(context), "vault_protected"), "files/vault_protected.cns")
        assertTrue(vaultFile.exists())
        assertEquals("Pristine vault copy must not be altered by workspace balance writes", initialVaultSha, sha256(vaultFile))
    }

    /**
     * Test 5: Verifies that FightDefManager inspects data/system.def to resolve the active fight definition.
     */
    @Test
    fun test05_FightDefManagerResolvesActiveSystemDefMotif() {
        val baseDir = MugenMobileStorage.getBaseDir(context)
        val dataDir = File(baseDir, "data").apply { mkdirs() }
        val systemDef = File(dataDir, "system.def")
        val customFightDef = File(dataDir, "mugen1_fight.def").apply {
            writeText("[Face]\np1.pos = 14, 20\n")
        }

        systemDef.writeText("""
            [Files]
            motif = data/system.def
            fight = mugen1_fight.def
        """.trimIndent())

        val resolved = FightDefManager.resolveActiveFightDef(context)
        assertEquals("Must resolve to the fight.def specified in system.def", customFightDef.canonicalPath, resolved.canonicalPath)
    }

    /**
     * Test 6: Verifies that FightDefManager detects unsafe/fullscreen portrait windows and replaces them.
     */
    @Test
    fun test06_FightDefManagerEnforcesSafePortraitWindowAndScale() {
        val baseDir = MugenMobileStorage.getBaseDir(context)
        val dataDir = File(baseDir, "data").apply { mkdirs() }
        val fightDef = File(dataDir, "fight.def")

        // Unsafe fullscreen window
        fightDef.writeText("""
            [Face]
            p1.pos = 14, 20
            p1.face.spr = 9000, 0
            p1.face.window = 0, 0, 1280, 720
            p2.pos = 1266, 20
            p2.face.spr = 9000, 0
            p2.face.window = 0, 0, 1280, 720
        """.trimIndent())

        assertFalse("Fullscreen window must be classified as unsafe", FightDefManager.isWindowSafe("0, 0, 1280, 720"))
        assertTrue("Default 1280 window must be classified as safe", FightDefManager.isWindowSafe(FightDefManager.DEFAULT_1280_P1_WINDOW))

        val enforced = FightDefManager.ensureSafePortraitClipping(context)
        assertTrue(enforced)

        val config = FightDefManager.inspectFightDef(fightDef)
        assertTrue("HUD clipping must now be safe", config.hasSafeClipping)
        assertEquals(FightDefManager.DEFAULT_1280_P1_WINDOW, config.p1Window)
        assertEquals(FightDefManager.DEFAULT_1280_P2_WINDOW, config.p2Window)
        assertNotNull("Face scale must be configured", config.p1Scale)
    }

    /**
     * Test 7: Verifies that FightDefManager auto-creates minimal valid fight.def if missing.
     */
    @Test
    fun test07_FightDefManagerAutoCreatesMissingFightDef() {
        val baseDir = MugenMobileStorage.getBaseDir(context)
        val dataDir = File(baseDir, "data").apply { mkdirs() }
        val fightDef = File(dataDir, "fight.def")
        if (fightDef.exists()) fightDef.delete()

        val success = FightDefManager.ensureSafePortraitClipping(context)
        assertTrue("Should auto-create missing fight.def", success)
        assertTrue("fight.def must now exist on disk", fightDef.exists())

        val config = FightDefManager.inspectFightDef(fightDef)
        assertTrue(config.hasFaceSection)
        assertTrue(config.hasSafeClipping)
    }

    /**
     * Test 8: Verifies GeminiApiClient model constants and error sanitization.
     */
    @Test
    fun test08_GeminiApiClientUsesValidModelAndHandlesFallback() {
        assertEquals("gemini-3.8-flash", GeminiApiClient.DEFAULT_MODEL)
        assertEquals("gemini-flash-latest", GeminiApiClient.FALLBACK_MODEL)

        val rawError = "API key AIzaSyD1234567890abcdef1234567890abcde was invalid"
        val sanitized = GeminiApiClient.sanitizeErrorMessage(rawError)
        assertFalse("Sanitized error must not contain raw API key", sanitized.contains("AIzaSyD1234567890abcdef1234567890abcde"))
        assertTrue("Sanitized error must contain redaction placeholder", sanitized.contains("[REDACTED_API_KEY]"))
    }

    /**
     * Test 9: Verifies GeminiRosterBalancer merges valid scale proposals into RosterBalancingSummary.
     */
    @Test
    fun test09_GeminiRosterBalancerMergesValidScaleProposals() {
        val inputs = listOf(
            CharacterStatInput(
                characterId = "goku",
                displayName = "Goku SSJ",
                life = 1000,
                attack = 100,
                defence = 100,
                xScale = 1.0,
                yScale = 1.0,
                height = 65
            )
        )
        val baseline = RosterBalancingEngine.analyzeRoster(inputs)

        val geminiProposals = listOf(
            GeminiCharacterProposal(
                characterId = "goku",
                proposedLife = 1050,
                proposedAttack = 105,
                proposedDefence = 95,
                archetype = "All-Rounder",
                rationale = "Balanced warrior",
                proposedXScale = 0.88,
                proposedYScale = 0.88,
                scaleRationale = "Calibrated for standard height"
            )
        )

        val merged = GeminiRosterBalancer.mergeAndValidateGeminiProposals(
            inputs = inputs,
            baselineSummary = baseline,
            geminiProposals = geminiProposals
        )

        val prop = merged.proposals.first()
        val scaleProp = prop.scaleProposal
        assertNotNull("Scale proposal must be present", scaleProp)
        assertEquals(0.88, scaleProp!!.proposedXScale, 0.001)
        assertEquals(0.88, scaleProp.proposedYScale, 0.001)
        assertTrue("Scale adjustment must be flagged", scaleProp.hasScaleAdjustment)
        assertTrue(scaleProp.isAiGenerated)
        assertTrue(prop.hasAdjustments)
    }

    /**
     * Test 10: Verifies dynamic CNS file resolution from .def when charData.cnsFile is null.
     */
    @Test
    fun test10_DynamicCnsResolutionWhenCharDataCnsIsNull() {
        val cns = """
            [data]
            life = 1000
            
            [size]
            height = 55
        """.trimIndent()

        val def = """
            [Info]
            name = "custom_char"
            displayname = "Custom Character"
            
            [Files]
            cns = custom_named_constants.cns
            anim = custom_char.air
        """.trimIndent()

        val charsRoot = MugenMobileStorage.getCharsDir(context).apply { mkdirs() }
        val charDir = File(charsRoot, "custom_char").apply { mkdirs() }
        val defFile = File(charDir, "custom_char.def").apply { writeText(def) }
        val cnsFile = File(charDir, "custom_named_constants.cns").apply { writeText(cns) }
        File(charDir, "custom_char.air").writeText("Clsn2: 1\nClsn2[0] = -15, -60, 15, 0\n")

        CharacterWorkspaceManager.ensureVaultSnapshot(charDir, context)

        // Resolve .cns from .def dynamically
        val parsedDef = DefEditor(defFile)
        val cnsRel = parsedDef.getProperty("files", "cns")
        assertNotNull(cnsRel)
        val resolvedCns = File(charDir, cnsRel!!)
        assertTrue("Resolved CNS file must exist", resolvedCns.isFile)

        val cnsEditor = CnsEditor(resolvedCns)
        cnsEditor.setProperty("[size]", "xscale", "0.90")
        cnsEditor.setProperty("[size]", "yscale", "0.90")

        val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
            characterId = "custom_char",
            defEditor = parsedDef,
            cnsEditor = cnsEditor,
            context = context
        )

        assertTrue("Dynamic CNS save must succeed", saveRes.success)
        val diskContent = resolvedCns.readText()
        assertTrue("Disk content must contain xscale = 0.90", diskContent.contains("xscale = 0.90"))
    }
}
