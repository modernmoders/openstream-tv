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

    const val STREAMS = "streams/{type}/{videoId}?title={title}"
    fun streams(type: String, videoId: String, title: String) =
        "streams/${Uri.encode(type)}/${Uri.encode(videoId)}?title=${Uri.encode(title)}"

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
            DetailsScreen(onOpenStreams = { type, videoId, title ->
                navController.navigate(Routes.streams(type, videoId, title))
            })
        }
        composable(Routes.STREAMS) {
            StreamListScreen(onPlay = { navController.navigate(Routes.PLAYER) })
        }
        composable(Routes.PLAYER) {
            PlayerScreen(onExit = { navController.popBackStack() })
        }
    }
}
