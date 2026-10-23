package com.example.ui.screens.characters

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.*
import com.example.engine.gemini.GeminiRosterBalancer
import com.example.engine.scale.*
import com.example.engine.workspace.ChangeRecord
import com.example.engine.workspace.CharacterWorkspaceManager
import com.example.storage.MugenMobileStorage
import com.example.utils.GeminiKeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@Composable
fun RosterBalancingDialog(
    characters: List<CharacterData>,
    onDismiss: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var summary by remember {
        mutableStateOf(RosterBalancingEngine.analyzeCharacterDataRoster(characters))
    }
    var isAiPowered by remember { mutableStateOf(false) }
    var isLoadingAi by remember { mutableStateOf(false) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var isApplyingChanges by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Combat Stats, 2: Physical Scale

    // Maintain selected IDs; default to all characters with adjustments
    val selectedCharacterIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            summary.proposals.filter { it.hasAdjustments }.forEach { put(it.characterId, true) }
        }
    }

    // Toggle to allow enabling/disabling scale adjustments per character
    val scaleAdjustmentsEnabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            summary.proposals.filter { it.scaleProposal?.hasScaleAdjustment == true }.forEach { put(it.characterId, true) }
        }
    }

    val hasGeminiKey = remember { GeminiKeyStoreManager.hasApiKey(context) }

    Dialog(
        onDismissRequest = { if (!isApplyingChanges && !isLoadingAi) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "موازنة وتناغم المقاتلين والفيزياء",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isAiPowered) "تحليل ذكي شامل مدعوم بـ Google Gemini (gemini-2.5-flash)" else "تحليل رياضي ومحلي للأبعاد والقتال",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAiPowered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = if (isAiPowered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isAiPowered) "Gemini AI" else "محرك محلي",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isAiPowered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stats Summary Card
                val combatNeedingCount = summary.combatAdjustmentsCount
                val scaleNeedingCount = summary.physicalCalibrationCount
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("المفحوصة", style = MaterialTheme.typography.labelSmall)
                            Text("${summary.totalCharactersScanned}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("تعديل قتالي", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "$combatNeedingCount",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (combatNeedingCount > 0) Color(0xFFE65100) else Color(0xFF2E7D32)
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("معايرة فيزيائية", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "$scaleNeedingCount",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (scaleNeedingCount > 0) Color(0xFF1976D2) else Color(0xFF2E7D32)
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("متوازنة", style = MaterialTheme.typography.labelSmall)
                            Text("${summary.balancedCharactersCount}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Tabs (All / Combat Stats / Physical Scale)
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الكل (${summary.proposals.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("السمات القتالية ($combatNeedingCount)", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("المقياس والأبعاد ($scaleNeedingCount)", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Gemini Action Button / Hint
                if (hasGeminiKey) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isLoadingAi = true
                                aiErrorMessage = null
                                val key = GeminiKeyStoreManager.getApiKey(context)
                                val inputs = characters.mapNotNull { char ->
                                    val cns = char.cnsFile
                                    if (cns != null && cns.isFile) {
                                        try {
                                            val editor = CnsEditor(cns)
                                            val defEditor = char.defFile?.let { if (it.isFile && it.canRead()) DefEditor(it) else null }
                                            val rawLc = defEditor?.let {
                                                it.getProperty("info", "localcoord")
                                                    ?: it.getProperty("", "localcoord")
                                                    ?: it.properties.values.firstOrNull { sec -> sec.containsKey("localcoord") }?.get("localcoord")?.value
                                            }
                                            CharacterStatInput(
                                                characterId = char.folderName,
                                                displayName = char.displayName,
                                                life = editor.getProperty("[data]", "life")?.trim()?.toIntOrNull(),
                                                attack = editor.getProperty("[data]", "attack")?.trim()?.toIntOrNull(),
                                                defence = editor.getProperty("[data]", "defence")?.trim()?.toIntOrNull(),
                                                xScale = editor.getProperty("[size]", "xscale")?.trim()?.toDoubleOrNull(),
                                                yScale = editor.getProperty("[size]", "yscale")?.trim()?.toDoubleOrNull(),
                                                height = editor.getProperty("[size]", "height")?.trim()?.toIntOrNull(),
                                                groundFront = editor.getProperty("[size]", "ground.front")?.trim()?.toIntOrNull(),
                                                groundBack = editor.getProperty("[size]", "ground.back")?.trim()?.toIntOrNull(),
                                                localcoord = rawLc
                                            )
                                        } catch (_: Exception) {
                                            null
                                        }
                                    } else null
                                }

                                val result = GeminiRosterBalancer.analyze(inputs, key)
                                isLoadingAi = false
                                summary = result.summary
                                isAiPowered = result.isAiPowered
                                aiErrorMessage = result.aiErrorMessage

                                if (result.isAiPowered) {
                                    selectedCharacterIds.clear()
                                    summary.proposals.filter { it.hasAdjustments }.forEach {
                                        selectedCharacterIds[it.characterId] = true
                                    }
                                    scaleAdjustmentsEnabled.clear()
                                    summary.proposals.filter { it.scaleProposal?.hasScaleAdjustment == true }.forEach {
                                        scaleAdjustmentsEnabled[it.characterId] = true
                                    }
                                    Toast.makeText(context, "تم تطبيق مقترحات Gemini AI الشاملة بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoadingAi && !isApplyingChanges
                    ) {
                        if (isLoadingAi) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ التحليل الشامل بواسطة Gemini AI...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isAiPowered) "إعادة التحليل بواسطة Gemini AI" else "تحليل ذكي شامل للأبعاد والقتال بواسطة Gemini AI")
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "يمكنك إضافة مفتاح Gemini في الإعدادات للحصول على موازنة ذكية ومعايرة أبعاد مفسرة.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (aiErrorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ملاحظة: $aiErrorMessage — تم اعتماد المقترحات الرياضية المحلية كبديل آمن.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter proposals according to tab
                val filteredProposals = remember(summary, selectedTab) {
                    when (selectedTab) {
                        1 -> summary.proposals.filter { it.statProposals.isNotEmpty() }
                        2 -> summary.proposals.filter { it.scaleProposal?.hasScaleAdjustment == true }
                        else -> summary.proposals
                    }
                }

                val charactersNeedingAdjustment = filteredProposals.filter { it.hasAdjustments }
                val balancedCharacters = filteredProposals.filter { !it.hasAdjustments }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (charactersNeedingAdjustment.isNotEmpty()) {
                        item {
                            Text(
                                text = "شخصيات مقترح تعديلها (${charactersNeedingAdjustment.size}):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(charactersNeedingAdjustment) { proposal ->
                            val isSelected = selectedCharacterIds[proposal.characterId] ?: false
                            val scaleProposal = proposal.scaleProposal
                            val isScaleEnabled = scaleAdjustmentsEnabled[proposal.characterId] ?: (scaleProposal?.hasScaleAdjustment == true)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Top Row: Checkbox, Name, Archetype, Severity
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedCharacterIds[proposal.characterId] = checked
                                                }
                                            )
                                            Column {
                                                Text(
                                                    text = proposal.displayName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = proposal.characterId,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (scaleProposal != null) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = scaleProposal.archetype.arabicLabel.substringBefore("(").trim(),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = when (proposal.overallSeverity) {
                                                    StatSeverity.EXTREME_OUTLIER -> Color(0xFFB71C1C)
                                                    StatSeverity.OUT_OF_TARGET_RANGE -> Color(0xFFE65100)
                                                    else -> Color(0xFF2E7D32)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = proposal.overallSeverity.arabicLabel,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 9.sp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Combat Stats Badges Row
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        proposal.originalLife?.let { orig ->
                                            val prop = proposal.proposedLife ?: orig
                                            StatBadge(name = "Life", orig = orig, prop = prop, unit = "")
                                        }
                                        proposal.originalAttack?.let { orig ->
                                            val prop = proposal.proposedAttack ?: orig
                                            StatBadge(name = "Attack", orig = orig, prop = prop, unit = "%")
                                        }
                                        proposal.originalDefence?.let { orig ->
                                            val prop = proposal.proposedDefence ?: orig
                                            StatBadge(name = "Defence", orig = orig, prop = prop, unit = "%")
                                        }
                                    }

                                    // Physical Scale Badges Row
                                    if (scaleProposal != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AspectRatio,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "الأبعاد والمقياس الفيزيائي",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                )
                                            }

                                            if (scaleProposal.hasScaleAdjustment) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("معايرة الحجم", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                                    Checkbox(
                                                        checked = isScaleEnabled,
                                                        onCheckedChange = { checked ->
                                                            scaleAdjustmentsEnabled[proposal.characterId] = checked
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            ScaleFactorBadge(
                                                origX = scaleProposal.originalXScale,
                                                origY = scaleProposal.originalYScale,
                                                propX = scaleProposal.proposedXScale,
                                                propY = scaleProposal.proposedYScale
                                            )

                                            scaleProposal.originalHeight?.let { origH ->
                                                val propH = scaleProposal.proposedHeight ?: origH
                                                StatBadge(name = "الارتفاع (تصادم)", orig = origH, prop = propH, unit = "px")
                                            }

                                            if (!scaleProposal.declaredLocalcoord.isNullOrBlank()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "localcoord: ${scaleProposal.declaredLocalcoord}",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                    )
                                                }
                                            }

                                            if (scaleProposal.originalGroundFront != null || scaleProposal.originalGroundBack != null) {
                                                val origG = "[${scaleProposal.originalGroundFront ?: 0}, ${scaleProposal.originalGroundBack ?: 0}]"
                                                val propG = "[${scaleProposal.proposedGroundFront ?: scaleProposal.originalGroundFront ?: 0}, ${scaleProposal.proposedGroundBack ?: scaleProposal.originalGroundBack ?: 0}]"
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "الدفع: $origG → $propG",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                                    )
                                                }
                                            }
                                        }

                                        // Anomalies Badges if present
                                        if (scaleProposal.anomalies.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            @OptIn(ExperimentalLayoutApi::class)
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                for (anomaly in scaleProposal.anomalies) {
                                                    Surface(
                                                        color = Color(0xFFFFF3E0),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = anomaly.arabicDescription,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE65100), fontSize = 9.sp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (scaleProposal.arabicRationale.isNotBlank() && scaleProposal.hasScaleAdjustment) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "توجيه الأبعاد: ${scaleProposal.arabicRationale}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Combat explanation text
                                    Text(
                                        text = proposal.overallExplanation,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (balancedCharacters.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "شخصيات متوازنة بالفعل (${balancedCharacters.size}):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF2E7D32)
                            )
                        }

                        items(balancedCharacters) { balanced ->
                            val sp = balanced.scaleProposal
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = balanced.displayName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "متوازن (Life: ${balanced.originalLife ?: 1000}, Scale: ${sp?.originalXScale ?: 1.0}x, Height: ${sp?.originalHeight ?: 60}px)",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2E7D32), fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                val selectedCount = selectedCharacterIds.values.count { it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isApplyingChanges && !isLoadingAi
                    ) {
                        Text("إغلاق")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isApplyingChanges = true
                                var appliedCount = 0

                                withContext(Dispatchers.IO) {
                                    val charMap = characters.associateBy { it.folderName }
                                    val charsRoot = MugenMobileStorage.getCharsDir(context)

                                    for (proposal in summary.proposals) {
                                        if (selectedCharacterIds[proposal.characterId] == true) {
                                            val charId = proposal.characterId
                                            val charData = charMap[charId]
                                            val charFolder = File(charsRoot, charId)

                                            // Resolve .def file in active workspace
                                            val defFile = charData?.defFile?.takeIf { it.isFile }
                                                ?: File(charFolder, "$charId.def").takeIf { it.isFile }
                                                ?: charFolder.listFiles { f -> f.extension.equals("def", ignoreCase = true) && !f.name.startsWith("intro", ignoreCase = true) }?.firstOrNull()

                                            // Resolve .cns file in active workspace
                                            var cnsFile = charData?.cnsFile?.takeIf { it.isFile }
                                            if (cnsFile == null && defFile != null && defFile.isFile) {
                                                try {
                                                    val tempDef = DefEditor(defFile)
                                                    val cnsRel = tempDef.getProperty("files", "cns") ?: tempDef.getProperty("files", "st")
                                                    if (!cnsRel.isNullOrBlank()) {
                                                        val cleanRel = cnsRel.replace('\\', '/')
                                                        cnsFile = File(charFolder, cleanRel).takeIf { it.isFile }
                                                            ?: charFolder.walkTopDown().firstOrNull { it.isFile && it.name.equals(File(cleanRel).name, ignoreCase = true) }
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                            if (cnsFile == null) {
                                                cnsFile = File(charFolder, "$charId.cns").takeIf { it.isFile }
                                                    ?: charFolder.listFiles { f -> f.extension.equals("cns", ignoreCase = true) }?.firstOrNull()
                                            }

                                            if (cnsFile != null && cnsFile.isFile) {
                                                try {
                                                    // Ensure immutable Vault snapshot exists before modifying
                                                    if (!CharacterWorkspaceManager.isCharacterVaulted(charId, context)) {
                                                        CharacterWorkspaceManager.ensureVaultSnapshot(charFolder, context)
                                                    }

                                                    val preCnsSha = CharacterWorkspaceManager.calculateSha256(cnsFile)
                                                    val editor = CnsEditor(cnsFile)
                                                    val defEditor = defFile?.let { if (it.isFile && it.canRead()) DefEditor(it) else null }

                                                    // Apply combat stats
                                                    proposal.proposedLife?.let { editor.setProperty("[data]", "life", it.toString()) }
                                                    proposal.proposedAttack?.let { editor.setProperty("[data]", "attack", it.toString()) }
                                                    proposal.proposedDefence?.let { editor.setProperty("[data]", "defence", it.toString()) }

                                                    // Apply physical scale & pushbox if enabled
                                                    val sp = proposal.scaleProposal
                                                    val shouldScale = scaleAdjustmentsEnabled[charId] != false
                                                    if (sp != null && shouldScale && (sp.hasScaleAdjustment || selectedTab == 2)) {
                                                        editor.setProperty("[size]", "xscale", sp.proposedXScale.toString())
                                                        editor.setProperty("[size]", "yscale", sp.proposedYScale.toString())
                                                        sp.proposedHeight?.let { editor.setProperty("[size]", "height", it.toString()) }
                                                        sp.proposedGroundFront?.let { editor.setProperty("[size]", "ground.front", it.toString()) }
                                                        sp.proposedGroundBack?.let { editor.setProperty("[size]", "ground.back", it.toString()) }

                                                        if (defEditor != null && sp.proposedLocalcoord != null) {
                                                            defEditor.setProperty("info", "localcoord", sp.proposedLocalcoord)
                                                        }
                                                    }

                                                    val saveRes = CharacterWorkspaceManager.saveCompleteCharacterEdits(
                                                        characterId = charId,
                                                        defEditor = defEditor,
                                                        cnsEditor = editor,
                                                        context = context
                                                    )
                                                    if (saveRes.success) {
                                                        val postCnsSha = CharacterWorkspaceManager.calculateSha256(cnsFile)
                                                        val diskContent = cnsFile.readText()
                                                        val hasScaleOnDisk = diskContent.contains("xscale =")
                                                        android.util.Log.i("RosterBalancingDialog", "Applied balance to '$charId' [path=${cnsFile.absolutePath}, preSha=$preCnsSha, postSha=$postCnsSha, hasScaleOnDisk=$hasScaleOnDisk]")
                                                        appliedCount++
                                                    } else {
                                                        android.util.Log.e("RosterBalancingDialog", "Failed to save balance edits for '$charId': ${saveRes.errorMessage}")
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("RosterBalancingDialog", "Exception applying balance to '$charId': ${e.message}", e)
                                                }
                                            }
                                        }
                                    }

                                    // Ensure fight.def has safe portrait window clipping and scale applied
                                    try {
                                        val clipped = FightDefManager.ensureSafePortraitClipping(context)
                                        android.util.Log.i("RosterBalancingDialog", "Enforced safe portrait clipping in fight.def: success=$clipped")
                                    } catch (e: Exception) {
                                        android.util.Log.w("RosterBalancingDialog", "Non-fatal clipping notice: ${e.message}")
                                    }
                                }

                                isApplyingChanges = false
                                Toast.makeText(
                                    context,
                                    "تم بنجاح تطبيق الموازنة ومعايرة الأبعاد على $appliedCount مقاتلين في مساحة العمل",
                                    Toast.LENGTH_LONG
                                ).show()
                                onApplied()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(2f),
                        enabled = !isApplyingChanges && !isLoadingAi && selectedCount > 0
                    ) {
                        if (isApplyingChanges) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جارٍ التطبيق...", maxLines = 1)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تطبيق الموازنة والأبعاد ($selectedCount)", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(name: String, orig: Int, prop: Int, unit: String) {
    val delta = prop - orig
    val isChanged = delta != 0
    Surface(
        color = if (isChanged) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$name: $orig$unit",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1
            )
            if (isChanged) {
                Text(
                    text = " → $prop$unit",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary),
                    maxLines = 1
                )
                Text(
                    text = if (delta > 0) " (+${delta}$unit)" else " (${delta}$unit)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (delta > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ScaleFactorBadge(origX: Double, origY: Double, propX: Double, propY: Double) {
    val isChanged = abs(propX - origX) > 0.005 || abs(propY - origY) > 0.005
    Surface(
        color = if (isChanged) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val origStr = if (origX == origY) "${origX}x" else "${origX}x${origY}"
            val propStr = if (propX == propY) "${propX}x" else "${propX}x${propY}"
            Text(
                text = "المقياس: $origStr",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1
            )
            if (isChanged) {
                Text(
                    text = " → $propStr",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary),
                    maxLines = 1
                )
            }
        }
    }
}
