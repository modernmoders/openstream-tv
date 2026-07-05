package dev.openstream.tv.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.ui.addons.AddAddonScreen
import dev.openstream.tv.ui.addons.AddonManagerScreen
import dev.openstream.tv.ui.details.DetailsScreen
import dev.openstream.tv.ui.discover.DiscoverScreen
import dev.openstream.tv.ui.home.HomeScreen
import dev.openstream.tv.ui.player.PlayerScreen
import dev.openstream.tv.ui.search.SearchScreen
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
fun AppNavHost() {
    val navController = rememberNavController()
    val openDetails: (MetaItem) -> Unit = { navController.navigate(Routes.details(it)) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onDiscover = { navController.navigate(Routes.DISCOVER) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onManageAddons = { navController.navigate(Routes.ADDONS) },
                onItemClick = openDetails,
            )
        }
        composable(Routes.DISCOVER) { DiscoverScreen(onItemClick = openDetails) }
        composable(Routes.SEARCH) { SearchScreen(onItemClick = openDetails) }
        composable(Routes.ADDONS) {
            AddonManagerScreen(onAddAddon = { navController.navigate(Routes.ADDONS_ADD) })
        }
        composable(Routes.ADDONS_ADD) {
            AddAddonScreen(onInstalled = { navController.popBackStack() })
        }
        composable(Routes.DETAILS) {
            DetailsScreen(onOpenStreams = { type, videoId, title, metaId, poster ->
                navController.navigate(Routes.streams(type, videoId, title, metaId, poster))
            })
        }
        composable(Routes.STREAMS) {
            StreamListScreen(
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
