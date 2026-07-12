package dev.openstream.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a self-update replaces the package, bring the app back on screen —
 * on a TV the person was mid-session and shouldn't have to find the launcher
 * tile again. Best-effort: newer Android versions may block background
 * activity starts, in which case the update still succeeded and the person
 * just reopens the app normally.
 */
class RelaunchAfterUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = context.packageManager
            .getLeanbackLaunchIntentForPackage(context.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
