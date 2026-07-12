package dev.openstream.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import dev.openstream.tv.diagnostics.DiagnosticsSink
import javax.inject.Inject

/**
 * PackageInstaller talks back through this receiver after
 * [AppUpdater.downloadAndInstall] commits a session. The one action that
 * matters: STATUS_PENDING_USER_ACTION carries the system's "Install?"
 * confirmation activity, which we must launch — without it the update just
 * silently waits forever. Everything else is logged for the App log.
 */
@AndroidEntryPoint
class UpdateStatusReceiver : BroadcastReceiver() {

    @Inject lateinit var diagnostics: DiagnosticsSink

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent)
                if (confirm != null) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    diagnostics.record("update", "pending-user-action without a confirm intent")
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                diagnostics.record("update", "install committed OK")
            else ->
                diagnostics.record(
                    "update",
                    "install status=$status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
                )
        }
    }

    private fun confirmIntent(intent: Intent): Intent? =
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_INTENT)

    companion object {
        const val ACTION_UPDATE_STATUS = "dev.openstream.tv.UPDATE_STATUS"
    }
}
