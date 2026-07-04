package dev.openstream.tv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.openstream.tv.ui.addons.AddAddonScreen
import dev.openstream.tv.ui.addons.AddonManagerScreen
import dev.openstream.tv.ui.discover.DiscoverScreen
import dev.openstream.tv.ui.home.HomeScreen
import dev.openstream.tv.ui.search.SearchScreen

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
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onDiscover = { navController.navigate(Routes.DISCOVER) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onManageAddons = { navController.navigate(Routes.ADDONS) },
            )
        }
        composable(Routes.DISCOVER) { DiscoverScreen() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.ADDONS) {
            AddonManagerScreen(onAddAddon = { navController.navigate(Routes.ADDONS_ADD) })
        }
        composable(Routes.ADDONS_ADD) {
            AddAddonScreen(onInstalled = { navController.popBackStack() })
        }
    }
}
