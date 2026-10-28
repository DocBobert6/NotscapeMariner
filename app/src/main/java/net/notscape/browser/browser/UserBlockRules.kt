package net.notscape.browser.browser

/**
 * The user's own block rules (element picker, beta.18):
 * network rules from long-pressed links/images, and
 * per-site cosmetic selectors from picked elements.
 * Stored as one rule per line in a setting, tab
 * separated, so the logic stays pure-Kotlin and
 * unit-testable (org.json is an Android stub under
 * local JVM tests):
 *
 *   net \t HOST \t <host>          — everything from a host
 *   net \t URL \t <scheme://host/path> — one address, query
 *     and fragment stripped at save time so ad URLs
 *     with cache-busters still match on reload
 *   cos \t <page host> \t <selector> — hide on one site only
 *
 * A network rule never blocks a main-frame load
 * (that would break tapped links); a cosmetic rule
 * applies to its exact host only, never inherited
 * across hosts, since a selector that is safe on
 * one site can hide real content on another.
 */
object UserBlockRules {
    const val NET_KEY = "userBlockNet"
    const val COSMETIC_KEY = "userBlockCosmetic"

    sealed interface NetRule {
        data class Host(val host: String) : NetRule
        data class Url(val url: String) : NetRule
    }

    data class CosmeticRule(val host: String, val selector: String)

    fun parseNet(raw: String): List<NetRule> {
        val out = ArrayList<NetRule>()
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size != 3 || parts[0] != "net") continue
            when (parts[1]) {
                "HOST" -> {
                    val h = parts[2].lowercase()
                    if (h.isNotBlank()) out += NetRule.Host(h)
                }
                "URL" -> {
                    val u = parts[2].lowercase()
                    if (u.isNotBlank()) out += NetRule.Url(u)
                }
            }
        }
        return out
    }

    fun serializeNet(rules: List<NetRule>): String =
        rules.joinToString("\n") { rule ->
            when (rule) {
                is NetRule.Host -> "net\tHOST\t${rule.host}"
                is NetRule.Url -> "net\tURL\t${rule.url}"
            }
        }

    fun parseCosmetic(raw: String): List<CosmeticRule> {
        val out = ArrayList<CosmeticRule>()
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size != 3 || parts[0] != "cos") continue
            val host = parts[1].lowercase()
            val sel = parts[2]
            if (host.isNotBlank() && isSafeSelector(sel)) {
                out += CosmeticRule(host, sel)
            }
        }
        return out
    }

    fun serializeCosmetic(rules: List<CosmeticRule>): String =
        rules.joinToString("\n") { rule ->
            "cos\t${rule.host}\t${rule.selector}"
        }

    /** A selector is safe to drop into the injected
     * <style> sheet only when it cannot break out
     * of its own rule: no braces, no semicolons, no
     * angle brackets, no newlines. The picker builder
     * never generates those characters; this is the
     * backstop for anything stored by hand. */
    fun isSafeSelector(selector: String): Boolean {
        if (selector.isBlank()) return false
        return selector.none { c ->
            c == '{' || c == '}' || c == ';' || c == '<' ||
                c == '\n' || c == '\r' || c == '\t'
        }
    }

    /** The address part a URL rule matches: scheme,
     * host and path, lowercased, no query/fragment.
     * Plain strings, not android.net.Uri, so the
     * matcher stays JVM-testable (Uri is a stub
     * under local unit tests); callers split the
     * real Uri apart. */
    fun urlKey(scheme: String, host: String, path: String): String {
        return "${scheme.lowercase()}://${host.lowercase()}" +
            path.lowercase()
    }

    fun netMatches(
        rule: NetRule,
        scheme: String,
        hostRaw: String,
        pathRaw: String
    ): Boolean {
        val host = hostRaw.lowercase()
        if (host.isEmpty()) return false
        return when (rule) {
            is NetRule.Host ->
                host == rule.host || host.endsWith("." + rule.host)
            is NetRule.Url ->
                urlKey(scheme, host, pathRaw) == rule.url
        }
    }

    fun selectorsForHost(
        rules: List<CosmeticRule>,
        pageHost: String
    ): List<String> {
        val host = pageHost.lowercase()
        return rules.filter { it.host == host }.map { it.selector }
    }
}
