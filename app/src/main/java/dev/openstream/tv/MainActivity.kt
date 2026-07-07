package dev.openstream.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.ui.AppNavHost
import dev.openstream.tv.ui.sound.UiSounds
import dev.openstream.tv.ui.theme.OpenStreamTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Single-activity app: all screens are Compose destinations inside this
 * activity (see ui/AppNavHost.kt for the destination graph).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var uiSounds: UiSounds
    @Inject lateinit var viewPrefs: ViewPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the sound engine in step with the Settings toggle.
        lifecycleScope.launch {
            viewPrefs.uiSounds.collect { uiSounds.enabled = it }
        }
        setContent {
            OpenStreamTheme {
                AppNavHost()
            }
        }
    }

    /**
     * UI sounds hook (owner round 10): every remote key-down passes through
     * here before Compose sees it, which is the one app-wide place to play
     * focus/select cues — Compose has no global focus-change listener.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) uiSounds.onKeyDown(event)
        return super.dispatchKeyEvent(event)
    }
}
