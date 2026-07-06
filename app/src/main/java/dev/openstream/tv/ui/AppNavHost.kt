package dev.openstream.tv.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun AppNavHost(launchViewModel: LaunchViewModel = hiltViewModel()) {
    // First launch with nothing installed → Welcome Guide; else Home.
    // null = Room read in flight (ms) — render nothing rather than flash Home.
    val startOnWelcome by launchViewModel.startOnWelcome.collectAsStateWithLifecycle()
    val startDestination = when (startOnWelcome) {
        null -> return
        true -> Routes.CONNECT
        false -> Routes.HOME
    }
    val navController = rememberNavController()
    // Movies skip the details screen: it held exactly one action ("View
    // streams"), which the owner called an extra step (2026-07-05 round 5).
    // Series/channels still need details for season & episode picking.
    // For a movie the meta id IS the video id (§4.1 stream addressing).
    val openDetails: (MetaItem) -> Unit = { item ->
        if (item.type.equals("movie", ignoreCase = true)) {
            navController.navigate(
                Routes.streams(item.type, item.id, item.name, item.id, item.poster)
            )
        } else {
            navController.navigate(Routes.details(item))
        }
    }
    // On-screen Back buttons (§10 elder-friendly) share the remote's semantics.
    val goBack: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
                onDiscover = { navController.navigate(Routes.DISCOVER) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
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
                onConnect = { navController.navigate(Routes.CONNECT) },
                onAddons = { navController.navigate(Routes.ADDONS) },
            )
        }
        composable(Routes.SETTINGS_HOME_ROWS) {
            HomeRowsScreen(onBack = goBack)
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
                onExit = { navController.popBackStack() },
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
}
