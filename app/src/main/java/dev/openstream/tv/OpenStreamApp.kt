package dev.openstream.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.openstream.tv.addon.ProfileSync
import dev.openstream.tv.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Application entry point. @HiltAndroidApp triggers Hilt's code generation;
 * all dependency injection containers hang off this class.
 */
@HiltAndroidApp
class OpenStreamApp : Application() {

    @Inject lateinit var profileSync: ProfileSync

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Remote addon management: follow the owner's hosted setup profile.
        // Fire-and-forget — sync failures are silent by design (elder rule).
        appScope.launch { profileSync.syncIfDue() }
    }
}
