package net.notscape.browser.browser

import net.notscape.browser.data.AppStore
import org.json.JSONArray
import org.json.JSONObject

/** One user script (0.0.99996). [pattern] is a match
 * pattern (scheme, host, path) tested against the full
 * page URL; a `*` in it matches any run of characters. */
data class UserScript(
    val id: String,
    val name: String,
    val pattern: String,
    val code: String,
    val enabled: Boolean = true
)

/**
 * User scripts (0.0.99996): Greasemonkey-lite on the
 * document-start plane. The list lives as JSON in the
 * `userScripts` setting - no database change. Each
 * enabled script becomes its own document-start blob,
 * so a syntax error in one cannot take the others (or
 * the cosmetic sheet) down with it.
 */
object UserScripts {
    const val SETTING_KEY = "userScripts"
    const val MAX_SCRIPTS = 50
    const val MAX_CODE_CHARS = 100_000

    fun parse(json: String): List<UserScript> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            val out = ArrayList<UserScript>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out += UserScript(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    pattern = o.optString("pattern"),
                    code = o.optString("code"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    fun toJson(scripts: List<UserScript>): String {
        val arr = JSONArray()
        for (s in scripts) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("pattern", s.pattern)
                    .put("code", s.code)
                    .put("enabled", s.enabled)
            )
        }
        return arr.toString()
    }

    fun load(store: AppStore): List<UserScript> =
        parse(store.setting(SETTING_KEY, ""))

    fun save(store: AppStore, scripts: List<UserScript>) {
        store.putSetting(
            SETTING_KEY, toJson(scripts.take(MAX_SCRIPTS))
        )
    }

    /** A match pattern as a JavaScript regex source (no
     * delimiters): `*` becomes `.*`, every other regex
     * special is escaped, and the result is anchored. */
    fun patternToRegexSource(pattern: String): String {
        val sb = StringBuilder("^")
        for (c in pattern.trim()) {
            when (c) {
                '*' -> sb.append(".*")
                '.', '+', '?', '(', ')', '[', ']', '{', '}',
                '^', '$', '|', '\\', '/' ->
                    sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        sb.append("$")
        return sb.toString()
    }

    /** The document-start blob for one script; "" when the
     * script is disabled or has no code. The user code runs
     * in an IIFE inside a try/catch, only on matching URLs. */
    fun startScriptFor(script: UserScript): String {
        if (!script.enabled || script.code.isBlank()) return ""
        val rx = patternToRegexSource(script.pattern)
        return "(function(){try{if(!/" + rx + "/.test(location.href))return;" +
            "(function(){\n" + script.code + "\n})();}catch(e){}})();"
    }
}
