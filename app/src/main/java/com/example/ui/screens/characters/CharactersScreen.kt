package com.example.ui.screens.characters

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.engine.CharacterData
import com.example.engine.CharacterValidationStatus
import com.example.engine.MugenParser
import com.example.engine.SelectDefAnalysis
import com.example.engine.SelectDefManager
import com.example.engine.workspace.CharacterWorkspaceManager
import com.example.engine.workspace.CharacterWorkspaceStatus
import com.example.storage.MugenMobileStorage
import com.example.storage.StorageAccessStatus
import com.example.storage.StorageDiagnosticInfo
import com.example.storage.StorageManager
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.utils.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

enum class CharacterFilter {
    ALL,
    MODIFIED,
    PRISTINE,
    READY_ONLY,
    FAVORITES
}

@Composable
fun CharactersScreen(hasPermission: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var characters by remember { mutableStateOf<List<CharacterData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCharacter by remember { mutableStateOf<CharacterData?>(null) }
    var accessStatus by remember { mutableStateOf<StorageAccessStatus?>(null) }
    var diagnostics by remember { mutableStateOf<StorageDiagnosticInfo?>(null) }
    var selectDefAnalysis by remember { mutableStateOf<SelectDefAnalysis?>(null) }
    var showBatchRestoreDialog by remember { mutableStateOf(false) }
    var showRosterBalancingDialog by remember { mutableStateOf(false) }

    // Search and display mode
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }
    var currentFilter by remember { mutableStateOf(CharacterFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf(setOf<String>()) }

    suspend fun reloadData() {
        isLoading = true
        withContext(Dispatchers.IO) {
            accessStatus = MugenMobileStorage.checkStorageAccess(context)
            diagnostics = MugenMobileStorage.getDiagnostics(context)
            characters = MugenParser.getCharacters(context)
            val selectDefFile = SelectDefManager.getSelectDefFile(context)
            selectDefAnalysis = SelectDefManager.analyzeSelectDef(selectDefFile)
        }
        isLoading = false
    }

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            StorageManager.takePersistableUriPermission(context, it)
            val resolved = StorageManager.getFullPathFromTreeUri(context, it)
            val finalPath = if (!resolved.isNullOrEmpty()) {
                resolved
            } else {
                val decoded = try { URLDecoder.decode(it.toString(), "UTF-8") } catch (_: Exception) { it.toString() }
                if (decoded.contains("Documents") && decoded.contains("MUGEN_MOBILE")) {
                    MugenMobileStorage.DEFAULT_PATH
                } else {
                    decoded
                }
            }
            MugenMobileStorage.setCustomBaseDir(context, finalPath, it.toString())
            Toast.makeText(context, "تم تحديث مجلد اللعبة: $finalPath", Toast.LENGTH_SHORT).show()
            scope.launch { reloadData() }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            reloadData()
        } else {
            isLoading = false
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MugenRedGlow, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Storage permission is required.", color = MugenWhitePrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MugenRedPrimary)
        }
        return
    }

    val charsDirPath = MugenMobileStorage.getCharsDir(context).absolutePath
    val validCharactersCount = characters.count { it.isValid }
    val registeredInEngineCount = selectDefAnalysis?.registeredKeys?.size ?: 0

    // Filter characters
    val displayedCharacters = remember(characters, searchQuery, currentFilter, favoriteIds) {
        characters.filter { char ->
            val matchesQuery = searchQuery.isBlank() ||
                    char.displayName.contains(searchQuery, ignoreCase = true) ||
                    char.selectEntry.contains(searchQuery, ignoreCase = true) ||
                    char.folderName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (currentFilter) {
                CharacterFilter.ALL -> true
                CharacterFilter.MODIFIED -> char.workspaceStatus == CharacterWorkspaceStatus.MODIFIED
                CharacterFilter.PRISTINE -> char.workspaceStatus == CharacterWorkspaceStatus.PRISTINE_ORIGINAL
                CharacterFilter.READY_ONLY -> char.validationStatus == CharacterValidationStatus.VALID
                CharacterFilter.FAVORITES -> favoriteIds.contains(char.selectEntry)
            }
            matchesQuery && matchesFilter
        }
    }

    MugenFighterBackground(intensity = 0.25f) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP HEADER INFO CARD (Reference C) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(CyberCutMedium)
                    .background(MugenSurfaceCard)
                    .border(1.5.dp, MugenRedBorderBright, CyberCutMedium)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Row: Path & Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Folder & Path
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF4A1014)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MugenRedPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = ":المسار",
                                color = MugenWhitePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = charsDirPath,
                                color = MugenTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right: 3 Square Action Buttons (Scan, Verify, Refresh)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Scan Button (مسح)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MugenIconButton(
                                    icon = Icons.Default.CenterFocusWeak,
                                    contentDescription = "مسح",
                                    onClick = { scope.launch { reloadData() } },
                                    size = 36.dp,
                                    borderColor = MugenRedBorderBright,
                                    tint = MugenRedGlow
                                )
                                Text("مسح", fontSize = 9.sp, color = MugenTextSecondary)
                            }

                            // Verify Button (تحقق)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MugenIconButton(
                                    icon = Icons.Default.Security,
                                    contentDescription = "تحقق",
                                    onClick = { showBatchRestoreDialog = true },
                                    size = 36.dp,
                                    borderColor = MugenGreenBorder,
                                    tint = MugenGreenSuccess
                                )
                                Text("تحقق", fontSize = 9.sp, color = MugenTextSecondary)
                            }

                            // Refresh Button (تحديث)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MugenIconButton(
                                    icon = Icons.Default.Refresh,
                                    contentDescription = "تحديث",
                                    onClick = {
                                        scope.launch {
                                            reloadData()
                                            Toast.makeText(context, "تم تحديث البيانات", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    size = 36.dp,
                                    borderColor = MugenRedBorderBright,
                                    tint = MugenRedPrimary
                                )
                                Text("تحديث", fontSize = 9.sp, color = MugenTextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Row: 2 Stat Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Chip: Discovered Characters (Red surface)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CyberCutSmall)
                                .background(Color(0xFF381014))
                                .border(1.dp, MugenRedBorderBright, CyberCutSmall)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MugenRedGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مكتشفة: ${characters.size} ($validCharactersCount صالحة)",
                                    color = MugenWhitePrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Right Chip: Registered in select.def (Green surface)
                        Box(
                            modifier = Modifier
                                .weight(1.15f)
                                .clip(CyberCutSmall)
                                .background(Color(0xFF132B1A))
                                .border(1.dp, MugenGreenBorder, CyberCutSmall)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Gamepad,
                                    contentDescription = null,
                                    tint = MugenGreenSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مسجلة بالمحرك (select.def): $registeredInEngineCount",
                                    color = MugenGreenSuccess,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // --- SEARCH & VIEW CONTROLS TOOLBAR (Reference C) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(CyberCutSmall)
                        .background(MugenSurfaceCard)
                        .border(1.dp, MugenBorderSubtle, CyberCutSmall)
                        .padding(horizontal = 10.dp),
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "البحث عن شخصية...",
                                    color = MugenTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = MugenWhitePrimary,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = MugenTextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Grid View Button (Red active)
                MugenIconButton(
                    icon = Icons.Default.GridView,
                    contentDescription = "Grid View",
                    onClick = { isGridView = true },
                    size = 42.dp,
                    isSelected = isGridView
                )

                // List View Button
                MugenIconButton(
                    icon = Icons.Default.ViewList,
                    contentDescription = "List View",
                    onClick = { isGridView = false },
                    size = 42.dp,
                    isSelected = !isGridView
                )

                // Filter Button with Dropdown
                Box {
                    MugenIconButton(
                        icon = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        onClick = { showFilterMenu = true },
                        size = 42.dp,
                        borderColor = if (currentFilter != CharacterFilter.ALL) MugenRedBorderBright else MugenBorderSubtle,
                        tint = if (currentFilter != CharacterFilter.ALL) MugenRedGlow else MugenTextSecondary
                    )

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                        modifier = Modifier
                            .background(MugenSurfaceCard)
                            .border(1.dp, MugenBorderSubtle, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("جميع الشخصيات", color = MugenWhitePrimary, fontSize = 13.sp) },
                            onClick = { currentFilter = CharacterFilter.ALL; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("شخصيات جاهزة فقط", color = MugenGreenSuccess, fontSize = 13.sp) },
                            onClick = { currentFilter = CharacterFilter.READY_ONLY; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("الشخصيات المعدلة", color = MugenOrangeWarning, fontSize = 13.sp) },
                            onClick = { currentFilter = CharacterFilter.MODIFIED; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("الشخصيات الأصلية (Pristine)", color = MugenWhitePrimary, fontSize = 13.sp) },
                            onClick = { currentFilter = CharacterFilter.PRISTINE; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("المفضلة", color = Color(0xFFFFD700), fontSize = 13.sp) },
                            onClick = { currentFilter = CharacterFilter.FAVORITES; showFilterMenu = false }
                        )
                        HorizontalDivider(color = MugenBorderSubtle)
                        DropdownMenuItem(
                            text = { Text("موازنة القائمة الذكية", color = MugenRedGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            onClick = { showRosterBalancingDialog = true; showFilterMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("اختيار مجلد آخر", color = MugenTextSecondary, fontSize = 13.sp) },
                            onClick = { documentTreeLauncher.launch(null); showFilterMenu = false }
                        )
                    }
                }
            }

            // --- CHARACTER CARDS (Grid or List View) ---
            if (displayedCharacters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "لا توجد شخصيات مطابقة للبحث" else "لم يتم العثور على شخصيات",
                            color = MugenWhitePrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ضع مجلدات الشخصيات في:\n$charsDirPath",
                            color = MugenTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedCharacters, key = { it.selectEntry }) { char ->
                        FightingCharacterGridCard(
                            character = char,
                            isFavorite = favoriteIds.contains(char.selectEntry),
                            onToggleFavorite = {
                                favoriteIds = if (favoriteIds.contains(char.selectEntry)) {
                                    favoriteIds - char.selectEntry
                                } else {
                                    favoriteIds + char.selectEntry
                                }
                            },
                            onClick = { selectedCharacter = char }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedCharacters, key = { it.selectEntry }) { char ->
                        FightingCharacterListCard(
                            character = char,
                            isFavorite = favoriteIds.contains(char.selectEntry),
                            onToggleFavorite = {
                                favoriteIds = if (favoriteIds.contains(char.selectEntry)) {
                                    favoriteIds - char.selectEntry
                                } else {
                                    favoriteIds + char.selectEntry
                                }
                            },
                            onClick = { selectedCharacter = char }
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS (Integrated) ---

    if (showRosterBalancingDialog) {
        RosterBalancingDialog(
            characters = characters,
            onDismiss = { showRosterBalancingDialog = false },
            onApplied = { scope.launch { reloadData() } }
        )
    }

    if (showBatchRestoreDialog) {
        BatchRestoreDialog(
            onDismiss = { showBatchRestoreDialog = false },
            onRestored = {
                showBatchRestoreDialog = false
                scope.launch { reloadData() }
            }
        )
    }

    selectedCharacter?.let { char ->
        CompleteCharacterEditorDialog(
            character = char,
            onDismiss = { selectedCharacter = null },
            onCharacterUpdated = { scope.launch { reloadData() } }
        )
    }
}

@Composable
fun FightingCharacterGridCard(
    character: CharacterData,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clip(CyberCutMedium)
            .background(MugenSurfaceCard)
            .border(1.5.dp, MugenRedBorderBright, CyberCutMedium)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Character Portrait Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .background(Color(0xFF0F1015)),
                contentAlignment = Alignment.Center
            ) {
                if (character.imageUri != null) {
                    AsyncImage(
                        model = character.imageUri,
                        contentDescription = character.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MugenTextSecondary.copy(alpha = 0.4f)
                    )
                }

                // Badges Overlay (Top-Left: Original/Modified, Top-Right: Ready/No Icon)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Badge
                    when (character.workspaceStatus) {
                        CharacterWorkspaceStatus.MODIFIED -> {
                            MugenStatusBadge(text = "معدلة", style = BadgeStyle.WARNING)
                        }
                        CharacterWorkspaceStatus.RESTORED -> {
                            MugenStatusBadge(text = "مستعادة", style = BadgeStyle.INFO)
                        }
                        else -> {
                            MugenStatusBadge(text = "أصلية", style = BadgeStyle.SUCCESS)
                        }
                    }

                    // Right Badge
                    when (character.validationStatus) {
                        CharacterValidationStatus.VALID -> {
                            MugenStatusBadge(text = "Ready", style = BadgeStyle.SUCCESS)
                        }
                        CharacterValidationStatus.VALID_MISSING_PORTRAIT -> {
                            MugenStatusBadge(text = "No Icon", style = BadgeStyle.WARNING)
                        }
                        else -> {
                            MugenStatusBadge(text = "Error", style = BadgeStyle.DANGER)
                        }
                    }
                }
            }

            // Bottom Info Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MugenSurfaceCard)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = character.displayName,
                        color = MugenWhitePrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFD700) else MugenTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MugenTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(MugenSurfaceCard)
                                    .border(1.dp, MugenBorderSubtle, RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("تعديل المقاتل", color = MugenWhitePrimary, fontSize = 12.sp) },
                                    onClick = { showMenu = false; onClick() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isFavorite) "إزالة من المفضلة" else "إضافة للمفضلة", color = MugenWhitePrimary, fontSize = 12.sp) },
                                    onClick = { showMenu = false; onToggleFavorite() }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "ID: ${character.selectEntry}",
                    color = MugenTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FightingCharacterListCard(
    character: CharacterData,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clip(CyberCutSmall)
            .background(MugenSurfaceCard)
            .border(1.dp, MugenRedBorderBright, CyberCutSmall)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CyberCutSmall)
                    .background(Color(0xFF0F1015))
                    .border(1.dp, MugenRedBorder, CyberCutSmall),
                contentAlignment = Alignment.Center
            ) {
                if (character.imageUri != null) {
                    AsyncImage(
                        model = character.imageUri,
                        contentDescription = character.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MugenTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = character.displayName,
                        color = MugenWhitePrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (character.workspaceStatus == CharacterWorkspaceStatus.MODIFIED) {
                        MugenStatusBadge(text = "معدلة", style = BadgeStyle.WARNING)
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "ID: ${character.selectEntry}",
                    color = MugenTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFFD700) else MugenTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BatchRestoreDialog(
    onDismiss: () -> Unit,
    onRestored: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRestoring by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<com.example.engine.workspace.BatchRestorePlan?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            plan = CharacterWorkspaceManager.planBatchRestore(context)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        containerColor = MugenSurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MugenRedPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "استعادة الخزينة الجماعية",
                    color = MugenWhitePrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (isRestoring) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MugenRedPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري استعادة ملفات الشخصيات الأصلية بدقة 100%...",
                        color = MugenTextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "هل تريد استعادة جميع الشخصيات المعدلة إلى نسختها الأصلية النقية المخزنة في الخزينة المحمية؟",
                        color = MugenTextSecondary,
                        fontSize = 13.sp
                    )
                    plan?.let { p ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "شخصيات بحاجة للاستعادة: ${p.modifiedCharacters.size}",
                            color = MugenOrangeWarning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "شخصيات نقية مطابقة: ${p.pristineCharacters.size}",
                            color = MugenGreenSuccess,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isRestoring) {
                MugenPrimaryButton(
                    text = "استعادة الكل للأصل",
                    icon = Icons.Default.Restore,
                    onClick = {
                        scope.launch {
                            isRestoring = true
                            val report = withContext(Dispatchers.IO) {
                                CharacterWorkspaceManager.executeBatchRestore(null, context)
                            }
                            isRestoring = false
                            Toast.makeText(
                                context,
                                if (report.success) "تمت استعادة ${report.restoredCharacters.size} مقاتل بنجاح" else "فشل استعادة بعض الشخصيات",
                                Toast.LENGTH_SHORT
                            ).show()
                            onRestored()
                        }
                    },
                    height = 40.dp
                )
            }
        },
        dismissButton = {
            if (!isRestoring) {
                TextButton(onClick = onDismiss) {
                    Text("إلغاء", color = MugenTextSecondary)
                }
            }
        }
    )
}

