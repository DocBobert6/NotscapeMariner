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
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        // AdGuard Tracking Protection — the trackers EasyPrivacy misses.
        "https://filters.adtidy.org/extension/ublock/filters/3.txt",
        // AdGuard Mobile Ads — in-app/mobile-web ad networks.
        "https://filters.adtidy.org/extension/ublock/filters/11.txt",
        // uBlock Origin's own filters — extra annoyance/ad coverage in
        // the same ABP syntax this engine compiles.
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt"
    )

    @Volatile private var listHosts: Set<String> = emptySet()
    @Volatile private var exceptionHosts: Set<String> = emptySet()
    @Volatile private var scopedRules: Map<String, List<ScopedRule>> = emptyMap()
    @Volatile private var genericSelectors: List<String> = emptyList()
    @Volatile private var domainSelectors: Map<String, List<String>> = emptyMap()
    @Volatile private var cssCache: Pair<String, String>? = null

    /**
     * An ABP rule that only applies under conditions: a path prefix on
     * the request host, and/or only on pages whose host is (or is not)
     * in a $domain= list. This is the honest middle ground between
     * ignoring scoped rules entirely and the original bug — flattening
     * them into global host blocks, which took out YouTube (the
     * googlevideo.com rule is restricted to two named sites) and the
     * JW Player hosts (restricted to a few publishers).
     */
    class ScopedRule(
        val pathPrefix: String?,
        val domains: Set<String>?,
        val notDomains: Set<String>?,
        val exception: Boolean,
        /** null = any party; true = only third-party requests; false = only first-party. */
        val thirdParty: Boolean? = null,
        /** $important: beats exception (@@) rules. */
        val important: Boolean = false
    )

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
        val scoped = HashMap<String, MutableList<ScopedRule>>()
        val generic = LinkedHashSet<String>()
        val perDomain = HashMap<String, MutableList<String>>()
        var newest = 0L
        listUrls.forEachIndexed { i, _ ->
            val f = File(d, "list$i.txt")
            if (f.exists()) {
                newest = maxOf(newest, f.lastModified())
                if (i == 2) parseHosts(f.readText(), hosts)
                else parse(f.readText(), hosts, exceptions, scoped, generic, perDomain)
            }
        }
        // Sanity guard: a bad/truncated download must never wipe a good
        // rule set — if the freshly parsed hosts collapse versus what's
        // already loaded, keep the loaded rules.
        if (hosts.isNotEmpty() && listHosts.isNotEmpty() && hosts.size < listHosts.size / 4) {
            return
        }
        if (hosts.isNotEmpty()) {
            listHosts = hosts
            exceptionHosts = exceptions
            scopedRules = scoped
            genericSelectors = generic.toList()
            domainSelectors = perDomain
            cssCache = null
            ruleCount = hosts.size + scoped.values.sumOf { it.size }
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
                    // Per-format sanity: a captive-portal HTML page or an
                    // error body must not replace a good list on disk.
                    val sane = text.length > 50_000 && when (i) {
                        2 -> text.contains("0.0.0.0")
                        else -> text.contains("||") && !text.trimStart().startsWith("<")
                    }
                    if (sane) {
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
     * Compile ABP rules into three structures:
     *  - pure ||host^ rules → the flat host sets;
     *  - path-scoped and $domain=-scoped rules → [ScopedRule]s keyed by
     *    host, applied only when their conditions match (rules carrying
     *    other $ options — types, $rewrite — are still skipped);
     *  - cosmetic ## rules with simple selectors → generic and
     *    per-domain hide lists, injected as CSS per page.
     */
    internal fun parse(
        text: String,
        hosts: MutableSet<String>,
        exceptions: MutableSet<String>,
        scoped: MutableMap<String, MutableList<ScopedRule>>,
        generic: MutableSet<String>,
        perDomain: MutableMap<String, MutableList<String>>
    ) {
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
            if (!line.startsWith("|") && !line.startsWith("@@")) {
                val hashIdx = line.indexOf("##")
                if (hashIdx >= 0 && !line.contains("#@#") && !line.contains("#$#") &&
                    !line.contains("#?#")
                ) {
                    val selector = line.substring(hashIdx + 2).trim()
                    if (isSimpleSelector(selector)) {
                        val domainPart = line.substring(0, hashIdx).trim()
                        if (domainPart.isEmpty()) {
                            generic.add(selector)
                        } else {
                            for (dom in domainPart.split(',')) {
                                val d = dom.trim().lowercase()
                                if (d.isNotEmpty() && !d.startsWith("~") && d.contains('.')) {
                                    val list = perDomain.getOrPut(d) { mutableListOf() }
                                    if (selector !in list) list.add(selector)
                                }
                            }
                        }
                    }
                }
                continue
            }
            val exception = line.startsWith("@@")
            val body0 = when {
                line.startsWith("@@||") -> line.substring(4)
                line.startsWith("||") -> line.substring(2)
                else -> continue
            }
            val dollar = body0.indexOf('$')
            val body = if (dollar >= 0) body0.substring(0, dollar) else body0
            val options = if (dollar >= 0) body0.substring(dollar + 1) else ""
            var domains: Set<String>? = null
            var notDomains: Set<String>? = null
            var thirdParty: Boolean? = null
            var important = false
            var optionsOk = true
            if (options.isNotEmpty()) {
                for (opt in options.split(',')) {
                    val o = opt.trim().lowercase()
                    when {
                        o == "third-party" -> thirdParty = true
                        o == "~third-party" -> thirdParty = false
                        o == "important" -> important = true
                        o.startsWith("domain=") -> {
                            val pos = HashSet<String>()
                            val neg = HashSet<String>()
                            for (dd in o.substring(7).split('|')) {
                                val d = dd.trim()
                                if (d.startsWith("~")) neg.add(d.substring(1))
                                else if (d.isNotEmpty()) pos.add(d)
                            }
                            domains = pos.ifEmpty { null }
                            notDomains = neg.ifEmpty { null }
                        }
                        else -> { optionsOk = false; break }
                    }
                }
            }
            if (!optionsOk) continue
            var hostEnd = body.length
            for (i in body.indices) {
                val c = body[i]
                if (c == '/' || c == '^' || c == '|' || c == '*') { hostEnd = i; break }
            }
            val host = body.substring(0, hostEnd).trim().lowercase()
            if (!host.contains('.') ||
                !host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
            ) continue
            val rest = body.substring(hostEnd)
            val isScoped = domains != null || notDomains != null || thirdParty != null || important
            if (rest.isEmpty() || rest == "^") {
                if (isScoped) {
                    scoped.getOrPut(host) { mutableListOf() }
                        .add(ScopedRule(null, domains, notDomains, exception, thirdParty, important))
                } else {
                    (if (exception) exceptions else hosts).add(host)
                }
                continue
            }
            if (rest.startsWith("/")) {
                var path = rest
                val star = path.indexOf('*')
                if (star >= 0) path = path.substring(0, star)
                val caret = path.indexOf('^')
                if (caret >= 0) path = path.substring(0, caret)
                val pipe = path.indexOf('|')
                if (pipe >= 0) path = path.substring(0, pipe)
                if (path.length < 2) continue
                scoped.getOrPut(host) { mutableListOf() }
                    .add(ScopedRule(path.lowercase(), domains, notDomains, exception, thirdParty, important))
                continue
            }
            // Anchored/wildcard leftovers — skip.
        }
    }

    private fun isSimpleSelector(sel: String): Boolean {
        if (sel.isEmpty() || sel.length > 120) return false
        if (sel.contains(',') || sel.contains('>') || sel.contains('+') ||
            sel.contains('~') || sel.contains(":has") || sel.contains(":not") ||
            sel.contains(":-") || sel.contains("::")
        ) return false
        return sel.all { it.isLetterOrDigit() || it in "#.[]=\"'^$*|_- " }
    }

    /** Hosts-file format (0.0.0.0 domain / 127.0.0.1 domain lines). */
    internal fun parseHosts(text: String, hosts: MutableSet<String>) {
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

    /** [host] and each parent domain, longest first. */
    private fun hostChain(host: String): List<String> {
        val out = ArrayList<String>()
        var h = host
        while (true) {
            out.add(h)
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
            if (!h.contains('.')) { out.add(h); break }
        }
        return out
    }

    /** True when the request is cross-site relative to the page. */
    private fun isThirdParty(reqHost: String, pageHost: String): Boolean {
        if (pageHost.isEmpty() || reqHost == pageHost) return false
        if (matches(setOf(pageHost), reqHost)) return false // subdomain of the page
        if (matches(setOf(reqHost), pageHost)) return false // page is a subdomain of the request host
        return true
    }

    private fun scopedMatch(
        reqHost: String,
        path: String,
        pageHost: String,
        wantException: Boolean,
        onlyImportant: Boolean = false
    ): Boolean {
        for (h in hostChain(reqHost)) {
            val rules = scopedRules[h] ?: continue
            for (r in rules) {
                if (r.exception != wantException) continue
                if (onlyImportant && !r.important) continue
                if (r.domains != null &&
                    (pageHost.isEmpty() || !matches(r.domains, pageHost))
                ) continue
                if (r.notDomains != null && pageHost.isNotEmpty() &&
                    matches(r.notDomains, pageHost)
                ) continue
                if (r.thirdParty != null &&
                    r.thirdParty != isThirdParty(reqHost, pageHost)
                ) continue
                if (r.pathPrefix != null && !path.startsWith(r.pathPrefix)) continue
                return true
            }
        }
        return false
    }

    fun isBlocked(
        uri: Uri,
        isForMainFrame: Boolean,
        blockAds: Boolean,
        blockTrackers: Boolean,
        pageHost: String = ""
    ): Boolean {
        if (isForMainFrame) return false
        if (!blockAds && !blockTrackers) return false
        val host = (uri.host ?: "").lowercase()
        if (host.isEmpty()) return false
        if (matches(neverBlockHosts, host)) return false
        val path = (uri.path ?: "").lowercase()
        val page = pageHost.lowercase()
        // $important block rules beat every exception.
        if (scopedMatch(host, path, page, wantException = false, onlyImportant = true)) return true
        if (matches(exceptionHosts, host)) return false
        if (scopedMatch(host, path, page, wantException = true)) return false
        if (blockTrackers && matches(builtinHosts, host)) return true
        if ((blockAds || blockTrackers) && matches(listHosts, host)) return true
        if (scopedMatch(host, path, page, wantException = false)) return true
        if (blockAds) {
            val s = uri.toString().lowercase()
            if (pathKeywords.any { s.contains(it) }) return true
        }
        return false
    }

    /** CSS that hides ad containers on [pageHost]; "" when nothing applies. */
    fun cosmeticCss(pageHost: String): String {
        val host = pageHost.lowercase()
        if (host.isEmpty()) return ""
        cssCache?.let { if (it.first == host) return it.second }
        val sels = LinkedHashSet<String>()
        sels.addAll(genericSelectors)
        for (h in hostChain(host)) domainSelectors[h]?.let { sels.addAll(it) }
        if (sels.isEmpty()) return ""
        // No silent count cap on selectors. The only guard is a generous
        // CSS size budget for the injected <style>; if a page's combined
        // set ever exceeded it, selectors are trimmed to fit (generic
        // first) instead of dropping the whole sheet.
        val suffix = "{display:none!important}"
        val budget = 200_000 - suffix.length
        val sb = StringBuilder()
        for (sel in sels) {
            val extra = if (sb.isEmpty()) sel.length else sel.length + 1
            if (sb.length + extra > budget) break
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(sel)
        }
        if (sb.isEmpty()) return ""
        val css = sb.toString() + suffix
        cssCache = host to css
        return css
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}

/** Backwards-compatible name used by earlier code paths. */
object Blocker {
    fun isBlocked(uri: Uri, isForMainFrame: Boolean, blockAds: Boolean, blockTrackers: Boolean): Boolean =
        FilterEngine.isBlocked(uri, isForMainFrame, blockAds, blockTrackers, "")

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
  if (og) {
    var ogu = og.getAttribute('content') || '';
    if (/\.(mp4|webm|mkv|mov|m3u8|mpd|mp3|m4a)(\?|#|$)/i.test(ogu)) add(ogu, 'video', 'featured');
    else if (ogu) add(ogu, 'page', 'featured video');
  }
  return JSON.stringify(out);
})()
"""
}

/** True for URLs that are HTML embed/player pages rather than media files. */
fun isEmbedPageUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("youtube.com/embed/") || u.contains("youtube-nocookie.com/embed/") ||
        u.contains("youtube.com/watch") || u.contains("youtu.be/") ||
        u.contains("player.vimeo.com/video/") || u.contains("dailymotion.com/embed/") ||
        u.contains("twitch.tv/") && u.contains("/embed")
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
            val url = o.getString("url")
            var kind = o.optString("kind", "video")
            // Embed pages are HTML, not streams: never offer them to the
            // player (it would sit at "Connecting..." forever).
            if ((kind == "video" || kind == "audio") && isEmbedPageUrl(url)) kind = "page"
            list.add(DetectedMedia(url, kind, o.optString("label")))
        }
        list
    }.getOrElse { emptyList() }
}
