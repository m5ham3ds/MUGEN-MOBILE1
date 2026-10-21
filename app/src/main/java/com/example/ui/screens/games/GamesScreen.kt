package com.example.ui.screens.games

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MugenApplication
import com.example.data.model.GameEntity
import com.example.storage.StorageManager
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.utils.bounceClick
import com.example.ui.viewmodels.GameLibraryViewModel
import com.example.ui.viewmodels.GameLibraryViewModelFactory
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun GamesScreen(
    hasPermission: Boolean,
    onLaunchCustomGame: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as MugenApplication
    val viewModel: GameLibraryViewModel = viewModel(
        factory = GameLibraryViewModelFactory(application.repository, application.contentRepository)
    )
    val games by viewModel.allGames.collectAsState()
    val scope = rememberCoroutineScope()

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            StorageManager.takePersistableUriPermission(context, it)
            scope.launch {
                val metadata = StorageManager.scanGameDirectory(context, it)
                viewModel.importGame(
                    title = metadata.title,
                    folderPath = it.toString(),
                    characters = metadata.characters,
                    stages = metadata.stages
                )
            }
        }
    }

    MugenFighterBackground(intensity = 0.4f) {
        if (games.isEmpty()) {
            // Empty State (Reference B)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Central Cyber Folder & Cards Art
                CyberFolderArt()

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "No custom games imported.",
                    color = MugenWhitePrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Import a game pack to build your library.",
                    color = MugenTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Big Chamfered Import Button
                MugenPrimaryButton(
                    text = "Import Full Game",
                    icon = Icons.Default.FileUpload,
                    onClick = { documentTreeLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(58.dp)
                )
            }
        } else {
            // Populated State: Grid or List of Games
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مكتبة الألعاب المخصصة (${games.size})",
                                color = MugenWhitePrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            MugenSecondaryButton(
                                text = "استيراد حزمة",
                                icon = Icons.Default.Add,
                                onClick = { documentTreeLauncher.launch(null) },
                                height = 36.dp
                            )
                        }
                    }

                    items(games) { game ->
                        CustomGameCard(
                            game = game,
                            onClick = {
                                com.example.utils.CrashReporter.recordBreadcrumb(
                                    "PLAY_BUTTON_CLICKED",
                                    "Custom game launched: " + game.title
                                )
                                val encodedPath = URLEncoder.encode(game.folderPath, StandardCharsets.UTF_8.toString())
                                onLaunchCustomGame(encodedPath)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomGameCard(game: GameEntity, onClick: () -> Unit) {
    MugenCard(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(),
        borderColor = MugenRedBorderBright,
        onClick = onClick
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
                        .size(46.dp)
                        .clip(CyberCutSmall)
                        .background(MugenSurfaceSecondary)
                        .border(1.dp, MugenRedBorder, CyberCutSmall),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        tint = MugenRedPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = game.title,
                        color = MugenWhitePrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${game.characterCount} شخصية • ${game.stageCount} مرحلة",
                        color = MugenTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CyberCutSmall)
                    .background(MugenRedPrimary)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MugenWhitePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
