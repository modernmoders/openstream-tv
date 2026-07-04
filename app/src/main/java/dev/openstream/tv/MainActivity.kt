package dev.openstream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import dev.openstream.tv.ui.AppNavHost
import dev.openstream.tv.ui.theme.OpenStreamTheme

/**
 * Single-activity app: all screens are Compose destinations inside this
 * activity (see ui/AppNavHost.kt for the destination graph).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenStreamTheme {
                AppNavHost()
            }
        }
    }
}
