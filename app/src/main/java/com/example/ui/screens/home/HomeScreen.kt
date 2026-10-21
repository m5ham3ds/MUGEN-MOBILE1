package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.DownloadState
import com.example.engine.EngineDataDownloader
import com.example.engine.NativeEngineLoader
import com.example.storage.MugenMobileStorage
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File

@Composable
fun HomeScreen(
    hasPermission: Boolean,
    onLaunchBuiltInGame: () -> Unit,
    onNavigateToDiagnostics: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var showLogs by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    var showMissingDataDialog by remember { mutableStateOf(false) }
    var showMissingEngineDialog by remember { mutableStateOf(false) }

    val downloadState by EngineDataDownloader.downloadState.collectAsState()
    val progressMessage by EngineDataDownloader.progressMessage.collectAsState()
    val progressPercentage by EngineDataDownloader.progressPercentage.collectAsState()

    fun triggerLaunch() {
        com.example.utils.CrashReporter.recordBreadcrumb("PLAY_BUTTON_CLICKED", "Built-in MUGEN / IKEMEN-GO launch requested")
        val sharedPrefs = context.getSharedPreferences("IkemenGo", android.content.Context.MODE_PRIVATE)
        val basePath = MugenMobileStorage.getBaseDir(context).absolutePath
        sharedPrefs.edit().putString("folder", basePath).apply()

        // Ensure characters are synchronized to data/select.def before launching engine
        try {
            val chars = com.example.engine.MugenParser.getCharacters(context)
            android.util.Log.i("HomeScreen", "Pre-launch character synchronization completed: ${chars.size} characters found")
        } catch (e: Exception) {
            android.util.Log.w("HomeScreen", "Non-fatal pre-launch sync notice: ${e.message}")
        }

        onLaunchBuiltInGame()
    }

    MugenFighterBackground(intensity = 0.5f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Permission alert or spacer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPermission) {
                    MugenCard(
                        borderColor = MugenRedBorderBright,
                        containerColor = Color(0xFF381014)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MugenRedGlow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = strings.storagePermissionRequired,
                                color = MugenWhitePrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Center Calligraphy / Hero Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 40.dp)
            ) {
                // Large aggressive "Ready to Fight!" title
                val calloutText = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = MugenWhitePrimary,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.sp
                        )
                    ) {
                        append("Ready to ")
                    }
                    withStyle(
                        style = SpanStyle(
                            color = MugenRedGlow,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp
                        )
                    ) {
                        append("Fight!")
                    }
                }

                Text(
                    text = calloutText,
                    textAlign = TextAlign.Center,
                    lineHeight = 44.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MugenRedPrimary)
                )
            }

            // Bottom Actions Cluster
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active download indicator card if in progress
                if (downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.EXTRACTING) {
                    MugenCard(
                        modifier = Modifier.fillMaxWidth(0.92f),
                        borderColor = MugenRedBorderBright
                    ) {
                        Text(
                            text = progressMessage,
                            color = MugenWhitePrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
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
                    }
                }

                // Primary START Button (Reference A)
                MugenPrimaryButton(
                    text = strings.start,
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        com.example.utils.LiveLogger.i(
                            com.example.utils.LiveLogger.Category.ENGINE,
                            "START_BUTTON_PRESSED",
                            "START button pressed on HomeScreen"
                        )
                        com.example.utils.CrashReporter.recordBreadcrumb("PLAY_BUTTON_CLICKED", "START button pressed on HomeScreen")
                        val isDataInstalled = MugenMobileStorage.isEngineDataInstalled(context)
                        val hasEngine = NativeEngineLoader.hasEngineFiles(context)

                        if (!isDataInstalled) {
                            com.example.utils.LiveLogger.w(com.example.utils.LiveLogger.Category.ENGINE, "START_HALTED", "Base engine data not installed")
                            showMissingDataDialog = true
                        } else if (!hasEngine) {
                            com.example.utils.LiveLogger.w(com.example.utils.LiveLogger.Category.ENGINE, "START_HALTED", "Native engine libmain.so missing")
                            showMissingEngineDialog = true
                        } else {
                            triggerLaunch()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(64.dp)
                )

                // Secondary HUD Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onNavigateToDiagnostics != null) {
                        MugenSecondaryButton(
                            text = "التشخيص والسجلات الحية",
                            icon = Icons.Default.MonitorHeart,
                            onClick = onNavigateToDiagnostics,
                            borderColor = MugenRedBorderBright,
                            containerColor = MugenSurfaceCard,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    MugenSecondaryButton(
                        text = strings.viewCrashLogs,
                        icon = Icons.Default.Article,
                        onClick = {
                            val logFile = File(MugenMobileStorage.getBaseDir(context), "mugen_crash.log")
                            logContent = if (logFile.exists()) {
                                val lines = logFile.readLines()
                                lines.takeLast(150).joinToString("\n")
                            } else {
                                strings.noCrashLogs
                            }
                            showLogs = true
                        },
                        borderColor = MugenBorderSubtle,
                        containerColor = MugenSurfaceCard,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // --- DIALOGS (Themed with Red & Black HUD) ---

    // Missing Base Data Dialog
    if (showMissingDataDialog) {
        Dialog(
            onDismissRequest = { showMissingDataDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(CyberCutMedium)
                    .background(MugenSurfaceCard)
                    .border(1.5.dp, MugenRedBorderBright, CyberCutMedium)
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = MugenRedPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.baseEngineData, color = MugenWhitePrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(strings.downloadEngineDescription, color = MugenTextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            showMissingDataDialog = false
                            triggerLaunch()
                        }) {
                            Text(strings.start, color = MugenTextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        MugenPrimaryButton(
                            text = strings.downloadBaseData,
                            onClick = {
                                showMissingDataDialog = false
                                EngineDataDownloader.startDownload(context)
                            },
                            height = 42.dp
                        )
                    }
                }
            }
        }
    }

    // Missing Engine Dialog
    if (showMissingEngineDialog) {
        Dialog(
            onDismissRequest = { showMissingEngineDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(CyberCutMedium)
                    .background(MugenSurfaceCard)
                    .border(1.5.dp, MugenRedBorderBright, CyberCutMedium)
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MugenRedGlow, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Native Engine Missing", color = MugenWhitePrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "IKEMEN-GO engine binary (libmain.so) was not detected in the installed application package. Launch is halted. Please reinstall the APK.",
                        color = MugenTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MugenSecondaryButton(
                            text = strings.close,
                            onClick = { showMissingEngineDialog = false },
                            height = 40.dp
                        )
                    }
                }
            }
        }
    }

    // Crash Logs Dialog
    if (showLogs) {
        Dialog(
            onDismissRequest = { showLogs = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clip(CyberCutMedium)
                    .background(MugenSurfaceCard)
                    .border(1.5.dp, MugenRedBorderBright, CyberCutMedium)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Article, contentDescription = null, tint = MugenRedPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.crashLogs, color = MugenWhitePrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showLogs = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MugenWhitePrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MugenBgDeep)
                            .border(1.dp, MugenBorderSubtle, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = logContent,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            color = Color(0xFFD6D7E0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MugenSecondaryButton(
                            text = strings.close,
                            onClick = { showLogs = false },
                            height = 40.dp
                        )
                    }
                }
            }
        }
    }
}
