package net.notscape.browser.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Request blocking with real filter lists (EasyList + EasyPrivacy), parsed to
 * host rules and cached on-device, plus a built-in tracker set that works
 * before the first list download and offline thereafter.
 */
object FilterEngine {

    private val builtinHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "analytics.twitter.com", "ads-twitter.com", "connect.facebook.net",
        "facebook.net", "adservice.google.com", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "partner.googleadservices.com",
        "amazon-adsystem.com", "aax.amazon-adsystem.com", "ads.yahoo.com",
        "analytics.yahoo.com", "advertising.com", "adnxs.com", "adsrvr.org",
        "rubiconproject.com", "pubmatic.com", "openx.net", "criteo.com",
        "criteo.net", "taboola.com", "outbrain.com", "scorecardresearch.com",
        "quantserve.com", "moatads.com", "zedo.com", "smartadserver.com",
        "lijit.com", "sovrn.com", "sharethrough.com", "triplelift.com",
        "indexww.com", "casalemedia.com", "contextweb.com", "spotxchange.com",
        "mathtag.com", "bluekai.com", "demdex.net", "krxd.net", "exelator.com",
        "turn.com", "adform.net", "bidswitch.net", "w55c.net", "rlcdn.com",
        "pippio.com", "eyeota.net", "tapad.com", "drawbridge.com",
        "crossdevice.com", "adsymptotic.com", "nr-data.net", "newrelic.com",
        "hotjar.com", "fullstory.com", "mouseflow.com", "crazyegg.com",
        "mixpanel.com", "segment.com", "segment.io", "amplitude.com",
        "branch.io", "adjust.com", "appsflyer.com", "kochava.com",
        "leanplum.com", "clevertap.com", "onesignal.com", "pushwoosh.com",
        "bat.bing.com", "clarity.ms", "mc.yandex.ru",
        "adskeeper.com", "mgid.com", "revcontent.com", "contentad.net",
        "zergnet.com", "newsmaxfeednetwork.com", "disqusads.com",
        // Ad networks a device ad-block test showed as unblocked
        // (2026-10-21): popunder networks used by streaming sites,
        // mobile ad SDKs, and the big platforms' secondary ad domains.
        "propellerads.com", "propellerclick.com", "onclicks.net",
        "popads.net", "popcash.net", "popmyads.com", "clickadu.com",
        "trafficjunky.net", "exoclick.com", "juicyads.com",
        "applovin.com", "unityads.unity3d.com", "adroll.com",
        "advertising.yahoo.com", "ysm.yahoo.com", "adtech.yahooinc.com",
        "bingads.microsoft.com", "ads.microsoft.com",
        "ads.linkedin.com", "px.ads.linkedin.com", "snap.licdn.com",
        "fls-na.amazon.com", "fingerprintjs.com", "fpjs.io",
        "siftscience.com", "permutive.com", "onetag-sys.com",
        "id5-sync.com", "doubleverify.com",
        "coinimp.com", "webminepool.com", "minero.cc", "mineralt.io",
        "monerominer.rocks", "ads.vk.com", "ad.mail.ru",
        "pixel.quora.com", "qevents.quora.com", "px.arvcs.tumblr.com",
        "widgets.pinterest.com", "advertising.apple.com", "xp.apple.com",
        "metrics.mzstatic.com"
    )

    private val pathKeywords = listOf(
        "/ads/", "/adservice", "/pagead/", "/gtag/js", "google-analytics",
        "/analytics.js", "/beacon", "/pixel.gif", "prebid", "adsbygoogle"
    )

    private val listUrls = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        // Hosts-format blocklist (adware/malware domains). Fills the
        // long-tail ad-network gaps the ABP lists only cover with path
        // rules — Propeller, onclicks, AppLovin and similar networks
        // showed up as unblocked in device ad-block tests.
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    )

    @Volatile private var listHosts: Set<String> = emptySet()
    @Volatile private var exceptionHosts: Set<String> = emptySet()

    var ruleCount by mutableStateOf(0)
        private set
    var updatedAt by mutableStateOf(0L)
        private set
    var updating by mutableStateOf(false)
        private set

    private fun dir(context: Context) = File(context.filesDir, "filters").apply { mkdirs() }

    /** Load cached lists into memory; refresh in the background if stale (>7 days). */
    fun initAsync(context: Context) {
        val appCtx = context.applicationContext
        Thread {
            loadFromDisk(appCtx)
            val missing = listUrls.indices.any { !File(dir(appCtx), "list$it.txt").exists() }
            val stale = updatedAt == 0L || System.currentTimeMillis() - updatedAt > 7L * 24 * 3600 * 1000
            if (stale || missing) refresh(appCtx)
        }.start()
    }

    fun refreshAsync(context: Context) {
        val appCtx = context.applicationContext
        Thread { refresh(appCtx) }.start()
    }

    private fun loadFromDisk(context: Context) {
        val d = dir(context)
        val hosts = HashSet<String>()
        val exceptions = HashSet<String>()
        var newest = 0L
        listUrls.forEachIndexed { i, _ ->
            val f = File(d, "list$i.txt")
            if (f.exists()) {
                newest = maxOf(newest, f.lastModified())
                if (i == 2) parseHosts(f.readText(), hosts) else parse(f.readText(), hosts, exceptions)
            }
        }
        if (hosts.isNotEmpty()) {
            listHosts = hosts
            exceptionHosts = exceptions
            ruleCount = hosts.size
            updatedAt = newest
        }
    }

    private fun refresh(context: Context) {
        if (updating) return
        updating = true
        try {
            val d = dir(context)
            var ok = false
            listUrls.forEachIndexed { i, url ->
                runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) NotscapeMariner")
                    val text = conn.inputStream.bufferedReader().readText()
                    if (text.length > 50_000) {
                        File(d, "list$i.txt").writeText(text)
                        ok = true
                    }
                    conn.disconnect()
                }
            }
            if (ok) loadFromDisk(context)
        } finally {
            updating = false
        }
    }

    /**
     * Extract PURE host rules of the form ||host^ (and @@||host^ exceptions).
     *
     * Rules that are path-scoped (||host/path…), wildcarded, or restricted
     * to specific sites via $domain= are skipped on purpose: flattening
     * them into global host blocks once took out googlevideo.com (all
     * YouTube playback), googleapis.com, and the JW Player hosts, because
     * rules like ||jwplayer.com^$domain=<a few publishers> were never
     * meant to apply everywhere.
     */
    private fun parse(text: String, hosts: MutableSet<String>, exceptions: MutableSet<String>) {
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val target: MutableSet<String>
            val body: String
            when {
                line.startsWith("@@||") -> { target = exceptions; body = line.substring(4) }
                line.startsWith("||") -> { target = hosts; body = line.substring(2) }
                else -> continue
            }
            val dollar = body.indexOf('$')
            val hostPart = if (dollar >= 0) body.substring(0, dollar) else body
            val options = if (dollar >= 0) body.substring(dollar + 1) else ""
            if (hostPart.contains('/') || hostPart.contains('*')) continue
            if (!hostPart.endsWith('^')) continue
            if (options.contains("domain=")) continue
            val host = hostPart.dropLast(1).trim().lowercase()
            if (host.contains('.') && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) {
                target.add(host)
            }
        }
    }

    /** Hosts-file format (0.0.0.0 domain / 127.0.0.1 domain lines). */
    private fun parseHosts(text: String, hosts: MutableSet<String>) {
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue
            if (parts[0] != "0.0.0.0" && parts[0] != "127.0.0.1") continue
            val host = parts[1].lowercase()
            if (host.contains('.') && host.any { it.isLetter() } &&
                host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
            ) {
                hosts.add(host)
            }
        }
    }

    /** Player/CDN hosts that must never be host-blocked (see parse note). */
    private val neverBlockHosts = setOf(
        "googlevideo.com", "googleapis.com", "youtube.com", "ytimg.com",
        "ggpht.com", "jwplatform.com", "jwpcdn.com", "jwplayer.com",
        "vimeo.com", "vimeocdn.com", "akamaihd.net", "dailymotion.com",
        "twitch.tv"
    )

    /** True if [host] or any of its parent domains is in [set]. */
    private fun matches(set: Set<String>, host: String): Boolean {
        var h = host
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (!h.contains('.')) return set.contains(h)
        }
    }

    fun isBlocked(uri: Uri, isForMainFrame: Boolean, blockAds: Boolean, blockTrackers: Boolean): Boolean {
        if (isForMainFrame) return false
        if (!blockAds && !blockTrackers) return false
        val host = (uri.host ?: "").lowercase()
        if (host.isEmpty()) return false
        if (matches(neverBlockHosts, host)) return false
        if (matches(exceptionHosts, host)) return false
        if (blockTrackers && matches(builtinHosts, host)) return true
        if ((blockAds || blockTrackers) && matches(listHosts, host)) return true
        if (blockAds) {
            val s = uri.toString().lowercase()
            if (pathKeywords.any { s.contains(it) }) return true
        }
        return false
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}

