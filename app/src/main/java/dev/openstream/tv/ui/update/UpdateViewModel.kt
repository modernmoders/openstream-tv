package dev.openstream.tv.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.update.AppUpdater
import dev.openstream.tv.update.UpdateManifest
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Update state for BOTH surfaces: the launch prompt (AppNavHost overlay) and
 * the Settings "App update" row. Two screens mean two ViewModel instances —
 * the once-per-process guard lives in [AppUpdater.autoCheckDone], not here,
 * so Settings opening never re-fires the automatic check.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
    setupConfig: dev.openstream.tv.data.SetupConfig,
) : ViewModel() {

    /** For prompt wording ("SStreams has an update"). */
    val brand: String = setupConfig.brand

    sealed interface UpdateUi {
        /** Nothing to show (also the silent-check "no news" outcome). */
        data object Hidden : UpdateUi

        data class Available(val versionName: String) : UpdateUi
        data object Downloading : UpdateUi

        /** Download/session failed BEFORE the system dialog — retryable. */
        data object InstallFailed : UpdateUi

        // Manual-check-only outcomes (Settings row feedback):
        data object Checking : UpdateUi
        data object UpToDate : UpdateUi
        data object CheckFailed : UpdateUi
    }

    private var manifest: UpdateManifest? = null
    private val _ui = MutableStateFlow<UpdateUi>(UpdateUi.Hidden)
    val ui: StateFlow<UpdateUi> = _ui

    /** Once per app process, silently; only surfaces when there IS an update. */
    fun autoCheckOnLaunch() {
        if (updater.autoCheckDone) return
        updater.autoCheckDone = true
        viewModelScope.launch { runCheck(silent = true) }
    }

    /** Settings row: always answers, even when the answer is "no update". */
    fun manualCheck() {
        _ui.value = UpdateUi.Checking
        viewModelScope.launch { runCheck(silent = false) }
    }

    private suspend fun runCheck(silent: Boolean) {
        when (val result = updater.check()) {
            is AppUpdater.CheckResult.UpdateAvailable -> {
                manifest = result.manifest
                _ui.value = UpdateUi.Available(result.manifest.versionName)
            }
            AppUpdater.CheckResult.UpToDate ->
                _ui.value = if (silent) UpdateUi.Hidden else UpdateUi.UpToDate
            AppUpdater.CheckResult.Unreachable ->
                _ui.value = if (silent) UpdateUi.Hidden else UpdateUi.CheckFailed
        }
    }

    /** Download + hand to Android. On success the system install dialog takes
     *  over, so our own UI just gets out of the way. */
    fun install() {
        val target = manifest ?: return
        _ui.value = UpdateUi.Downloading
        viewModelScope.launch {
            val committed = updater.downloadAndInstall(target)
            _ui.value = if (committed) UpdateUi.Hidden else UpdateUi.InstallFailed
        }
    }

    fun dismiss() {
        _ui.value = UpdateUi.Hidden
    }
}
