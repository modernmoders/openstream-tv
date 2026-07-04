package dev.openstream.tv.addon

import kotlinx.serialization.json.Json

/**
 * The one Json instance used for all addon protocol parsing.
 *
 * Lenient parsing is mandatory (MASTER_PLAN §3.1): community addons return
 * sloppy JSON — unknown keys, unquoted values, nulls where objects are
 * expected. A parse must only fail when the payload is truly unusable.
 */
val AddonJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    // Addons sometimes send explicit nulls for fields they should omit;
    // fall back to the DTO's default value instead of failing.
    coerceInputValues = true
}
