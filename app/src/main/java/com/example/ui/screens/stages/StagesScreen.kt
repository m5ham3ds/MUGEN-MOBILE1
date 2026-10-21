package com.example.ui.screens.stages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.engine.MugenParser
import com.example.engine.SelectDefManager
import com.example.engine.StageData
import com.example.engine.StageValidationStatus
import com.example.storage.MugenMobileStorage
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.utils.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StageFilter(val label: String) {
    ALL("الكل"),
    REGISTERED("المسجلة"),
    UNREGISTERED("غير مسجلة"),
    READY("جاهزة للقتال")
}

@Composable
fun StagesScreen(hasPermission: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stages by remember { mutableStateOf<List<StageData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf(StageFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var favoritePaths by remember { mutableStateOf(setOf<String>()) }
    var showStagesDirDialog by remember { mutableStateOf(false) }

    fun refreshStages() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                stages = MugenParser.getStages(context)
            }
            isLoading = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            refreshStages()
        } else {
            isLoading = false
        }
    }

    if (!hasPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MugenBgDeep),
            contentAlignment = Alignment.Center
        ) {
            MugenCard(
                modifier = Modifier.padding(24.dp),
                borderColor = MugenRedBorderBright
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MugenRedGlow,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "صلاحية التخزين مطلوبة لاستكشاف المراحل.",
                        color = MugenWhitePrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MugenBgDeep),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MugenRedPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "جاري استكشاف وفحص المراحل...",
                    color = MugenWhitePrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    val filteredStages = remember(stages, selectedFilter, searchQuery, favoritePaths) {
        stages.filter { stage ->
            val matchesFilter = when (selectedFilter) {
                StageFilter.ALL -> true
                StageFilter.REGISTERED -> stage.isRegisteredInSelectDef
                StageFilter.UNREGISTERED -> !stage.isRegisteredInSelectDef
                StageFilter.READY -> stage.status == StageValidationStatus.READY
            }
            val matchesQuery = searchQuery.isBlank() ||
                    stage.displayName.contains(searchQuery, ignoreCase = true) ||
                    stage.relativePath.contains(searchQuery, ignoreCase = true) ||
                    (stage.author?.contains(searchQuery, ignoreCase = true) == true)
            matchesFilter && matchesQuery
        }
    }

    val totalCount = stages.size
    val readyCount = stages.count { it.status == StageValidationStatus.READY }
    val registeredCount = stages.count { it.isRegisteredInSelectDef }
    val unregisteredCount = totalCount - registeredCount

    MugenFighterBackground(intensity = 0.35f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // --- TOP HUD HERO CARD (Reference D) ---
            MugenCard(
                modifier = Modifier.fillMaxWidth(),
                shape = CyberCutMedium,
                borderColor = MugenRedBorderBright,
                borderWidth = 1.5.dp,
                containerColor = MugenSurfaceCard
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Torii Icon + Counter texts
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CyberCutSmall)
                                .background(Color(0xFF260A0D))
                                .border(1.dp, MugenRedBorder, CyberCutSmall),
                            contentAlignment = Alignment.Center
                        ) {
                            ToriiGateIcon(
                                modifier = Modifier.size(34.dp),
                                color = MugenRedPrimary,
                                glowColor = MugenRedGlow
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "المراحل المكتشفة",
                                    color = MugenWhitePrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "($totalCount)",
                                    color = MugenRedGlow,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$registeredCount مرحلة في $totalCount مسجلة في\nselect.def",
                                color = MugenTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // "تسجيل الكل" Big Red Chamfered Button
                    MugenPrimaryButton(
                        text = "تسجيل الكل",
                        icon = Icons.Default.PlaylistAddCheck,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val readyStages = stages.filter { it.status == StageValidationStatus.READY }
                                val added = SelectDefManager.syncAllReadyStages(context, readyStages)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (added > 0) "تمت إضافة $added مرحلة جديدة إلى select.def" else "جميع المراحل الجاهزة مسجلة مسبقاً",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refreshStages()
                                }
                            }
                        },
                        height = 44.dp,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- SEARCH BAR (Reference D) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CyberCutSmall)
                    .background(MugenSurfaceCard)
                    .border(1.dp, MugenRedBorder, CyberCutSmall)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MugenTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "بحث عن مرحلة بالاسم أو المسار...",
                                color = MugenTextMuted,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = MugenWhitePrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif
                            ),
                            cursorBrush = SolidColor(MugenRedPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MugenTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- FILTER BAR (Reference D) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ALL (الكل)
                StageFilterChip(
                    label = "الكل ($totalCount)",
                    icon = Icons.Default.GridView,
                    isSelected = selectedFilter == StageFilter.ALL,
                    onClick = { selectedFilter = StageFilter.ALL }
                )

                // REGISTERED (المسجلة)
                StageFilterChip(
                    label = "المسجلة",
                    icon = Icons.Default.CheckCircle,
                    isSelected = selectedFilter == StageFilter.REGISTERED,
                    onClick = { selectedFilter = StageFilter.REGISTERED }
                )

                // UNREGISTERED (غير مسجلة)
                StageFilterChip(
                    label = "غير مسجلة",
                    icon = Icons.Default.Cancel,
                    isSelected = selectedFilter == StageFilter.UNREGISTERED,
                    onClick = { selectedFilter = StageFilter.UNREGISTERED }
                )

                // STAGES DIR SHORTCUT BUTTON
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CyberCutSmall)
                        .background(MugenSurfaceCard)
                        .border(1.dp, MugenBorderSubtle, CyberCutSmall)
                        .clickable { showStagesDirDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = MugenTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- STAGES LIST / GRID (Reference D) ---
            if (filteredStages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    MugenCard(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(24.dp),
                        borderColor = MugenBorderSubtle
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MugenTextMuted
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (stages.isEmpty()) "لم يتم العثور على مراحل." else "لا توجد مراحل مطابقة لمعايير البحث.",
                                color = MugenWhitePrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ضع ملفات المراحل (.def و .sff) في مجلد stages:\n${MugenMobileStorage.getStagesDir(context).absolutePath}",
                                color = MugenTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredStages, key = { it.relativePath }) { stage ->
                        val isFavorite = favoritePaths.contains(stage.relativePath)
                        StageItemCard(
                            stage = stage,
                            isFavorite = isFavorite,
                            onToggleFavorite = {
                                favoritePaths = if (isFavorite) {
                                    favoritePaths - stage.relativePath
                                } else {
                                    favoritePaths + stage.relativePath
                                }
                            },
                            onToggleRegistration = {
                                scope.launch(Dispatchers.IO) {
                                    if (stage.isRegisteredInSelectDef) {
                                        SelectDefManager.removeExtraStage(context, stage.relativePath)
                                    } else {
                                        SelectDefManager.addExtraStage(context, stage.relativePath)
                                    }
                                    withContext(Dispatchers.Main) {
                                        refreshStages()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Stages directory info dialog
    if (showStagesDirDialog) {
        AlertDialog(
            onDismissRequest = { showStagesDirDialog = false },
            containerColor = MugenSurfaceCard,
            title = {
                Text(
                    text = "مجلد المراحل (Stages Directory)",
                    color = MugenWhitePrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "يتم استكشاف ملفات المراحل (.def و .sff) تلقائياً من المسار التالي:",
                        color = MugenTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MugenBgDeep)
                            .border(1.dp, MugenBorderSubtle, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = MugenMobileStorage.getStagesDir(context).absolutePath,
                            color = MugenRedPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                MugenSecondaryButton(
                    text = "نسخ المسار",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("StagesDir", MugenMobileStorage.getStagesDir(context).absolutePath))
                        Toast.makeText(context, "تم نسخ مسار مجلد المراحل", Toast.LENGTH_SHORT).show()
                        showStagesDirDialog = false
                    },
                    height = 38.dp
                )
            },
            dismissButton = {
                TextButton(onClick = { showStagesDirDialog = false }) {
                    Text("إغلاق", color = MugenTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun StageFilterChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MugenRedPrimary else MugenSurfaceCard
    val border = if (isSelected) MugenRedGlow else MugenBorderSubtle
    val contentColor = if (isSelected) MugenWhitePrimary else MugenTextSecondary

    Box(
        modifier = Modifier
            .height(40.dp)
            .bounceClick()
            .clip(CyberCutSmall)
            .background(bg)
            .border(1.dp, border, CyberCutSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun StageItemCard(
    stage: StageData,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleRegistration: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    MugenCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CyberCutMedium,
        borderColor = MugenRedBorderBright,
        borderWidth = 1.dp,
        containerColor = MugenSurfaceCard
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- STAGE PREVIEW THUMBNAIL (Reference D) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(CyberCutSmall)
                    .background(Color(0xFF0F1015))
            ) {
                if (stage.imageUri != null) {
                    AsyncImage(
                        model = stage.imageUri,
                        contentDescription = stage.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Stylized Cyber Arena Placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E0E12), Color(0xFF0C0D11))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Landscape,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MugenRedPrimary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stage.displayName,
                                color = MugenWhitePrimary.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Subtle dark vignette at the bottom of the image for text separation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // Top Right Badge: "جاهزة للقتال" (Reference D)
                val badgeColor = when (stage.status) {
                    StageValidationStatus.READY -> MugenGreenSuccess
                    StageValidationStatus.MISSING_SFF -> MugenOrangeWarning
                    StageValidationStatus.CORRUPTED_DEF -> MugenRedPrimary
                }
                val badgeBg = when (stage.status) {
                    StageValidationStatus.READY -> Color(0xFF132B1A)
                    StageValidationStatus.MISSING_SFF -> Color(0xFF382312)
                    StageValidationStatus.CORRUPTED_DEF -> Color(0xFF381014)
                }

                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(CyberCutSmall)
                        .background(badgeBg)
                        .border(1.dp, badgeColor.copy(alpha = 0.8f), CyberCutSmall)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (stage.status) {
                                StageValidationStatus.READY -> Icons.Default.SportsMartialArts
                                StageValidationStatus.MISSING_SFF -> Icons.Default.Warning
                                StageValidationStatus.CORRUPTED_DEF -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stage.validationMessage,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- STAGE DETAILS & ACTIONS SECTION ---
            Column(modifier = Modifier.fillMaxWidth()) {
                // Title Row with Favorite & More
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stage.displayName,
                        color = MugenWhitePrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Favorite Star
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFD700) else MugenTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // More 3-dots
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MugenTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MugenSurfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("نسخ المسار النسبي", color = MugenWhitePrimary) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MugenRedPrimary) },
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("StagePath", stage.relativePath))
                                        Toast.makeText(context, "تم نسخ مسار المرحلة", Toast.LENGTH_SHORT).show()
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("حالة المرحلة: ${stage.validationMessage}", color = MugenWhitePrimary) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MugenBlueInfo) },
                                    onClick = { showMenu = false }
                                )
                            }
                        }
                    }
                }

                // Author Row
                val authorText = if (!stage.author.isNullOrBlank()) stage.author else "غير معروف"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MugenTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "المؤلف : $authorText",
                        color = MugenTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Path Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MugenTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stage.relativePath,
                        color = MugenTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Button (Reference D)
                if (stage.isRegisteredInSelectDef) {
                    // Wide Glowing Red Action Button: "مسجلة في اللعبة (إلغاء)" with checkmark
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .bounceClick()
                            .clip(CyberCutSmall)
                            .background(MugenRedButtonVerticalGradient)
                            .border(1.5.dp, MugenRedGlow, CyberCutSmall)
                            .clickable(onClick = onToggleRegistration),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MugenWhitePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مسجلة في اللعبة (إلغاء)",
                                color = MugenWhitePrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                } else {
                    // Unregistered Action Button: "إضافة إلى select.def" with plus icon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .bounceClick()
                            .clip(CyberCutSmall)
                            .background(MugenSurfaceSecondary)
                            .border(1.dp, MugenRedBorderBright, CyberCutSmall)
                            .clickable(
                                enabled = stage.status == StageValidationStatus.READY,
                                onClick = onToggleRegistration
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = if (stage.status == StageValidationStatus.READY) MugenRedPrimary else MugenTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "إضافة إلى select.def",
                                color = if (stage.status == StageValidationStatus.READY) MugenWhitePrimary else MugenTextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
