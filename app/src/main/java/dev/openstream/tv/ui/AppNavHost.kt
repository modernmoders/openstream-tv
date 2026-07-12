package dev.openstream.tv.ui

import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.openstream.tv.ui.components.NavDestination
import dev.openstream.tv.ui.components.NavRail
import dev.openstream.tv.ui.components.RailIconKind
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.ui.addons.AddAddonScreen
import dev.openstream.tv.ui.addons.AddonManagerScreen
import dev.openstream.tv.ui.connect.ConnectScreen
import dev.openstream.tv.ui.details.DetailsScreen
import dev.openstream.tv.ui.discover.DiscoverScreen
import dev.openstream.tv.ui.home.HomeScreen
import dev.openstream.tv.ui.player.PlayerScreen
import dev.openstream.tv.ui.search.SearchScreen
import dev.openstream.tv.ui.settings.AppLogScreen
import dev.openstream.tv.ui.settings.HomeRowsScreen
import dev.openstream.tv.ui.settings.SettingsScreen
import dev.openstream.tv.ui.streams.StreamListScreen

/**
 * All navigation destinations. Routes are plain strings (KISS); Back always
 * pops exactly one level (MASTER_PLAN §5.4) — never build multi-pop flows.
 */
object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val SEARCH = "search"
    const val ADDONS = "addons"
    const val ADDONS_ADD = "addons/add"
    const val SETTINGS = "settings"
    const val SETTINGS_HOME_ROWS = "settings/home-rows"
    /** Expert-mode diagnostics viewer (MASTER_PLAN §10 "log them"). */
    const val SETTINGS_APP_LOG = "settings/app-log"
    /** Welcome Guide + type-your-name setup (owner directive 2026-07-06). */
    const val CONNECT = "connect"

    /** Ids contain `:` and arbitrary addon characters — always Uri.encode. */
    const val DETAILS = "details/{type}/{id}"
    fun details(item: MetaItem) = "details/${Uri.encode(item.type)}/${Uri.encode(item.id)}"

    const val STREAMS = "streams/{type}/{videoId}?title={title}&metaId={metaId}&poster={poster}"
    fun streams(type: String, videoId: String, title: String, metaId: String, poster: String?) =
        "streams/${Uri.encode(type)}/${Uri.encode(videoId)}?title=${Uri.encode(title)}" +
            "&metaId=${Uri.encode(metaId)}&poster=${Uri.encode(poster.orEmpty())}"

    /** Source arrives via CurrentPlayback, not route args (see that class). */
    const val PLAYER = "player"
}

