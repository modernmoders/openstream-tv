package dev.openstream.tv.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The self-update manifest at `<setup-url>/app/version.json` (published by
 * `tools/publish_update.sh` after every release build). Kept tiny on purpose:
 * a version, and where to get its APK.
 *
 * Why this exists: Rachael's box leaves the owner's house (2026-07-11) — adb
 * is no longer a deploy path, so the app has to be able to fetch its own next
 * build. Android still shows its own "Install?" confirmation; the updater
 * gets the user one OK away, it can't (and shouldn't) go silent.
 */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
) {
    /** Strictly newer only — equal or older (server rolled back) offers nothing. */
    fun isNewerThan(installedCode: Long): Boolean = versionCode > installedCode
}

/**
 * Null for anything unusable: bad JSON, missing fields, or a non-https APK
 * URL (the manifest travels over the network — never hand the installer a
 * downgraded-scheme URL).
 */
fun parseUpdateManifest(json: String): UpdateManifest? = try {
    val obj = Json.parseToJsonElement(json).jsonObject
    val code = obj["versionCode"]?.jsonPrimitive?.longOrNull
    val url = obj["apkUrl"]?.jsonPrimitive?.contentOrNull
    if (code == null || url == null || !url.startsWith("https://")) {
        null
    } else {
        UpdateManifest(
            versionCode = code,
            versionName = obj["versionName"]?.jsonPrimitive?.contentOrNull ?: code.toString(),
            apkUrl = url,
        )
    }
} catch (e: Exception) {
    null
}
