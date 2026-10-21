package com.example.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.MugenMobileStorage
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.utils.CrashReporter
import com.example.utils.LiveLogger
import com.example.utils.LogcatEntry
import com.example.utils.LogcatStreamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DiagnosticsTab(val label: String) {
    LIVE_LOG("السجل الحي (mugen_live.log)"),
    LOGCAT("عارض Logcat المباشر"),
    SYSTEM("بيئة النظام والملفات")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(DiagnosticsTab.LIVE_LOG) }

    Scaffold(
        containerColor = MugenBgDeep,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(58.dp)
                    .background(MugenTopBarGradient)
                    .mugenTopBarDivider()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MugenWhitePrimary
                    )
                }

                Text(
                    text = "منظومة التشخيص والمراقبة",
                    color = MugenWhitePrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MugenSurfaceCard,
                contentColor = MugenWhitePrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = MugenRedPrimary,
                        height = 3.dp
                    )
                }
            ) {
                DiagnosticsTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MugenWhitePrimary else MugenTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                DiagnosticsTab.LIVE_LOG -> LiveLogView(context)
                DiagnosticsTab.LOGCAT -> LogcatView(context)
                DiagnosticsTab.SYSTEM -> SystemDiagnosticView(context)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLogView(context: Context) {
    val scope = rememberCoroutineScope()
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedLevel by remember { mutableStateOf("ALL") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    fun loadLogs() {
        scope.launch {
            isLoading = true
            val lines = withContext(Dispatchers.IO) {
                LiveLogger.readRecentLines(context, 1000)
            }
            logLines = lines
            isLoading = false
            if (lines.isNotEmpty()) {
                listState.scrollToItem(lines.size - 1)
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    val filteredLines = remember(logLines, selectedLevel, selectedCategory, searchQuery) {
        logLines.filter { line ->
            val matchesLevel = when (selectedLevel) {
                "ALL" -> true
                "INFO" -> line.contains(" [I] ")
                "DEBUG" -> line.contains(" [D] ")
                "WARN" -> line.contains(" [W] ")
                "ERROR" -> line.contains(" [E] ") || line.contains(" [F] ")
                else -> true
            }
            val matchesCategory = when (selectedCategory) {
                "ALL" -> true
                else -> line.contains(" [$selectedCategory] ")
            }
            val matchesSearch = searchQuery.isBlank() || line.contains(searchQuery, ignoreCase = true)
            matchesLevel && matchesCategory && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Toolbar actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "سجلات دورة الحياة (${filteredLines.size} / ${logLines.size})",
                color = MugenWhitePrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { loadLogs() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MugenWhitePrimary)
                }

                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("LiveLog", filteredLines.joinToString("\n")))
                    Toast.makeText(context, "تم نسخ السجل إلى الحافظة", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = MugenWhitePrimary)
                }

                IconButton(onClick = {
                    val uri = LiveLogger.getShareableUri(context)
                    if (uri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة سجل التشخيص الحي"))
                    } else {
                        Toast.makeText(context, "ملف السجل فارغ أو غير متاح", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "تصدير ومشاركة", tint = MugenWhitePrimary)
                }

                IconButton(onClick = {
                    LiveLogger.clearLogs(context)
                    loadLogs()
                    Toast.makeText(context, "تم مسح السجل", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "مسح", tint = MugenRedPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Search & Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("بحث في سجل التطبيق الحي...", color = MugenTextMuted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MugenTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = MugenTextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MugenWhitePrimary,
                unfocusedTextColor = MugenWhitePrimary,
                focusedBorderColor = MugenRedPrimary,
                unfocusedBorderColor = MugenBorderSubtle
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Filter chips row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "INFO", "DEBUG", "WARN", "ERROR").forEach { lvl ->
                FilterChip(
                    selected = selectedLevel == lvl,
                    onClick = { selectedLevel = lvl },
                    label = { Text(lvl, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MugenRedPrimary,
                        selectedLabelColor = MugenWhitePrimary,
                        containerColor = MugenSurfaceCard,
                        labelColor = MugenTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedLevel == lvl,
                        borderColor = if (selectedLevel == lvl) MugenRedGlow else MugenBorderSubtle
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log display area (Cyber Terminal Box)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF090A0D))
                .border(1.dp, MugenBorderSubtle, RoundedCornerShape(6.dp))
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MugenRedPrimary)
                }
            } else if (filteredLines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد أحداث مطابقة في السجل الحي", color = MugenTextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    items(filteredLines) { line ->
                        val lineTextColor = when {
                            line.contains(" [F] ") || line.contains(" [E] ") -> Color(0xFFFF5252)
                            line.contains(" [W] ") -> Color(0xFFFFB74D)
                            line.contains(" [I] ") -> Color(0xFF81C784)
                            else -> Color(0xFFD6D7E0)
                        }
                        Text(
                            text = line,
                            color = lineTextColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatView(context: Context) {
    val entries by LogcatStreamer.entries.collectAsState()
    val isStreaming by LogcatStreamer.isStreaming.collectAsState()

    var selectedLevel by remember { mutableStateOf("ALL") }
    var selectedTag by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        LogcatStreamer.startStreaming()
        onDispose {}
    }

    LaunchedEffect(entries.size, autoScroll) {
        if (autoScroll && entries.isNotEmpty()) {
            listState.scrollToItem(entries.size - 1)
        }
    }

    val filteredEntries = remember(entries, selectedLevel, selectedTag, searchQuery) {
        entries.filter { entry ->
            val matchesLevel = when (selectedLevel) {
                "ALL" -> true
                "V" -> entry.level == "V"
                "D" -> entry.level == "D"
                "I" -> entry.level == "I"
                "W" -> entry.level == "W"
                "E" -> entry.level == "E" || entry.level == "F"
                else -> true
            }
            val matchesTag = when (selectedTag) {
                "ALL" -> true
                "IKEMEN" -> entry.raw.contains("ikemen", ignoreCase = true) || entry.tag.contains("ikemen", ignoreCase = true)
                "SDL" -> entry.raw.contains("SDL", ignoreCase = true) || entry.tag.contains("SDL", ignoreCase = true)
                "MUGEN" -> entry.raw.contains("MUGEN", ignoreCase = true) || entry.tag.contains("MUGEN", ignoreCase = true)
                "CRASH" -> entry.raw.contains("crash", ignoreCase = true) || entry.level == "E" || entry.level == "F"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() || entry.raw.contains(searchQuery, ignoreCase = true)
            matchesLevel && matchesTag && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isStreaming) Color(0xFF132B1A) else Color(0xFF381014))
                        .border(1.dp, if (isStreaming) MugenGreenSuccess else MugenRedPrimary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isStreaming) "مباشر LIVE" else "متوقف مؤقتاً",
                        color = if (isStreaming) MugenGreenSuccess else MugenRedPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Logcat (${filteredEntries.size} سطر)",
                    color = MugenWhitePrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                    if (isStreaming) LogcatStreamer.pauseStreaming() else LogcatStreamer.resumeStreaming()
                }) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Pause/Resume",
                        tint = MugenWhitePrimary
                    )
                }

                IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(
                        imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignCenter,
                        contentDescription = "Auto Scroll",
                        tint = if (autoScroll) MugenRedPrimary else MugenTextMuted
                    )
                }

                IconButton(onClick = {
                    val uri = LogcatStreamer.exportLogcat(context)
                    if (uri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة سجل Logcat"))
                    } else {
                        Toast.makeText(context, "لا توجد أسطر لتصديرها", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "تصدير", tint = MugenWhitePrimary)
                }

                IconButton(onClick = { LogcatStreamer.clearBuffer() }) {
                    Icon(Icons.Default.ClearAll, contentDescription = "تفريغ المخزن", tint = MugenWhitePrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("بحث في تدفق Logcat الحقيقي...", color = MugenTextMuted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MugenTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = MugenTextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MugenWhitePrimary,
                unfocusedTextColor = MugenWhitePrimary,
                focusedBorderColor = MugenRedPrimary,
                unfocusedBorderColor = MugenBorderSubtle
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Tag filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "IKEMEN", "SDL", "MUGEN", "CRASH").forEach { tag ->
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = { selectedTag = tag },
                    label = { Text(tag, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MugenRedPrimary,
                        selectedLabelColor = MugenWhitePrimary,
                        containerColor = MugenSurfaceCard,
                        labelColor = MugenTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedTag == tag,
                        borderColor = if (selectedTag == tag) MugenRedGlow else MugenBorderSubtle
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logcat Terminal Output
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF090A0D))
                .border(1.dp, MugenBorderSubtle, RoundedCornerShape(6.dp))
        ) {
            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("بانتظار وصول رسائل Logcat...", color = MugenTextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val lineColor = when (entry.level) {
                            "E", "F" -> Color(0xFFFF5252)
                            "W" -> Color(0xFFFFB74D)
                            "I" -> Color(0xFF64B5F6)
                            "V" -> Color(0xFF9E9E9E)
                            else -> Color(0xFFD6D7E0)
                        }
                        Text(
                            text = entry.raw,
                            color = lineColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SystemDiagnosticView(context: Context) {
    val diag = remember { MugenMobileStorage.getDiagnostics(context) }
    val latestCrash = remember { CrashReporter.getUnreviewedCrashReport(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "بيئة التشغيل والنظام",
                color = MugenWhitePrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            MugenCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MugenBorderSubtle
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("معلومات الجلسة والعملية الحالية:", color = MugenRedPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("معرف الجلسة: ${LiveLogger.getSessionId()}", color = MugenTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("رقم العملية (PID): ${LiveLogger.getPid()}", color = MugenTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("مسار التخزين القياسي: ${context.filesDir.absolutePath}", color = MugenTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("مسار ملف السجل الحي: ${LiveLogger.getLogFile(context).absolutePath}", color = MugenTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }

        item {
            MugenCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MugenRedBorderBright
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("حالة مسار لعبة MUGEN MOBILE:", color = MugenRedPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("المسار المستهدف: ${MugenMobileStorage.getBaseDirPath(context)}", color = MugenTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("المسار الفعلي المستخدم: ${diag.actualPathUsed}", color = MugenTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("عدد الشخصيات المكتشفة: ${diag.characterCount}", color = MugenWhitePrimary, fontSize = 12.sp)
                    Text(
                        text = "حالة الوصول: ${if (diag.isAccessSuccessful) "ناجح ومتاح ✓" else "بحاجة لصلاحية ✗"}",
                        color = if (diag.isAccessSuccessful) MugenGreenSuccess else MugenRedPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (diag.failureReason != null) {
                        Text("السبب: ${diag.failureReason}", color = MugenRedPrimary, fontSize = 11.sp)
                    }
                }
            }
        }

        if (latestCrash != null) {
            item {
                MugenCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MugenRedGlow,
                    containerColor = Color(0xFF2E0C10)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("آخر تقرير عطل تم تسجيله:", fontWeight = FontWeight.Bold, color = MugenRedGlow, fontSize = 14.sp)
                        Text("النوع: ${latestCrash.errorType}", color = MugenWhitePrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        Text("الرسالة: ${latestCrash.errorMessage}", color = MugenTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                        Text("المرحلة: ${latestCrash.launchStage}", color = MugenTextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        MugenPrimaryButton(
                            text = "فتح التقرير في شاشة فحص الأعطال",
                            onClick = { CrashReporter.launchCrashActivity(context, latestCrash) },
                            height = 42.dp
                        )
                    }
                }
            }
        }
    }
}