@Composable
fun AppNavHost(
    launchViewModel: LaunchViewModel = hiltViewModel(),
    updateViewModel: dev.openstream.tv.ui.update.UpdateViewModel = hiltViewModel(),
) {
    // Self-update: one silent check per app launch; only ever surfaces UI
    // when a newer build is actually waiting (owner 2026-07-11 — boxes that
    // left the house update themselves, no adb).
    androidx.compose.runtime.LaunchedEffect(Unit) { updateViewModel.autoCheckOnLaunch() }
    val updateUi by updateViewModel.ui.collectAsStateWithLifecycle()
    dev.openstream.tv.ui.update.UpdatePrompt(
        ui = updateUi,
        brand = updateViewModel.brand,
        onInstall = updateViewModel::install,
        onDismiss = updateViewModel::dismiss,
    )

    // First launch with nothing installed → Welcome Guide; else Home.
    // null = Room read in flight (ms) — render nothing rather than flash Home.
    val startOnWelcome by launchViewModel.startOnWelcome.collectAsStateWithLifecycle()
    val startDestination = when (startOnWelcome) {
        null -> return
        true -> Routes.CONNECT
        false -> Routes.HOME
    }
    // Easy vs Expert mode (owner round 10 — DECISIONS #27 default OFF):
    // gates two navigation shapes below. Expert mode keeps every shortcut a
    // technical user already relies on; easy mode never strands a viewer on
    // a raw, addon-labelled screen.
    val expertMode by launchViewModel.expertMode.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    // Expert mode: movies skip the details screen (it held exactly one
    // action, "View streams" — an extra step, 2026-07-05 round 5) straight to
    // the stream list. Easy mode instead opens a proper Info screen first
    // (owner round 10: backdrop, description, rating, cast, a big Play CTA,
    // an optional trailer button) — DetailsScreen already renders all of
    // that for series, so movies now share the same screen instead of a
    // second one (KISS). Series/channels always need Details for season &
    // episode picking either way. For a movie the meta id IS the video id
    // (§4.1 stream addressing).
    val openDetails: (MetaItem) -> Unit = { item ->
        val isMovie = item.type.equals("movie", ignoreCase = true)
        if (isMovie && expertMode) {
            navController.navigate(
                Routes.streams(item.type, item.id, item.name, item.id, item.poster)
            )
        } else {
            navController.navigate(Routes.details(item))
        }
    }
    // On-screen Back buttons (§10 elder-friendly) share the remote's semantics.
    val goBack: () -> Unit = { navController.popBackStack() }

    // Persistent left rail (owner 2026-07-10). Home/Discover/Search/Settings are
    // SIBLINGS, not a stack: switching between them pops back to the start
    // destination with saveState, so the back stack never grows and each section
    // keeps its scroll/focus. That's what lets their Back buttons go away.
    val sections = remember {
        listOf(
            NavDestination(Routes.HOME, "Home", RailIconKind.HOME),
            NavDestination(Routes.DISCOVER, "Discover", RailIconKind.DISCOVER),
            NavDestination(Routes.SEARCH, "Search", RailIconKind.SEARCH),
            NavDestination(Routes.SETTINGS, "Settings", RailIconKind.SETTINGS),
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val goSection: (String) -> Unit = { route ->
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                // Never restoreState when the target is HOME: Home is the
                // popUpTo anchor, so its "saved state" is whatever section was
                // last popped ON TOP of it — restoring that resurrected the
                // old section (Search → Home landed on Discover, and clicking
                // Home again "did nothing", owner 2026-07-11; emulator-proven).
                restoreState = route != Routes.HOME
            }
        }
    }

    // BACK-to-rail (owner 2026-07-11): deep inside a section's grid, BACK
    // opens the rail with the selector on the CURRENT section — no more
    // LEFT-crawling across a whole row just to switch sections.
    val railSectionFocus = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }
    val onSection = sections.any { it.route == currentRoute }

    Row(modifier = Modifier.fillMaxSize()) {
    if (onSection) {
        NavRail(
            currentRoute = currentRoute,
            destinations = sections,
            onSelect = goSection,
            sectionFocus = railSectionFocus,
            onFocusWithinChanged = { railHasFocus = it },
        )
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.weight(1f),
        // Refined, snappy screen motion (owner UX pass #4): a quick cross-fade
        // with a whisper of scale — enough to feel alive on a TV, never enough
        // to disorient or stutter. One place, so every destination inherits it.
        enterTransition = { fadeIn(tween(240)) + scaleIn(initialScale = 0.985f, animationSpec = tween(240)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.985f, animationSpec = tween(160)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                // The header pills are section switches, so they MUST use the
                // rail's goSection: a plain navigate() pushes the section onto
                // the stack, and the next rail move pops-and-saves it — the
                // saved segment then shadowed Home forever (see goSection).
                onDiscover = { goSection(Routes.DISCOVER) },
                onSearch = { goSection(Routes.SEARCH) },
                onSettings = { goSection(Routes.SETTINGS) },
                onItemClick = openDetails,
            )
        }
        composable(Routes.CONNECT) {
            // Done/"maybe later" both land Home with a clean back stack: from
            // the welcome start there IS no back, and from Settings a freshly
            // connected TV should open on its new home screen, not history.
            ConnectScreen(
                onExit = {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = goBack,
                onHomeRows = { navController.navigate(Routes.SETTINGS_HOME_ROWS) },
                onAddons = { navController.navigate(Routes.ADDONS) },
                onAppLog = { navController.navigate(Routes.SETTINGS_APP_LOG) },
                // "Reset this TV": land on Welcome/Connect with a clean back
                // stack, same as ConnectScreen's own Done exit — there's
                // nothing left to go "back" to once every addon is gone.
                onReset = {
                    navController.navigate(Routes.CONNECT) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.SETTINGS_HOME_ROWS) {
            HomeRowsScreen(onBack = goBack)
        }
        composable(Routes.SETTINGS_APP_LOG) {
            AppLogScreen(onBack = goBack)
        }
        composable(Routes.DISCOVER) {
            DiscoverScreen(onBack = goBack, onItemClick = openDetails)
        }
        composable(Routes.SEARCH) {
            SearchScreen(onBack = goBack, onItemClick = openDetails)
        }
        composable(Routes.ADDONS) {
            AddonManagerScreen(
                onBack = goBack,
                onAddAddon = { navController.navigate(Routes.ADDONS_ADD) },
            )
        }
        composable(Routes.ADDONS_ADD) {
            // No auto-navigation on install: the screen stays for the next
            // paste and confirms inline; Back returns to the addon list.
            AddAddonScreen(onBack = goBack)
        }
        composable(Routes.DETAILS) {
            DetailsScreen(
                onBack = goBack,
                onOpenStreams = { type, videoId, title, metaId, poster ->
                    navController.navigate(Routes.streams(type, videoId, title, metaId, poster))
                },
            )
        }
        composable(Routes.STREAMS) {
            StreamListScreen(
                onBack = goBack,
                onPlay = { navController.navigate(Routes.PLAYER) },
                // External-player autoplay's manual fallback (§7.1.6): REPLACE
                // this episode's list with the next episode's, mirroring the
                // player's behavior — Back must not walk a binge's whole tail.
                onOpenStreams = { type, videoId, title, metaId, poster ->
                    navController.navigate(Routes.streams(type, videoId, title, metaId, poster)) {
                        popUpTo(Routes.STREAMS) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                // Easy mode (owner round 10): BACK from the player must land
                // back on Details/episode-selection, never strand the viewer
                // on the raw stream list — pop THROUGH the Streams entry
                // instead of just one level. Expert mode keeps the original
                // single-pop-to-streams behavior (it's a deliberately
                // technical surface there). popBackStack(route, inclusive)
                // is a no-op (returns false) if Streams somehow isn't on the
                // stack — falling back to a plain pop keeps Back from ever
                // doing nothing.
                onExit = {
                    val poppedThroughStreams = !expertMode &&
                        navController.popBackStack(Routes.STREAMS, inclusive = true)
                    if (!poppedThroughStreams) navController.popBackStack()
                },
                // Autoplay's manual fallback (§7.1 step 4): REPLACE the player
                // with the next episode's stream list, so Back from there goes
                // to the previous episode's list — not a dead ended player.
                onOpenStreams = { type, videoId, title, metaId, poster ->
                    navController.navigate(Routes.streams(type, videoId, title, metaId, poster)) {
                        popUpTo(Routes.PLAYER) { inclusive = true }
                    }
                },
            )
        }
    }
    }  // end Row(rail + NavHost)

    // Composed AFTER the NavHost on purpose: the last-registered enabled
    // BackHandler wins, and the NavHost registers its own back-pop while it
    // composes — these must beat it on the four section routes (elsewhere
    // they're disabled, so Details/Streams/Player back flows are untouched).
    // BACK in a section's content → rail, selector on the current section.
    BackHandler(enabled = onSection && !railHasFocus) {
        runCatching { railSectionFocus.requestFocus() }
    }
    // BACK again on the rail → confirm-then-exit ("press back again", owner
    // 2026-07-11). Sections are siblings, so there's no deeper "up" to pop —
    // exiting is the only honest meaning left for BACK here.
    val activity = LocalActivity.current
    val context = LocalContext.current
    var exitArmedAtMs by remember { mutableStateOf(0L) }
    BackHandler(enabled = onSection && railHasFocus) {
        val now = SystemClock.uptimeMillis()
        if (now - exitArmedAtMs <= EXIT_CONFIRM_WINDOW_MS) {
            activity?.finish()
        } else {
            exitArmedAtMs = now
            Toast.makeText(context, "Press BACK again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}

/** How long the exit toast keeps the second BACK armed. */
private const val EXIT_CONFIRM_WINDOW_MS = 3_000L