/** Backwards-compatible name used by earlier code paths. */
object Blocker {
    fun isBlocked(uri: Uri, isForMainFrame: Boolean, blockAds: Boolean, blockTrackers: Boolean): Boolean =
        FilterEngine.isBlocked(uri, isForMainFrame, blockAds, blockTrackers)

    fun emptyResponse(): WebResourceResponse = FilterEngine.emptyResponse()
}

/**
 * Network-level media sniffer: watches the requests a page makes. Embedded
 * players and streaming (MSE/HLS) players reveal their media here even when
 * no <video> tag is visible to a page scan.
 */
object MediaLog {
    private val byTab = mutableStateMapOf<Int, List<DetectedMedia>>()
    var revision by mutableStateOf(0)
        private set

    private val streamPattern = Regex(
        """\.(m3u8|mpd|mp4|webm|mkv|mov|mp3|m4a|aac|ogg|opus|flac|wav|m4v|ts)(\?|#|$)""",
        RegexOption.IGNORE_CASE
    )

    fun inspect(tabId: Int, url: String) {
        if (!streamPattern.containsMatchIn(url)) return
        if (url.contains("blob:")) return
        val lower = url.lowercase()
        val kind = if (Regex("""\.(mp3|m4a|aac|ogg|opus|flac|wav)(\?|#|$)""").containsMatchIn(lower)) "audio" else "video"
        val current = byTab[tabId] ?: emptyList()
        if (current.any { it.url == url }) return
        val label = when {
            lower.contains(".m3u8") -> "stream (HLS)"
            lower.contains(".mpd") -> "stream (DASH)"
            else -> ""
        }
        byTab[tabId] = current + DetectedMedia(url, kind, label)
        revision++
    }

