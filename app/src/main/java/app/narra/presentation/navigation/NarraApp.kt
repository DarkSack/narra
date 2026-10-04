package app.narra.presentation.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.narra.core.designsystem.motion.LocalSharedTransitionScope
import app.narra.core.designsystem.motion.ProvideNavAnimatedScope
import app.narra.core.designsystem.theme.NarraTheme
import app.narra.domain.model.AppSettings
import app.narra.presentation.AppEvent
import app.narra.presentation.AppViewModel
import app.narra.presentation.book.BookDetailsScreen
import app.narra.presentation.components.MiniPlayer
import app.narra.presentation.home.HomeScreen
import app.narra.presentation.importing.ImportScreen
import app.narra.presentation.library.LibraryScreen
import app.narra.presentation.player.PlayerScreen
import app.narra.presentation.processing.ProcessingScreen
import app.narra.presentation.settings.SettingsScreen
import app.narra.presentation.text.ChapterTextScreen
import app.narra.presentation.voices.VoicesScreen
import kotlin.reflect.KClass

/**
 * Espacio que las pantallas deben dejar libre abajo para que el mini reproductor (y la barra
 * del sistema, cuando no hay barra de navegación) no tapen su último elemento.
 */
val LocalBottomOverlayPadding = compositionLocalOf { 0.dp }

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val TOP_LEVEL = listOf(
    TopLevelDestination(HomeRoute, HomeRoute::class, "Inicio", Icons.Outlined.Home, Icons.Rounded.Home),
    TopLevelDestination(LibraryRoute, LibraryRoute::class, "Biblioteca", Icons.AutoMirrored.Outlined.LibraryBooks, Icons.AutoMirrored.Rounded.LibraryBooks),
    TopLevelDestination(SettingsRoute, SettingsRoute::class, "Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

private const val PDF_MIME = "application/pdf"
private val MINI_PLAYER_SPACE = 84.dp

@Composable
fun NarraApp(viewModel: AppViewModel, settings: AppSettings) {
    NarraTheme(settings) {
        val navController = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val miniPlayer by viewModel.miniPlayer.collectAsStateWithLifecycle()
        val importing by viewModel.importing.collectAsStateWithLifecycle()
        var duplicate by remember { mutableStateOf<AppEvent.Duplicate?>(null) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) viewModel.importPdf(uri)
        }
        val onImport = { picker.launch(arrayOf(PDF_MIME)) }

        LaunchedEffect(viewModel, navController) {
            viewModel.events.collect { event ->
                when (event) {
                    is AppEvent.ReviewImport -> navController.navigate(ImportRoute(event.bookId)) { launchSingleTop = true }
                    is AppEvent.ShowBook -> navController.navigate(BookRoute(event.bookId)) { launchSingleTop = true }
                    is AppEvent.Duplicate -> duplicate = event
                    AppEvent.OpenPlayer -> navController.navigate(PlayerRoute) { launchSingleTop = true }
                    is AppEvent.ImportFailed -> {
                        val result = snackbar.showSnackbar(
                            message = "${event.error.title}. ${event.error.explanation}",
                            actionLabel = "Elegir otro",
                        )
                        if (result == SnackbarResult.ActionPerformed) onImport()
                    }
                }
            }
        }

        val backStack by navController.currentBackStackEntryAsState()
        val destination = backStack?.destination
        val isTopLevel = TOP_LEVEL.any { destination.isRoute(it.routeClass) }
        val isPlayer = destination.isRoute(PlayerRoute::class)
        val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        val suiteType = if (isTopLevel) adaptiveType else NavigationSuiteType.None
        val showMini = miniPlayer != null && !isPlayer
        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomOverlay: Dp = (if (showMini) MINI_PLAYER_SPACE else 0.dp) +
            (if (suiteType == NavigationSuiteType.NavigationBar) 0.dp else systemBottom)

        NavigationSuiteScaffold(
            layoutType = suiteType,
            navigationSuiteItems = {
                TOP_LEVEL.forEach { item ->
                    val selected = destination.isRoute(item.routeClass)
                    item(
                        selected = selected,
                        onClick = { navController.navigateTopLevel(item.route) },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SharedTransitionLayout {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides this,
                        LocalBottomOverlayPadding provides bottomOverlay,
                    ) {
                        NarraNavHost(navController, onImport)
                    }
                }

                if (importing) {
                    LinearProgressIndicator(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars))
                }

                AnimatedVisibility(
                    visible = showMini,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (suiteType == NavigationSuiteType.NavigationBar) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                        .padding(horizontal = NarraTheme.tokens.screenPadding / 2, vertical = 8.dp),
                ) {
                    miniPlayer?.let { state ->
                        MiniPlayer(
                            state = state,
                            onOpen = { navController.navigate(PlayerRoute) { launchSingleTop = true } },
                            onPlayPause = viewModel::togglePlayPause,
                        )
                    }
                }

                SnackbarHost(
                    snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomOverlay),
                )
            }
        }

        duplicate?.let { event ->
            AlertDialog(
                onDismissRequest = { duplicate = null },
                title = { Text("Ya tienes este libro") },
                text = { Text("«${event.title}» ya está en tu biblioteca. ¿Quieres abrirlo o importarlo otra vez como un libro aparte?") },
                confirmButton = {
                    TextButton(onClick = {
                        duplicate = null
                        navController.navigate(BookRoute(event.bookId)) { launchSingleTop = true }
                    }) { Text("Abrir") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        duplicate = null
                        viewModel.importPdf(event.uri, allowDuplicate = true)
                    }) { Text("Importar de nuevo") }
                },
            )
        }
    }
}

