package dev.openstream.tv.ui.details

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * "Watch trailer" plumbing (owner 2026-07-28: "a YouTube — or SmartTube would
 * be incredible — video of the trailer").
 *
 * Two problems this solves:
 *
 *  1. **Which app opens it.** A bare ACTION_VIEW on a youtube.com URL is a
 *     coin flip on a TV box: some onn boxes have no browser at all, so the
 *     intent dead-ends with nothing on screen. We therefore aim at a known
 *     installed YouTube-capable app first — SmartTube ahead of YouTube,
 *     because it's what the owner's boxes actually run and it plays without
 *     ads — and only fall back to "let the system decide".
 *
 *  2. **Most addons send no trailer at all** (Cinemeta included — see
 *     Meta.trailers). Rather than hide the button on half the movies, a
 *     missing trailer id falls back to a YouTube *search* for the title, which
 *     lands the viewer one click from the trailer instead of nowhere.
 *
 * Package visibility: Android 11+ hides other apps unless they're declared in
 * <queries> — the manifest lists these package names for that reason. Without
 * it [PackageManager.getLaunchIntentForPackage] returns null and we'd always
 * take the generic fallback.
 */
object TrailerLauncher {

    /**
     * YouTube-capable apps in the order we'd rather use them. SmartTube first
     * (owner preference), then the TV YouTube app, then the phone/tablet one
     * — sideloaded boxes sometimes carry the phone build.
     */
    private val PREFERRED_PACKAGES = listOf(
        "com.teamsmart.videomanager.tv",   // SmartTube Next (TV)
        "com.liskovsoft.smarttubetv.beta", // SmartTube Beta
        "com.google.android.youtube.tv",   // YouTube for Android TV
        "com.google.android.youtube",      // YouTube (phone build, sideloaded)
    )

    /** Watch page for a bare YouTube video id (Stremio's Trailer.source). */
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /** Search results for a title — the no-trailer-in-meta fallback. */
    fun searchUrl(title: String, year: String?): String {
        val query = listOfNotNull(title.ifBlank { null }, year?.ifBlank { null }, "trailer")
            .joinToString(" ")
        return "https://www.youtube.com/results?search_query=" + Uri.encode(query)
    }

    /**
     * The first preferred package that is actually installed, or null when
     * none is — in which case the caller sends an unpackaged intent and lets
     * the system chooser (or a browser) handle it.
     */
    fun preferredPackage(context: Context): String? =
        PREFERRED_PACKAGES.firstOrNull { pkg ->
            runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg) != null
            }.getOrDefault(false)
        }

    /**
     * Open [url] in the best available app. Returns false when nothing on the
     * box could handle it, so the caller can say so instead of looking broken.
     */
    fun open(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        val targeted = preferredPackage(context)?.let { pkg ->
            Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
        }
        // Targeted first; a package that's installed but refuses this URL
        // (rare, but SmartTube forks differ) still gets the generic retry.
        for (intent in listOfNotNull(targeted, Intent(Intent.ACTION_VIEW, uri))) {
            val launched = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }
}