    fun forTab(tabId: Int): List<DetectedMedia> = byTab[tabId] ?: emptyList()

    fun clear(tabId: Int) {
        if (byTab.remove(tabId) != null) revision++
    }
}

/** JavaScript media scan: the page itself, recursing into same-origin iframes,
 *  and noting cross-origin embedded players as openable pages. */
object MediaDetect {
    const val JS = """
(function() {
  var out = [];
  var seen = {};
  function add(u, kind, label) {
    if (!u) return;
    try { u = new URL(u, document.baseURI).href; } catch(e) { return; }
    if (u.indexOf('blob:') === 0) return;
    if (seen[u]) return;
    seen[u] = 1;
    out.push({url: u, kind: kind, label: label || ''});
  }
  function scan(doc, base) {
    try {
      doc.querySelectorAll('video').forEach(function(v) {
        add(v.currentSrc || v.src, 'video', 'video on page');
        v.querySelectorAll('source').forEach(function(s){ add(s.src, 'video', ''); });
      });
      doc.querySelectorAll('audio').forEach(function(a) {
        add(a.currentSrc || a.src, 'audio', 'audio on page');
        a.querySelectorAll('source').forEach(function(s){ add(s.src, 'audio', ''); });
      });
      doc.querySelectorAll('a[href]').forEach(function(a) {
        var h = a.href || '';
        if (/\.(mp4|webm|mkv|mov|m3u8|mpd|mp3|m4a|ogg|opus|flac|wav)(\?|#|$)/i.test(h)) {
          add(h, /\.(mp3|m4a|ogg|opus|flac|wav)(\?|#|$)/i.test(h) ? 'audio' : 'video', (a.textContent || '').trim().slice(0, 80));
        }
      });
      doc.querySelectorAll('iframe').forEach(function(f) {
        var src = f.src || '';
        try {
          var inner = f.contentDocument;
          if (inner) { scan(inner, f.src); return; }
        } catch(e) {}
        if (src && (f.offsetWidth || 0) > 200 && /player|video|embed|stream|watch/i.test(src)) {
          add(src, 'page', 'embedded player');
        }
      });
    } catch(e) {}
  }
  scan(document, location.href);
  var og = document.querySelector('meta[property="og:video"], meta[property="og:video:url"], meta[name="twitter:player:stream"]');
  if (og) add(og.getAttribute('content'), 'video', 'featured');
  return JSON.stringify(out);
})()
"""
}

data class DetectedMedia(val url: String, val kind: String, val label: String)

fun parseDetectedMedia(raw: String?): List<DetectedMedia> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    return runCatching {
        // evaluateJavascript returns the JS string JSON-encoded (double-encoded).
        val inner = org.json.JSONTokener(raw).nextValue() as? String ?: return emptyList()
        val arr = org.json.JSONArray(inner)
        val list = mutableListOf<DetectedMedia>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(DetectedMedia(o.getString("url"), o.optString("kind", "video"), o.optString("label")))
        }
        list
    }.getOrElse { emptyList() }
}