@Composable
private fun NarraNavHost(navController: NavHostController, onImport: () -> Unit) {
    val fadeMillis = NarraTheme.tokens.transitionMillis
    val reduceMotion = NarraTheme.reduceMotion
    val enter: EnterTransition = if (reduceMotion) EnterTransition.None else fadeIn(tween(fadeMillis))
    val exit: ExitTransition = if (reduceMotion) ExitTransition.None else fadeOut(tween(fadeMillis))
    val openBook: (String) -> Unit = { id -> navController.navigate(BookRoute(id)) }
    val review: (String) -> Unit = { id -> navController.navigate(ImportRoute(id)) { launchSingleTop = true } }
    val openPlayer = { navController.navigate(PlayerRoute) { launchSingleTop = true } }
    val openProcessing: (String) -> Unit = { id -> navController.navigate(ProcessingRoute(id)) { launchSingleTop = true } }
    val chooseVoice: (String) -> Unit = { id -> navController.navigate(VoicesRoute(id)) { launchSingleTop = true } }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        composable<HomeRoute> {
            ProvideNavAnimatedScope(this) {
                HomeScreen(
                    onOpenBook = openBook,
                    onOpenProcessing = { book -> if (book.state.isImporting) review(book.id) else openProcessing(book.id) },
                    onReview = review,
                    onOpenLibrary = { navController.navigateTopLevel(LibraryRoute) },
                    onOpenPlayer = openPlayer,
                    onImport = onImport,
                )
            }
        }
        composable<LibraryRoute> {
            ProvideNavAnimatedScope(this) {
                LibraryScreen(onOpenBook = openBook, onImport = onImport)
            }
        }
        composable<SettingsRoute> {
            ProvideNavAnimatedScope(this) {
                SettingsScreen(
                    onChooseDefaultVoice = { navController.navigate(VoicesRoute()) { launchSingleTop = true } },
                    onOpenLicenses = null,
                )
            }
        }
        composable<BookRoute> {
            ProvideNavAnimatedScope(this) {
                BookDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = openPlayer,
                    onOpenProcessing = openProcessing,
                    onReview = review,
                    onEdit = review,
                    onChooseVoice = chooseVoice,
                )
            }
        }
        composable<ImportRoute> {
            ProvideNavAnimatedScope(this) {
                ImportScreen(
                    onBack = { navController.popBackStack() },
                    onReadChapter = { chapter -> navController.navigate(ChapterTextRoute(chapter.id, chapter.title)) },
                    onChooseVoice = chooseVoice,
                    onAudiobookStarted = { id ->
                        // La revisión ya no hace falta en la pila: atrás desde la ficha vuelve a donde se importó.
                        navController.navigate(BookRoute(id)) { popUpTo<ImportRoute> { inclusive = true } }
                    },
                )
            }
        }
        composable<VoicesRoute> {
            VoicesScreen(onBack = { navController.popBackStack() })
        }
        composable<ProcessingRoute> {
            ProcessingScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = openPlayer,
                onOpenProcessing = { id -> navController.navigate(ProcessingRoute(id)) { popUpTo<ProcessingRoute> { inclusive = true } } },
                onChooseVoice = chooseVoice,
            )
        }
        composable<ChapterTextRoute> {
            ChapterTextScreen(onBack = { navController.popBackStack() })
        }
        composable<PlayerRoute>(
            enterTransition = { if (reduceMotion) EnterTransition.None else slideInVertically(tween(fadeMillis)) { it } },
            exitTransition = { exit },
            popEnterTransition = { enter },
            popExitTransition = { if (reduceMotion) ExitTransition.None else slideOutVertically(tween(fadeMillis)) { it } },
        ) {
            ProvideNavAnimatedScope(this) {
                PlayerScreen(
                    onClose = { navController.popBackStack() },
                    onOpenLibrary = {
                        navController.popBackStack()
                        navController.navigateTopLevel(LibraryRoute)
                    },
                )
            }
        }
    }
}

private fun NavDestination?.isRoute(route: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

/** Navegación entre pestañas: conserva el estado de cada una y no apila duplicados. */
private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
