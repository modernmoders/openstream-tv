package dev.openstream.tv.ui.components

import dev.openstream.tv.addon.AddonRequestException

/**
 * Short, URL-free text for on-screen failure chips. Addon URLs are user
 * secrets (they can embed personal config tokens) — never render them.
 */
fun Throwable.toChipMessage(): String =
    when ((this as? AddonRequestException)?.reason) {
        AddonRequestException.Reason.NETWORK -> "couldn't reach the addon"
        AddonRequestException.Reason.HTTP_STATUS -> "the addon answered with an error"
        AddonRequestException.Reason.BAD_JSON -> "the addon sent an unreadable response"
        AddonRequestException.Reason.INVALID_URL,
        AddonRequestException.Reason.INVALID_MANIFEST,
        null -> "failed to load"
    }
