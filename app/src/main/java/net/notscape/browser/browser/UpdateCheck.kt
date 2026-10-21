package net.notscape.browser.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.notscape.browser.BuildConfig
import net.notscape.browser.data.AppStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Quiet update check: asks GitHub (at most once a day) whether a newer
 * release of the app exists. Nothing is downloaded automatically.
 */
object UpdateChecker {

    var availableVersion by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lastCheckedAt by mutableStateOf(0L)
        private set

    private const val LATEST_URL =
        "https://api.github.com/repos/DocBobert6/NotscapeMariner/releases/latest"

    fun checkAsync(store: AppStore, force: Boolean = false) {
        val last = store.setting("lastUpdateCheck", "0").toLongOrNull() ?: 0L
        lastCheckedAt = last
        if (!force && System.currentTimeMillis() - last < 24L * 3600 * 1000) return
        if (checking) return
        checking = true
        Thread {
            try {
                val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "NotscapeMariner")
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val tag = JSONObject(body).optString("tag_name").removePrefix("v")
                    if (tag.isNotBlank() && isNewer(tag, BuildConfig.VERSION_NAME)) {
                        availableVersion = tag
                    } else if (tag.isNotBlank()) {
                        availableVersion = null
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Offline or no releases yet — stay silent.
            } finally {
                val now = System.currentTimeMillis()
                store.putSetting("lastUpdateCheck", now.toString())
                lastCheckedAt = now
                checking = false
            }
        }.start()
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
