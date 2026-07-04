package dev.openstream.tv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.openstream.tv.ui.addons.AddAddonScreen
import dev.openstream.tv.ui.addons.AddonManagerScreen
import dev.openstream.tv.ui.home.HomeScreen

/**
 * All navigation destinations. Routes are plain strings (KISS); Back always
 * pops exactly one level (MASTER_PLAN §5.4) — never build multi-pop flows.
 */
object Routes {
    const val HOME = "home"
    const val ADDONS = "addons"
    const val ADDONS_ADD = "addons/add"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onManageAddons = { navController.navigate(Routes.ADDONS) })
        }
        composable(Routes.ADDONS) {
            AddonManagerScreen(onAddAddon = { navController.navigate(Routes.ADDONS_ADD) })
        }
        composable(Routes.ADDONS_ADD) {
            AddAddonScreen(onInstalled = { navController.popBackStack() })
        }
    }
}
