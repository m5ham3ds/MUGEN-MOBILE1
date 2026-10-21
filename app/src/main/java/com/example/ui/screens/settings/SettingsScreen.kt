package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.settings.SettingsDataStore
import com.example.engine.DownloadState
import com.example.engine.EngineDataDownloader
import com.example.engine.gemini.GeminiApiClient
import com.example.engine.gemini.GeminiResult
import com.example.storage.MugenMobileStorage
import com.example.storage.StorageDiagnosticInfo
import com.example.storage.StorageManager
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.utils.bounceClick
import com.example.utils.GeminiKeyStoreManager
import kotlinx.coroutines.launch
import java.net.URLDecoder

@Composable
fun SettingsScreen(
    onNavigateToControllerEditor: () -> Unit,
    onNavigateToDiagnostics: (() -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current

    // Observe DataStore
    val language by remember { SettingsDataStore.getLanguage(context) }.collectAsState(initial = "en")
    val themeMode by remember { SettingsDataStore.getThemeMode(context) }.collectAsState(initial = "dark")
    val primaryColor by remember { SettingsDataStore.getPrimaryColor(context) }.collectAsState(initial = "red")

    // Authoritative game directory state
    var customGamePath by remember { mutableStateOf(MugenMobileStorage.getBaseDirPath(context)) }
    var diagnostics by remember { mutableStateOf(MugenMobileStorage.getDiagnostics(context)) }
    var showDiagnosticDetails by remember { mutableStateOf(false) }

    val downloadState by EngineDataDownloader.downloadState.collectAsState()
    val progressPercentage by EngineDataDownloader.progressPercentage.collectAsState()
    val progressMessage by EngineDataDownloader.progressMessage.collectAsState()

    // Gemini API states
    var geminiApiKeyInput by remember { mutableStateOf("") }
    var geminiApiKeyVisible by remember { mutableStateOf(false) }
    var hasGeminiKey by remember { mutableStateOf(GeminiKeyStoreManager.hasApiKey(context)) }
    var maskedGeminiKey by remember { mutableStateOf(GeminiKeyStoreManager.getMaskedStoredKey(context)) }
    var geminiStatusMessage by remember { mutableStateOf<String?>(null) }
    var geminiStatusSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTestingGemini by remember { mutableStateOf(false) }

    // Dialog state for pickers
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

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
            customGamePath = finalPath
            diagnostics = MugenMobileStorage.getDiagnostics(context)
            Toast.makeText(context, "تم اعتماد مجلد اللعبة: $finalPath", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            EngineDataDownloader.startDownload(context)
        }
    }

    LaunchedEffect(Unit) {
        customGamePath = MugenMobileStorage.getBaseDirPath(context)
        diagnostics = MugenMobileStorage.getDiagnostics(context)
        hasGeminiKey = GeminiKeyStoreManager.hasApiKey(context)
        maskedGeminiKey = GeminiKeyStoreManager.getMaskedStoredKey(context)
    }

    MugenFighterBackground(intensity = 0.35f) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================================================================
            // 1. LANGUAGE AND APPEARANCE SECTION (Reference F)
            // ================================================================
            item {
                SectionHeaderRow(
                    icon = Icons.Default.Settings,
                    title = "Language and Appearance",
                    subtitle = "اللغة والمظهر"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Row with Language and Theme Mode side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Language Card
                    SettingInteractiveCard(
                        icon = Icons.Default.Language,
                        title = "Language",
                        value = if (language == "ar") "العربية" else "English",
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.weight(1f)
                    )

                    // Theme Mode Card
                    val themeLabel = when (themeMode) {
                        "light" -> strings.light
                        "dark" -> "Dark"
                        else -> strings.systemDefault
                    }
                    SettingInteractiveCard(
                        icon = Icons.Default.DarkMode,
                        title = "Theme Mode",
                        value = themeLabel,
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Color Card
                val colorLabel = when (primaryColor) {
                    "purple" -> strings.purple
                    "blue" -> strings.blue
                    "green" -> strings.green
                    "yellow" -> strings.yellow
                    else -> "Red"
                }
                SettingInteractiveCard(
                    icon = Icons.Default.Palette,
                    title = "Primary Color",
                    value = colorLabel,
                    onClick = { showColorDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ================================================================
            // 2. GAME DIRECTORY SECTION (Reference F)
            // ================================================================
            item {
                SectionHeaderRow(
                    icon = Icons.Default.Folder,
                    title = "Game Directory",
                    subtitle = "المجلد الافتراضي",
                    action = {
                        Text(
                            text = "استعادة الافتراضي",
                            color = MugenRedPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    MugenMobileStorage.setCustomBaseDir(context, MugenMobileStorage.DEFAULT_PATH, null)
                                    customGamePath = MugenMobileStorage.DEFAULT_PATH
                                    diagnostics = MugenMobileStorage.getDiagnostics(context)
                                    Toast.makeText(context, "تمت استعادة المجلد الافتراضي", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                MugenCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CyberCutMedium,
                    borderColor = MugenRedBorderBright
                ) {
                    // Top Row: Folder Icon + Path + Status Badge & Change Path Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CyberCutSmall)
                                    .background(Color(0xFF2E0C10))
                                    .border(1.dp, MugenRedBorder, CyberCutSmall),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MugenRedPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Game Directory",
                                    color = MugenWhitePrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = customGamePath,
                                    color = MugenTextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right Column: Status pill + Change Path Red Button
                        Column(horizontalAlignment = Alignment.End) {
                            val isAccessOk = diagnostics.isAccessSuccessful
                            Box(
                                modifier = Modifier
                                    .clip(CyberCutSmall)
                                    .background(if (isAccessOk) Color(0xFF132B1A) else Color(0xFF381014))
                                    .border(1.dp, if (isAccessOk) MugenGreenSuccess else MugenRedPrimary, CyberCutSmall)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isAccessOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (isAccessOk) MugenGreenSuccess else MugenRedPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAccessOk) "جاهز ومتاح" else "بحاجة لصلاحية",
                                        color = if (isAccessOk) MugenGreenSuccess else MugenRedPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .bounceClick()
                                    .clip(CyberCutSmall)
                                    .background(Color(0xFF8A0E13))
                                    .border(1.dp, MugenRedGlow, CyberCutSmall)
                                    .clickable { documentTreeLauncher.launch(null) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MugenWhitePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "تغيير المسار",
                                        color = MugenWhitePrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MugenBorderSubtle, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Row: Stats & Diagnostics Test button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = MugenRedPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${diagnostics.characterCount} شخصيات  |  ${diagnostics.stageCount} مراحل مكتشفة",
                                color = MugenWhitePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showDiagnosticDetails = !showDiagnosticDetails }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MugenRedPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showDiagnosticDetails) "إخفاء التفاصيل" else "فحص التشخيص",
                                color = MugenRedPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MugenRedPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (showDiagnosticDetails) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MugenBgDeep)
                                .border(1.dp, MugenBorderSubtle, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "المسار الفعلي: ${diagnostics.actualPathUsed}",
                                    color = MugenTextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (!diagnostics.configuredUri.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "SAF URI: ${diagnostics.configuredUri}",
                                        color = MugenTextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (diagnostics.failureReason != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "السبب: ${diagnostics.failureReason}",
                                        color = MugenRedPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 3. BASE ENGINE DATA SECTION (Reference F)
            // ================================================================
            item {
                SectionHeaderRow(
                    icon = Icons.Default.Download,
                    title = "Base Engine Data",
                    subtitle = strings.downloadEngineDescription
                )

                Spacer(modifier = Modifier.height(8.dp))

                MugenCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CyberCutMedium,
                    borderColor = MugenRedBorderBright
                ) {
                    if (downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.EXTRACTING || downloadState == DownloadState.PAUSED) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = progressMessage,
                                color = MugenWhitePrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (downloadState == DownloadState.DOWNLOADING && progressPercentage > 0f) {
                                LinearProgressIndicator(
                                    progress = { progressPercentage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MugenRedPrimary,
                                    trackColor = MugenSurfaceSecondary
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MugenRedPrimary,
                                    trackColor = MugenSurfaceSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (downloadState == DownloadState.PAUSED) {
                                    MugenSecondaryButton(
                                        text = "استئناف",
                                        icon = Icons.Default.PlayArrow,
                                        onClick = { EngineDataDownloader.startDownload(context) },
                                        height = 36.dp
                                    )
                                } else {
                                    MugenSecondaryButton(
                                        text = "إيقاف مؤقت",
                                        icon = Icons.Default.Pause,
                                        onClick = { EngineDataDownloader.pauseDownload() },
                                        height = 36.dp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                MugenSecondaryButton(
                                    text = "إلغاء",
                                    icon = Icons.Default.Close,
                                    onClick = { EngineDataDownloader.cancelDownload() },
                                    borderColor = MugenBorderSubtle,
                                    height = 36.dp
                                )
                            }
                        }
                    } else {
                        // Big Chamfered Red Download Button (Reference F)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .bounceClick()
                                .clip(CyberCutSmall)
                                .background(MugenRedButtonVerticalGradient)
                                .border(1.5.dp, MugenRedGlow, CyberCutSmall)
                                .clickable {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        EngineDataDownloader.startDownload(context)
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MugenWhitePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Download Base Data",
                                        color = MugenWhitePrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MugenWhitePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (downloadState == DownloadState.SUCCESS || downloadState == DownloadState.ERROR) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = progressMessage,
                                color = if (downloadState == DownloadState.ERROR) MugenRedPrimary else MugenGreenSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ================================================================
            // 4. CONTROLS SECTION (Reference F)
            // ================================================================
            item {
                SectionHeaderRow(
                    icon = Icons.Default.SportsEsports,
                    title = "Controls",
                    subtitle = null
                )

                Spacer(modifier = Modifier.height(8.dp))

                MugenCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(),
                    shape = CyberCutMedium,
                    borderColor = MugenRedBorderBright,
                    onClick = onNavigateToControllerEditor
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CyberCutSmall)
                                    .background(Color(0xFF2E0C10))
                                    .border(1.dp, MugenRedBorder, CyberCutSmall),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideogameAsset,
                                    contentDescription = null,
                                    tint = MugenRedPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Edit Controller Layout",
                                    color = MugenWhitePrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Customize on-screen buttons for MUGEN",
                                    color = MugenTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MugenTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ================================================================
            // 5. GOOGLE GEMINI AI SECTION (Reference F)
            // ================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MugenRedPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Gemini AI",
                            color = MugenRedPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Key Status Badge
                    Box(
                        modifier = Modifier
                            .clip(CyberCutSmall)
                            .background(if (hasGeminiKey) Color(0xFF132B1A) else Color(0xFF262730))
                            .border(1.dp, if (hasGeminiKey) MugenGreenSuccess else MugenBorderSubtle, CyberCutSmall)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasGeminiKey) Icons.Default.Lock else Icons.Default.KeyOff,
                                contentDescription = null,
                                tint = if (hasGeminiKey) MugenGreenSuccess else MugenTextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasGeminiKey) "Key securely saved" else strings.noKeyAdded,
                                color = if (hasGeminiKey) MugenGreenSuccess else MugenTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                MugenCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CyberCutMedium,
                    borderColor = MugenRedBorderBright
                ) {
                    // Stored Masked Key Row
                    if (hasGeminiKey && !maskedGeminiKey.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "المفتاح المحفوظ المشفر",
                                    color = MugenTextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = maskedGeminiKey ?: "",
                                    color = MugenRedGlow,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            // Delete Key Button
                            Box(
                                modifier = Modifier
                                    .bounceClick()
                                    .clip(CyberCutSmall)
                                    .background(Color(0xFF8A0E13))
                                    .border(1.dp, MugenRedGlow, CyberCutSmall)
                                    .clickable {
                                        GeminiKeyStoreManager.deleteApiKey(context)
                                        hasGeminiKey = false
                                        maskedGeminiKey = null
                                        geminiApiKeyInput = ""
                                        geminiStatusMessage = strings.keyDeleted
                                        geminiStatusSuccess = null
                                        Toast.makeText(context, strings.keyDeleted, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MugenWhitePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete Key",
                                        color = MugenWhitePrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MugenBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Key Input Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(CyberCutSmall)
                            .background(MugenBgDeep)
                            .border(1.dp, MugenRedBorder, CyberCutSmall)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MugenTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (geminiApiKeyInput.isEmpty()) {
                                    Text(
                                        text = "Personal Gemini API Key",
                                        color = MugenTextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = geminiApiKeyInput,
                                    onValueChange = { geminiApiKeyInput = it },
                                    textStyle = TextStyle(
                                        color = MugenWhitePrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    visualTransformation = if (geminiApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    cursorBrush = SolidColor(MugenRedPrimary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            IconButton(
                                onClick = { geminiApiKeyVisible = !geminiApiKeyVisible },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (geminiApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MugenTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons Row: Save Key & Test Connection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Save Key
                        MugenSecondaryButton(
                            text = strings.saveKey,
                            icon = Icons.Default.VpnKey,
                            onClick = {
                                if (geminiApiKeyInput.isNotBlank()) {
                                    val saved = GeminiKeyStoreManager.saveApiKey(context, geminiApiKeyInput)
                                    if (saved) {
                                        hasGeminiKey = true
                                        maskedGeminiKey = GeminiKeyStoreManager.getMaskedStoredKey(context)
                                        geminiApiKeyInput = ""
                                        geminiApiKeyVisible = false
                                        geminiStatusMessage = strings.keySaved
                                        geminiStatusSuccess = true
                                        Toast.makeText(context, strings.keySaved, Toast.LENGTH_SHORT).show()
                                    } else {
                                        geminiStatusMessage = "فشل تشفير المفتاح وتخزينه"
                                        geminiStatusSuccess = false
                                        Toast.makeText(context, "فشل تشفير المفتاح وتخزينه", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, strings.enterGeminiApiKey, Toast.LENGTH_SHORT).show()
                                }
                            },
                            borderColor = if (geminiApiKeyInput.isNotBlank()) MugenRedBorderBright else MugenBorderSubtle,
                            containerColor = MugenSurfaceSecondary,
                            modifier = Modifier.weight(1f),
                            enabled = geminiApiKeyInput.isNotBlank(),
                            height = 44.dp
                        )

                        // Test Connection
                        MugenSecondaryButton(
                            text = strings.testConnection,
                            icon = Icons.Default.WifiTethering,
                            onClick = {
                                scope.launch {
                                    isTestingGemini = true
                                    geminiStatusMessage = strings.testingConnection
                                    geminiStatusSuccess = null
                                    val keyToTest = if (geminiApiKeyInput.isNotBlank()) geminiApiKeyInput.trim() else GeminiKeyStoreManager.getApiKey(context)
                                    if (keyToTest.isNullOrBlank()) {
                                        isTestingGemini = false
                                        geminiStatusMessage = strings.noKeyAdded
                                        geminiStatusSuccess = false
                                        return@launch
                                    }
                                    val res = GeminiApiClient.testConnection(keyToTest)
                                    isTestingGemini = false
                                    when (res) {
                                        is GeminiResult.Success -> {
                                            geminiStatusMessage = "${strings.connectionSuccess}: ${res.data}"
                                            geminiStatusSuccess = true
                                        }
                                        is GeminiResult.Error -> {
                                            geminiStatusMessage = "${strings.connectionFailed}: ${res.message}"
                                            geminiStatusSuccess = false
                                        }
                                    }
                                }
                            },
                            borderColor = MugenRedBorderBright,
                            containerColor = MugenSurfaceSecondary,
                            contentColor = MugenRedPrimary,
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingGemini && (geminiApiKeyInput.isNotBlank() || hasGeminiKey),
                            height = 44.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Create Gemini API Key Button (Reference F)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .bounceClick()
                            .clip(CyberCutSmall)
                            .background(Color(0xFF260A0D))
                            .border(1.dp, MugenRedBorderBright, CyberCutSmall)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                context.startActivity(intent)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MugenRedGlow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create Gemini API Key",
                                color = MugenWhitePrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (geminiStatusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (geminiStatusSuccess == true) Color(0xFF132B1A) else Color(0xFF381014)
                                )
                                .border(
                                    1.dp,
                                    if (geminiStatusSuccess == true) MugenGreenSuccess else MugenRedPrimary,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = geminiStatusMessage ?: "",
                                color = if (geminiStatusSuccess == true) MugenGreenSuccess else MugenRedGlow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Security & Disclaimer Card (Reference F)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0E0F14))
                            .border(1.dp, MugenBorderSubtle, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MugenTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Your API key is stored locally on this device only using hardware encryption (AES-256-GCM) and excluded from backups. AI requests are sent directly to Google using your personal key, and usage quotas are governed by your Google account.",
                                color = MugenTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // ================================================================
            // 6. LIVE DIAGNOSTICS AND MONITORING SECTION (Reference F)
            // ================================================================
            item {
                SectionHeaderRow(
                    icon = Icons.Default.Terminal,
                    title = "التشخيص والمراقبة الحية",
                    subtitle = null
                )

                Spacer(modifier = Modifier.height(8.dp))

                MugenCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(),
                    shape = CyberCutMedium,
                    borderColor = MugenRedBorderBright,
                    onClick = { onNavigateToDiagnostics?.invoke() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CyberCutSmall)
                                    .background(Color(0xFF2E0C10))
                                    .border(1.dp, MugenRedBorder, CyberCutSmall),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MugenRedPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "منظومة السجل الحي و Logcat",
                                    color = MugenWhitePrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "عرض سجل mugen_live.log، تدفق السجل الحي، المباشر، وتصدير التقارير",
                                    color = MugenTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MugenTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // --- SELECTION DIALOGS ---

    // 1. Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = MugenSurfaceCard,
            title = {
                Text(
                    text = strings.language,
                    color = MugenWhitePrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogOptionRow(
                        label = strings.english,
                        isSelected = language == "en",
                        onClick = {
                            scope.launch { SettingsDataStore.setLanguage(context, "en") }
                            showLanguageDialog = false
                        }
                    )
                    DialogOptionRow(
                        label = strings.arabic,
                        isSelected = language == "ar",
                        onClick = {
                            scope.launch { SettingsDataStore.setLanguage(context, "ar") }
                            showLanguageDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.close, color = MugenTextSecondary)
                }
            }
        )
    }

    // 2. Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = MugenSurfaceCard,
            title = {
                Text(
                    text = strings.themeMode,
                    color = MugenWhitePrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogOptionRow(
                        label = strings.dark,
                        isSelected = themeMode == "dark",
                        onClick = {
                            scope.launch { SettingsDataStore.setThemeMode(context, "dark") }
                            showThemeDialog = false
                        }
                    )
                    DialogOptionRow(
                        label = strings.light,
                        isSelected = themeMode == "light",
                        onClick = {
                            scope.launch { SettingsDataStore.setThemeMode(context, "light") }
                            showThemeDialog = false
                        }
                    )
                    DialogOptionRow(
                        label = strings.systemDefault,
                        isSelected = themeMode == "system",
                        onClick = {
                            scope.launch { SettingsDataStore.setThemeMode(context, "system") }
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(strings.close, color = MugenTextSecondary)
                }
            }
        )
    }

    // 3. Primary Color Dialog
    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            containerColor = MugenSurfaceCard,
            title = {
                Text(
                    text = strings.primaryColor,
                    color = MugenWhitePrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("red", "purple", "blue", "green", "yellow").forEach { colorKey ->
                        val name = when (colorKey) {
                            "purple" -> strings.purple
                            "blue" -> strings.blue
                            "green" -> strings.green
                            "yellow" -> strings.yellow
                            else -> strings.red
                        }
                        DialogOptionRow(
                            label = name,
                            isSelected = primaryColor == colorKey,
                            onClick = {
                                scope.launch { SettingsDataStore.setPrimaryColor(context, colorKey) }
                                showColorDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showColorDialog = false }) {
                    Text(strings.close, color = MugenTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SectionHeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MugenRedPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = MugenRedPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MugenTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
private fun SettingInteractiveCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .bounceClick()
            .clip(CyberCutSmall)
            .background(MugenSurfaceCard)
            .border(1.dp, MugenBorderSubtle, CyberCutSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MugenSurfaceSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MugenWhitePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = MugenWhitePrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = value,
                        color = MugenTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MugenTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DialogOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CyberCutSmall)
            .background(if (isSelected) Color(0xFF6B1218) else MugenSurfaceSecondary)
            .border(1.dp, if (isSelected) MugenRedGlow else MugenBorderSubtle, CyberCutSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MugenWhitePrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MugenWhitePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
