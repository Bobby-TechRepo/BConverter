package io.bsoft.bconverter.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.bsoft.bconverter.domain.share.ShareAction
import io.bsoft.bconverter.feature.batch.BatchConvertScreen
import io.bsoft.bconverter.feature.share.ShareContent
import io.bsoft.bconverter.feature.share.ShareViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import io.bsoft.bconverter.feature.audioextractor.AudioExtractorScreen
import io.bsoft.bconverter.feature.converter.ConverterScreen
import io.bsoft.bconverter.feature.home.HomeDestination
import io.bsoft.bconverter.feature.home.HomeScreen
import io.bsoft.bconverter.feature.home.HomeViewModel
import io.bsoft.bconverter.feature.editor.EditorRoute
import io.bsoft.bconverter.feature.about.AboutRoute
import io.bsoft.bconverter.feature.placeholder.PlaceholderScreen
import io.bsoft.bconverter.feature.settings.SettingsRoute
import io.bsoft.bconverter.feature.projects.ProjectsRoute
import io.bsoft.bconverter.feature.projects.ProjectsViewModel

private const val HOME_ROUTE = "home"
private const val PROJECT_ARG = "project"

/** The editor; an optional project id reopens a saved project. */
private const val EDITOR_ROUTE = "editor?$PROJECT_ARG={$PROJECT_ARG}"

/** Step 25: converting several videos at once. */
private const val BATCH_ROUTE = "batch"
/** Step 25: "Open with BConverter" for videos shared from another app. */
private const val SHARE_ROUTE = "share"
private const val TOOL_ARG = "tool"
/** A tool opened on a share; it takes the shared files once ([io.bsoft.bconverter.domain.share.ShareAction] name). */
private const val SHARED_TOOL_ROUTE = "shared/{$TOOL_ARG}"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    // Activity-wide: a share can arrive at any time (cold start or while the app is open) and opens the share screen.
    val share: ShareViewModel = hiltViewModel()
    val pendingShare by share.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShare?.id) {
        if (pendingShare != null) navController.navigate(SHARE_ROUTE) { launchSingleTop = true }
    }
    NavHost(navController, startDestination = HOME_ROUTE) {
        composable(SHARE_ROUTE) {
            val names by share.names.collectAsStateWithLifecycle()
            val leave: () -> Unit = { share.clear(); navController.popBackStack() }
            BackHandler(onBack = leave)
            // While the screen leaves (the share has just been taken) it keeps showing what was shared.
            var lastUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
            LaunchedEffect(pendingShare) { pendingShare?.let { lastUris = it.uris } }
            ShareContent(
                uris = pendingShare?.uris ?: lastUris,
                names = names,
                onAction = { action ->
                    navController.navigate("shared/${action.name}") { popUpTo(SHARE_ROUTE) { inclusive = true } }
                },
                onBack = leave,
            )
        }
        composable(SHARED_TOOL_ROUTE, arguments = listOf(navArgument(TOOL_ARG) { type = NavType.StringType })) { entry ->
            val tool = entry.arguments?.getString(TOOL_ARG)?.let { runCatching { ShareAction.valueOf(it) }.getOrNull() }
            // Taken once per visit; after the activity is recreated the tool's ViewModel already has the files, so null is right.
            var shared by remember(entry) { mutableStateOf<List<Uri>?>(null) }
            LaunchedEffect(entry) { shared = share.take() }
            val back: () -> Unit = { navController.popBackStack() }
            when (tool) {
                ShareAction.EDIT -> EditorRoute(projectId = null, onBack = back, sharedVideos = shared)
                ShareAction.CONVERT -> ConverterScreen(back, onBatch = { navController.navigate(BATCH_ROUTE) }, sharedInput = shared?.firstOrNull())
                ShareAction.CONVERT_ALL -> BatchConvertScreen(back, sharedInputs = shared)
                ShareAction.EXTRACT_AUDIO -> AudioExtractorScreen(back, sharedInput = shared?.firstOrNull())
                ShareAction.EXTRACT_SUBTITLES -> io.bsoft.bconverter.feature.subtitleextractor.SubtitleExtractorScreen(back, sharedInput = shared?.firstOrNull())
                null -> LaunchedEffect(Unit) { back() }
            }
        }
        composable(BATCH_ROUTE) { BatchConvertScreen(onBack = { navController.popBackStack() }) }
        composable(HOME_ROUTE) {
            val vm: HomeViewModel = hiltViewModel()
            // Recent work on Home comes from the same source as the Recent Projects screen; refreshed whenever Home returns.
            val projects: ProjectsViewModel = hiltViewModel()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { projects.refresh() }
            HomeScreen(
                state = vm.uiState.collectAsStateWithLifecycle().value,
                onDestinationClick = { navController.navigate(it.route) },
                recentProjects = projects.state.collectAsStateWithLifecycle().value.projects,
                onOpenProject = { id -> navController.navigate("editor?$PROJECT_ARG=$id") },
            )
        }
        composable(
            EDITOR_ROUTE,
            arguments = listOf(navArgument(PROJECT_ARG) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            EditorRoute(projectId = entry.arguments?.getString(PROJECT_ARG), onBack = { navController.popBackStack() })
        }
        composable(HomeDestination.RECENT_PROJECTS.route) {
            ProjectsRoute(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate("editor?$PROJECT_ARG=$id") },
            )
        }
        HomeDestination.entries.filter { it != HomeDestination.VIDEO_EDITOR && it != HomeDestination.RECENT_PROJECTS }.forEach { d ->
            composable(d.route) {
                val back: () -> Unit = { navController.popBackStack() }
                when (d) {
                    HomeDestination.CONVERT_VIDEO -> ConverterScreen(back, onBatch = { navController.navigate(BATCH_ROUTE) })
                    HomeDestination.EXTRACT_AUDIO -> AudioExtractorScreen(back)
                    HomeDestination.EXTRACT_SUBTITLES -> io.bsoft.bconverter.feature.subtitleextractor.SubtitleExtractorScreen(back)
                    HomeDestination.FONTS -> io.bsoft.bconverter.feature.fonts.FontsRoute(back)
                    HomeDestination.SETTINGS -> SettingsRoute(back, onOpenFonts = { navController.navigate(HomeDestination.FONTS.route) })
                    HomeDestination.ABOUT -> AboutRoute(back)
                    HomeDestination.JOBS -> io.bsoft.bconverter.feature.jobs.JobsRoute(back)
                    else -> PlaceholderScreen(d.titleRes, back)
                }
            }
        }
    }
}
