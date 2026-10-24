package net.notscape.browser.browser

import android.net.Uri

/**
 * Clean links (Phase D Everyday Completeness): strips the tracking
 * query parameters (utm_* and the click-id family) from a URL before
 * it is copied or shared, so what leaves the browser doesn't carry
 * the trackers the page put there. Non-http(s) URLs and URLs without
 * tracked parameters pass through untouched.
 */
object CleanLink {

    private val trackedNames = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid",
        "igshid", "_ga", "yclid", "twclid", "ttclid", "li_fat_id"
    )

    private fun isTracked(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("utm_") || n in trackedNames
    }

    fun strip(url: String): String {
        if (url.isBlank()) return url
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if (uri.scheme != "http" && uri.scheme != "https") return url
        val names = runCatching { uri.queryParameterNames }.getOrNull() ?: return url
        if (names.isEmpty() || names.none { isTracked(it) }) return url
        val builder = uri.buildUpon().clearQuery()
        for (name in names) {
            if (isTracked(name)) continue
            for (value in uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value)
            }
        }
        return builder.build().toString()
    }
}

/**
 * Validation for the custom search-engine URL template. Rules from
 * the plan: https only, a %s placeholder is required, and the
 * script/data/file schemes are refused outright.
 */
object SearchTemplate {

    /** Returns an error message, or null when [raw] is usable. */
    fun validate(raw: String): String? {
        val u = raw.trim()
        if (u.isEmpty()) return "Enter a search URL."
        val lower = u.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:") ||
            lower.startsWith("file:")
        ) {
            return "That kind of URL can't be used for search."
        }
        if (!lower.startsWith("https://")) {
            return "The search URL must start with https://"
        }
        if (!u.contains("%s")) {
            return "The URL must contain %s where the search terms go."
        }
        return null
    }

    fun isValid(raw: String): Boolean = validate(raw) == null
}
