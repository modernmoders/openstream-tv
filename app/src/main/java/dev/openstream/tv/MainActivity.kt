package dev.openstream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import dev.openstream.tv.ui.HelloScreen

/**
 * Single-activity app: all screens are Compose destinations inside this
 * activity. Phase 0 shows only the HelloScreen boot check.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloScreen()
        }
    }
}
