package com.example.ui.screens.main

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.storage.MugenMobileStorage
import com.example.ui.components.*
import com.example.ui.screens.characters.CharactersScreen
import com.example.ui.screens.games.GamesScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.stages.StagesScreen
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MugenBgDeep
import com.example.utils.PermissionUtils
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onLaunchBuiltInGame: () -> Unit,
    onLaunchCustomGame: (String) -> Unit,
    onNavigateToControllerEditor: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var hasPermission by remember { mutableStateOf(PermissionUtils.hasStoragePermission(context)) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = PermissionUtils.hasStoragePermission(context)
        if (hasPermission) {
            MugenMobileStorage.initializeDirectories()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = PermissionUtils.hasStoragePermission(context)
        if (hasPermission) {
            MugenMobileStorage.initializeDirectories()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionUtils.requestManageStorageIntent(context)?.let {
                    manageStorageLauncher.launch(it)
                }
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        } else {
            MugenMobileStorage.initializeDirectories()
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val currentTitle = when (currentRoute) {
        "home" -> strings.play
        "characters" -> strings.characters
        "stages" -> strings.stages
        "games" -> strings.games
        "settings" -> strings.settings
        else -> strings.mugenMobile
    }

    val navItems = remember(strings) {
        listOf(
            MugenNavItem("home", strings.play, Icons.Default.PlayArrow),
            MugenNavItem("characters", strings.characters, Icons.Default.Groups),
            MugenNavItem("stages", strings.stages, Icons.Default.Map),
            MugenNavItem("games", strings.games, Icons.Default.Gamepad),
            MugenNavItem("settings", strings.settings, Icons.Default.Settings)
        )
    }

    val drawerItems = remember(strings) {
        listOf(
            MugenDrawerItem("home", strings.play, "Start the game", Icons.Default.PlayArrow),
            MugenDrawerItem("characters", strings.characters, "Manage your characters", Icons.Default.Groups),
            MugenDrawerItem("stages", strings.stages, "Browse and manage stages", Icons.Default.Map),
            MugenDrawerItem("games", strings.games, "Import and manage games", Icons.Default.Gamepad),
            MugenDrawerItem("settings", strings.settings, "App configuration", Icons.Default.Settings)
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            MugenDrawerContent(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navigateToTab(route)
                },
                onClose = {
                    scope.launch { drawerState.close() }
                },
                items = drawerItems
            )
        }
    ) {
        Scaffold(
            containerColor = MugenBgDeep,
            topBar = {
                MugenTopBar(
                    title = currentTitle,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            },
            bottomBar = {
                MugenBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navigateToTab(route)
                    },
                    items = navItems
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    androidx.compose.animation.slideInHorizontally(
                        initialOffsetX = { 300 },
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                    ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
                },
                exitTransition = {
                    androidx.compose.animation.slideOutHorizontally(
                        targetOffsetX = { -300 },
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                    ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                }
            ) {
                composable("home") {
                    HomeScreen(
                        hasPermission = hasPermission,
                        onLaunchBuiltInGame = onLaunchBuiltInGame,
                        onNavigateToDiagnostics = onNavigateToDiagnostics
                    )
                }
                composable("characters") {
                    CharactersScreen(hasPermission = hasPermission)
                }
                composable("stages") {
                    StagesScreen(hasPermission = hasPermission)
                }
                composable("games") {
                    GamesScreen(
                        hasPermission = hasPermission,
                        onLaunchCustomGame = onLaunchCustomGame
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onNavigateToControllerEditor = onNavigateToControllerEditor,
                        onNavigateToDiagnostics = onNavigateToDiagnostics,
                        onNavigateBack = {}
                    )
                }
            }
        }
    }
}
