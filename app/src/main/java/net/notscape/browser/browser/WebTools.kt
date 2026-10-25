package net.notscape.browser.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
        // AdGuard Base (0.0.962) - AdGuard's flagship list; the set
        // carried their Tracking and Mobile lists but not this one.
        // Appended LAST so the cached-list indices never shift.
        "https://filters.adtidy.org/extension/ublock/filters/2.txt"
    )

    @Volatile private var listHosts: Set<String> = emptySet()
    @Volatile private var exceptionHosts: Set<String> = emptySet()
    @Volatile private var scopedRules: Map<String, List<ScopedRule>> = emptyMap()
    @Volatile private var genericSelectors: List<String> = emptyList()
    @Volatile private var domainSelectors: Map<String, List<String>> = emptyMap()
    @Volatile private var docHosts: Set<String> = emptySet()
    @Volatile private var cosmeticExcGeneric: Set<String> = emptySet()
    @Volatile private var cosmeticExcDomain: Map<String, List<String>> = emptyMap()
    @Volatile private var genericHideOffHosts: Set<String> = emptySet()

    /** The document-start cosmetic script (0.0.99995),
     * rebuilt whenever filter data lands; "" until the
     * first lists load. Registered on new WebViews so the
     * generic sheet applies before the page paints. */
    @Volatile var documentStartScript: String = ""
        private set
    @Volatile private var cssCache: Pair<String, String>? = null

    /** The user's own picker rules (beta.18): set
     * from the browser whenever the stored rules
     * change, read on the request thread. Network
     * rules beat the compiled lists; cosmetic rules
     * join the sheet for their exact host. Setting
     * either busts the cosmetic cache. */
    @Volatile var userNetRules: List<UserBlockRules.NetRule> = emptyList()
    @Volatile var userCosmeticRules: List<UserBlockRules.CosmeticRule> =
        emptyList()
        set(value) {
            field = value
            cssCache = null
        }

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

    private const val COMPILED_MAGIC = 0x4E464331
    private const val COMPILED_VERSION = 1

    var ruleCount by mutableStateOf(0)
        private set
    var updatedAt by mutableStateOf(0L)
        private set
    var updating by mutableStateOf(false)
        private set

    /** Requests blocked this session - shown in Settings so the
     *  blocking can be seen firing, not taken on faith. */
    var blockedCount by mutableStateOf(0)
        private set

    fun noteBlocked() { blockedCount++ }

    /** Resource-type rule options, ABP and AdGuard spellings. */
    private val TYPE_OPTIONS = setOf(
        "script", "image", "stylesheet", "css", "font", "media",
        "object", "object-subrequest", "xmlhttprequest", "xhr",
        "subdocument", "frame", "document", "popup", "websocket",
        "webrtc", "other", "ping", "beacon", "manifest", "empty", "mp4"
    )

    private fun dir(context: Context) = File(context.filesDir, "filters").apply { mkdirs() }

    /** Load cached lists into memory; refresh in the background if stale (>24 hours). */
    fun initAsync(context: Context) {
        val appCtx = context.applicationContext
        Thread {
            loadFromDisk(appCtx)
            val missing = listUrls.indices.any { !File(dir(appCtx), "list$it.txt").exists() }
            val stale = updatedAt == 0L || System.currentTimeMillis() - updatedAt > 24L * 3600 * 1000
            if (stale || missing) refresh(appCtx)
        }.start()
    }

    fun refreshAsync(context: Context) {
        val appCtx = context.applicationContext
        Thread { refresh(appCtx) }.start()
    }

    /** The ten parsed collections, bundled so load,
     * refresh validation and the compiled cache can pass
     * one thing around (0.0.997). */
    internal class FilterData {
        val hosts = HashSet<String>()
        val exceptions = HashSet<String>()
        val scoped = HashMap<String, MutableList<ScopedRule>>()
        val generic = LinkedHashSet<String>()
        val perDomain = HashMap<String, MutableList<String>>()
        val docHosts = HashSet<String>()
        val excGeneric = HashSet<String>()
        val excDomain = HashMap<String, MutableList<String>>()
        val hideOff = HashSet<String>()

        fun isEmpty() = hosts.isEmpty() && scoped.isEmpty() &&
            generic.isEmpty()

        fun total() = hosts.size +
            scoped.values.sumOf { it.size } + generic.size

        fun merge(other: FilterData) {
            hosts.addAll(other.hosts)
            exceptions.addAll(other.exceptions)
            other.scoped.forEach { (k, v) ->
                scoped.getOrPut(k) { mutableListOf() }.addAll(v)
            }
            generic.addAll(other.generic)
            other.perDomain.forEach { (k, v) ->
                perDomain.getOrPut(k) { mutableListOf() }.addAll(v)
            }
            docHosts.addAll(other.docHosts)
            excGeneric.addAll(other.excGeneric)
            other.excDomain.forEach { (k, v) ->
                excDomain.getOrPut(k) { mutableListOf() }.addAll(v)
            }
            hideOff.addAll(other.hideOff)
        }
    }

    private fun parseInto(i: Int, text: String, data: FilterData) {
        if (i == 2) parseHosts(text, data.hosts)
        else parse(
            text, data.hosts, data.exceptions, data.scoped,
            data.generic, data.perDomain, data.docHosts,
            data.excGeneric, data.excDomain, data.hideOff
        )
    }

    private fun applyData(data: FilterData, newest: Long) {
        listHosts = data.hosts
        exceptionHosts = data.exceptions
        scopedRules = data.scoped
        genericSelectors = data.generic.toList()
        domainSelectors = data.perDomain
        docHosts = data.docHosts
        cosmeticExcGeneric = data.excGeneric
        cosmeticExcDomain = data.excDomain
        genericHideOffHosts = data.hideOff
        cssCache = null
        documentStartScript = buildDocumentStartScript(
            chunkCosmeticSelectors(
                genericSelectors.filter {
                    it !in cosmeticExcGeneric
                },
                budget = 150_000
            ),
            genericHideOffHosts
        )
        ruleCount = data.hosts.size +
            data.scoped.values.sumOf { it.size }
        updatedAt = newest
    }

    private fun loadFromDisk(context: Context) {
        val d = dir(context)
        // Compiled cache first (0.0.997): if the binary cache
        // matches the on-disk lists exactly, it IS the parse.
        if (loadCompiled(d)) return
        val total = FilterData()
        var newest = 0L
        listUrls.forEachIndexed { i, _ ->
            val f = File(d, "list$i.txt")
            val bak = File(d, "list$i.txt.bak")
            val srcFile = when {
                f.exists() -> f
                bak.exists() -> bak
                else -> null
            } ?: return@forEachIndexed
            newest = maxOf(newest, srcFile.lastModified())
            val one = FilterData()
            runCatching { parseInto(i, srcFile.readText(), one) }
            // Rollback: a list file that parses to nothing
            // (truncated or corrupt) falls back to the
            // previous good copy kept beside it.
            if (one.isEmpty() && srcFile == f && bak.exists()) {
                val fromBak = FilterData()
                runCatching { parseInto(i, bak.readText(), fromBak) }
                if (!fromBak.isEmpty()) {
                    total.merge(fromBak)
                    return@forEachIndexed
                }
            }
            total.merge(one)
        }
        // Sanity guard: a bad/truncated download must never wipe a good
        // rule set - if the freshly parsed hosts collapse versus what's
        // already loaded, keep the loaded rules.
        if (total.hosts.isNotEmpty() && listHosts.isNotEmpty() &&
            total.hosts.size < listHosts.size / 4
        ) {
            return
        }
        if (total.hosts.isNotEmpty()) {
            applyData(total, newest)
            writeCompiled(d, total, newest)
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
                    conn.disconnect()
                    // Per-format sanity: a captive-portal HTML page or an
                    // error body must not replace a good list on disk.
                    val sane = text.length > 50_000 && when (i) {
                        2 -> text.contains("0.0.0.0")
                        else -> text.contains("||") && !text.trimStart().startsWith("<")
                    }
                    if (!sane) return@runCatching
                    // Validate-and-rollback (0.0.997): parse the
                    // candidate BEFORE it touches disk. Commit
                    // only if it parses to a real rule set and -
                    // when a copy is already on disk - not to a
                    // collapse versus that copy. The previous
                    // good file is kept as .bak; the commit goes
                    // through a staging file + rename so a crash
                    // mid-write can't leave a torn list.
                    val candidate = FilterData()
                    parseInto(i, text, candidate)
                    if (candidate.isEmpty()) return@runCatching
                    val target = File(d, "list$i.txt")
                    if (target.exists()) {
                        val current = FilterData()
                        runCatching {
                            parseInto(i, target.readText(), current)
                        }
                        if (!current.isEmpty() &&
                            candidate.total() < current.total() / 2
                        ) {
                            return@runCatching
                        }
                        runCatching {
                            target.copyTo(
                                File(d, "list$i.txt.bak"),
                                overwrite = true
                            )
                        }
                    }
                    val tmp = File(d, "list$i.txt.tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                    ok = true
                }
            }
            if (ok) loadFromDisk(context)
        } finally {
            updating = false
        }
    }

    // ---- Compiled cache (0.0.997) ----
    // Parsing seven ABP lists from text costs real background
    // CPU on every cold start. After any healthy full parse,
    // the structures are written to compiled.bin; a later
    // start loads the binary instead - but only when its
    // fingerprint (every list file's length + mtime) still
    // matches the files on disk exactly. Any doubt at all
    // falls back to the full text parse, which rewrites the
    // cache. The format is versioned and all-or-nothing.

    private fun fingerprint(d: File): LongArray {
        val out = LongArray(listUrls.size * 2)
        listUrls.indices.forEach { i ->
            val main = File(d, "list$i.txt")
            val bak = File(d, "list$i.txt.bak")
            val srcFile = when {
                main.exists() -> main
                bak.exists() -> bak
                else -> null
            }
            out[i * 2] = srcFile?.length() ?: -1L
            out[i * 2 + 1] = srcFile?.lastModified() ?: -1L
        }
        return out
    }

    private fun writeStr(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readStr(inp: DataInputStream): String {
        val n = inp.readInt()
        require(n in 0..1_000_000)
        val bytes = ByteArray(n)
        inp.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun writeStrSet(out: DataOutputStream, set: Set<String>) {
        out.writeInt(set.size)
        set.forEach { writeStr(out, it) }
    }

    private fun readStrSet(inp: DataInputStream): Set<String> {
        val n = inp.readInt()
        require(n in 0..2_000_000)
        val set = HashSet<String>()
        repeat(n) { set.add(readStr(inp)) }
        return set
    }

    private fun writeStrList(out: DataOutputStream, list: List<String>) {
        out.writeInt(list.size)
        list.forEach { writeStr(out, it) }
    }

    private fun readStrList(inp: DataInputStream): List<String> {
        val n = inp.readInt()
        require(n in 0..2_000_000)
        return List(n) { readStr(inp) }
    }

    private fun writeStrListMap(
        out: DataOutputStream, map: Map<String, List<String>>
    ) {
        out.writeInt(map.size)
        map.forEach { (k, v) ->
            writeStr(out, k)
            writeStrList(out, v)
        }
    }

    private fun readStrListMap(
        inp: DataInputStream,
        into: MutableMap<String, MutableList<String>>
    ) {
        val n = inp.readInt()
        require(n in 0..500_000)
        repeat(n) {
            val key = readStr(inp)
            into[key] = readStrList(inp).toMutableList()
        }
    }

    private fun writeNullableStr(out: DataOutputStream, s: String?) {
        out.writeBoolean(s != null)
        if (s != null) writeStr(out, s)
    }

    private fun readNullableStr(inp: DataInputStream): String? =
        if (inp.readBoolean()) readStr(inp) else null

    private fun writeNullableStrSet(
        out: DataOutputStream, set: Set<String>?
    ) {
        out.writeBoolean(set != null)
        if (set != null) writeStrSet(out, set)
    }

    private fun readNullableStrSet(
        inp: DataInputStream
    ): Set<String>? =
        if (inp.readBoolean()) readStrSet(inp) else null

    internal fun writeCompiled(
        d: File, data: FilterData, newest: Long
    ) {
        runCatching {
            val tmp = File(d, "compiled.bin.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(COMPILED_MAGIC)
                out.writeInt(COMPILED_VERSION)
                fingerprint(d).forEach { out.writeLong(it) }
                writeStrSet(out, data.hosts)
                writeStrSet(out, data.exceptions)
                out.writeInt(data.scoped.size)
                data.scoped.forEach { (key, list) ->
                    writeStr(out, key)
                    out.writeInt(list.size)
                    list.forEach { rule ->
                        writeNullableStr(out, rule.pathPrefix)
                        writeNullableStrSet(out, rule.domains)
                        writeNullableStrSet(out, rule.notDomains)
                        out.writeBoolean(rule.exception)
                        out.writeByte(
                            when (rule.thirdParty) {
                                null -> -1
                                false -> 0
                                else -> 1
                            }
                        )
                        out.writeBoolean(rule.important)
                    }
                }
                writeStrList(out, data.generic.toList())
                writeStrListMap(out, data.perDomain)
                writeStrSet(out, data.docHosts)
                writeStrSet(out, data.excGeneric)
                writeStrListMap(out, data.excDomain)
                writeStrSet(out, data.hideOff)
                out.writeLong(newest)
            }
            val target = File(d, "compiled.bin")
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    /** Loads the compiled cache if - and only if - it is
     * intact and its fingerprint matches the list files on
     * disk exactly. Returns false on any doubt. */
    internal fun readCompiled(d: File): Pair<FilterData, Long>? {
        val f = File(d, "compiled.bin")
        if (!f.exists()) return null
        return runCatching {
            DataInputStream(f.inputStream().buffered()).use { inp ->
                if (inp.readInt() != COMPILED_MAGIC) return null
                if (inp.readInt() != COMPILED_VERSION) return null
                val stored = LongArray(listUrls.size * 2) {
                    inp.readLong()
                }
                if (!stored.contentEquals(fingerprint(d))) {
                    return null
                }
                val data = FilterData()
                data.hosts.addAll(readStrSet(inp))
                data.exceptions.addAll(readStrSet(inp))
                val scopedCount = inp.readInt()
                require(scopedCount in 0..500_000)
                repeat(scopedCount) {
                    val key = readStr(inp)
                    val ruleCountIn = inp.readInt()
                    require(ruleCountIn in 0..100_000)
                    val list = mutableListOf<ScopedRule>()
                    repeat(ruleCountIn) {
                        list.add(
                            ScopedRule(
                                pathPrefix = readNullableStr(inp),
                                domains = readNullableStrSet(inp),
                                notDomains = readNullableStrSet(inp),
                                exception = inp.readBoolean(),
                                thirdParty = when (
                                    inp.readByte().toInt()
                                ) {
                                    -1 -> null
                                    0 -> false
                                    else -> true
                                },
                                important = inp.readBoolean()
                            )
                        )
                    }
                    data.scoped[key] = list
                }
                data.generic.addAll(readStrList(inp))
                readStrListMap(inp, data.perDomain)
                data.docHosts.addAll(readStrSet(inp))
                data.excGeneric.addAll(readStrSet(inp))
                readStrListMap(inp, data.excDomain)
                data.hideOff.addAll(readStrSet(inp))
                val newest = inp.readLong()
                if (data.hosts.isEmpty()) null
                else data to newest
            }
        }.getOrNull()
    }

    private fun loadCompiled(d: File): Boolean {
        val (data, newest) = readCompiled(d) ?: return false
        // Same collapse guard as the text path.
        if (listHosts.isNotEmpty() &&
            data.hosts.size < listHosts.size / 4
        ) {
            return false
        }
        applyData(data, newest)
        return true
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
        perDomain: MutableMap<String, MutableList<String>>,
        docHosts: MutableSet<String>,
        genericExc: MutableSet<String>,
        perDomainExc: MutableMap<String, MutableList<String>>,
        hideOff: MutableSet<String>
    ) {
        // $badfilter pass: a badfilter rule disables its twin. Collect
        // the twins' signatures first (the badfilter line may precede
        // the rule it disables), then skip both in the main pass.
        val badSignatures = HashSet<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.contains("badfilter")) {
                ruleSignature(line)?.let { badSignatures.add(it) }
            }
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
            if (!line.startsWith("|") && !line.startsWith("@@")) {
                // Cosmetic rules: ## hides, #@# unhides. (#$# / #?#
                // extended rules stay out - they are not plain CSS.)
                val excIdx = line.indexOf("#@#")
                val hideIdx = line.indexOf("##")
                val idx = when {
                    excIdx >= 0 -> excIdx
                    hideIdx >= 0 -> hideIdx
                    else -> -1
                }
                if (idx >= 0 && !line.contains("#$#") && !line.contains("#?#")) {
                    val isExc = excIdx >= 0
                    val selector = line.substring(idx + if (isExc) 3 else 2).trim()
                    if (isCssSelector(selector)) {
                        val domainPart = line.substring(0, idx).trim()
                        val positives = ArrayList<String>()
                        val negatives = ArrayList<String>()
                        if (domainPart.isNotEmpty()) {
                            for (dom in domainPart.split(',')) {
                                val d = dom.trim().lowercase()
                                if (d.startsWith("~")) negatives.add(d.substring(1))
                                else if (d.isNotEmpty() && d.contains('.')) positives.add(d)
                            }
                        }
                        fun excFor(d: String) {
                            if (d.contains('.')) {
                                val list = perDomainExc.getOrPut(d) { mutableListOf() }
                                if (selector !in list) list.add(selector)
                            }
                        }
                        if (isExc) {
                            when {
                                positives.isNotEmpty() -> for (d in positives) {
                                    val list = perDomainExc.getOrPut(d) { mutableListOf() }
                                    if (selector !in list) list.add(selector)
                                }
                                domainPart.isEmpty() -> genericExc.add(selector)
                            }
                        } else {
                            when {
                                positives.isNotEmpty() -> {
                                    for (d in positives) {
                                        val list = perDomain.getOrPut(d) { mutableListOf() }
                                        if (selector !in list) list.add(selector)
                                    }
                                    for (d in negatives) excFor(d)
                                }
                                negatives.isNotEmpty() -> {
                                    generic.add(selector)
                                    for (d in negatives) excFor(d)
                                }
                                else -> generic.add(selector)
                            }
                        }
                    }
                }
                continue
            }
            if (line.startsWith("@@") && !line.startsWith("@@||")) {
                // Page-level cosmetic exceptions with no host anchor
                // (@@$generichide,domain=a|b): the named domains lose
                // generic hiding. Nothing else in this shape is
                // honourable here.
                if (line.contains("generichide") || line.contains("ghide")) {
                    val dm = line.indexOf("domain=")
                    if (dm >= 0) {
                        val rest = line.substring(dm + 7)
                        val end = rest.indexOf('$')
                        val domPart = if (end >= 0) rest.substring(0, end) else rest
                        for (dd in domPart.split('|')) {
                            val d = dd.trim().lowercase()
                            if (d.isNotEmpty() && !d.startsWith("~") && d.contains('.')) {
                                hideOff.add(d)
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
            val bodyRaw = if (dollar >= 0) body0.substring(0, dollar) else body0
            // ||*.ads.example.com^ - a leading wildcard is exactly
            // what our parent-walking matcher already does (a listed
            // host covers its subdomains), so it is stripped here,
            // before the host scan (which treats '*' as a
            // terminator). Interior wildcards still skip the rule.
            val body = if (bodyRaw.startsWith("*.")) bodyRaw.substring(2) else bodyRaw
            val options = if (dollar >= 0) body0.substring(dollar + 1) else ""
            if (options.split(',').any { it.trim().lowercase() == "badfilter" }) continue
            if (badSignatures.isNotEmpty() &&
                ruleSignature(line) in badSignatures
            ) continue
            var domains: Set<String>? = null
            var notDomains: Set<String>? = null
            var thirdParty: Boolean? = null
            var important = false
            var hideModifier = false
            var docType = false
            var otherType = false
            var optionsOk = true
            if (options.isNotEmpty()) {
                for (opt in options.split(',')) {
                    val o = opt.trim().lowercase()
                    when {
                        // Party options, ABP and AdGuard spellings.
                        o == "third-party" || o == "3p" || o == "~1p" -> thirdParty = true
                        o == "~third-party" || o == "~3p" || o == "1p" -> thirdParty = false
                        o == "important" -> important = true
                        // Resource-type options ($script, $image,
                        // $document, $~image, ...): the rule DOES
                        // apply. WebView interception cannot split
                        // requests by resource type, so the host/path
                        // part is honoured for every type. Dropping
                        // these rules (the old gate) silently lost
                        // thousands of live list rules, including
                        // most $document popunder hosts.
                        o in TYPE_OPTIONS ||
                            (o.startsWith("~") && o.substring(1) in TYPE_OPTIONS) -> {
                            val base = o.removePrefix("~")
                            if (base == "document" || base == "popup") docType = true
                            else otherType = true
                        }
                        // Cosmetic-scope modifiers: recognised so the
                        // rule is not dropped whole; resolved after the
                        // loop (exceptions) or ignored (block rules,
                        // where the network part still applies).
                        o == "generichide" || o == "ghide" || o == "elemhide" -> hideModifier = true
                        o == "match-case" || o == "all" -> Unit
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
            if (exception && hideModifier && !otherType &&
                thirdParty == null && !important
            ) {
                // A cosmetic-scope exception (@@||host^$generichide
                // and friends): it governs generic hiding on pages,
                // never network requests, so it never reaches the
                // network sets. The $domain= set wins; otherwise the
                // rule's own host is the page scope.
                val targets = domains
                if (targets != null) {
                    hideOff.addAll(targets)
                } else {
                    var he = body.length
                    for (i in body.indices) {
                        val c = body[i]
                        if (c == '/' || c == '^' || c == '|' || c == '*') { he = i; break }
                    }
                    val h = body.substring(0, he).trim().lowercase()
                    if (h.contains('.')) hideOff.add(h)
                }
                continue
            }
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
                    // Popunder provenance for the navigation guard:
                    // a pure host whose ONLY type options are
                    // $document/$popup.
                    if (!exception && docType && !otherType) docHosts.add(host)
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

    /** Identity of a || rule for $badfilter matching: exception flag,
     *  body, and the option set with badfilter removed (order-free). */
    private fun ruleSignature(line: String): String? {
        val exception = line.startsWith("@@")
        val body0 = when {
            line.startsWith("@@||") -> line.substring(4)
            line.startsWith("||") -> line.substring(2)
            else -> return null
        }
        val dollar = body0.indexOf('$')
        val body = if (dollar >= 0) body0.substring(0, dollar) else body0
        val options = if (dollar >= 0) body0.substring(dollar + 1) else ""
        val opts = options.split(',').map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != "badfilter" }.sorted()
        return (if (exception) "@@" else "") + body + "$" + opts.joinToString(",")
    }

    /**
     * Plain-CSS selector gate (widened 0.0.962): combinators, commas
     * and functional :not()/:has() are all valid CSS that Chromium
     * evaluates natively, and the measured lists carry ~10k such
     * rules the old gate dropped. Procedural pseudos (:contains,
     * :has-text, :xpath, :style(), ...) are NOT CSS and stay out -
     * one invalid selector would void its whole rule (the sheet is
     * chunked in cosmeticCss to bound even that).
     */
    private fun isCssSelector(sel: String): Boolean {
        if (sel.isEmpty() || sel.length > 200) return false
        val banned = listOf(
            ":contains", ":has-text", ":xpath", ":matches-css",
            ":matches-attr", ":style(", ":remove(", ":upward",
            ":-abp-", ":if(", ":min-text-length", ":watch-attr", "::"
        )
        if (banned.any { sel.contains(it) }) return false
        return sel.all { it.isLetterOrDigit() || it in "#.[]=\"'^$*|_- >+~,()%!:" }
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

    /** True when the request is cross-site relative to the page:
     *  anything not on the page's own registrable domain (eTLD+1)
     *  is third-party — so sibling subdomains of a co.uk-style site
     *  count as first-party, as they should. */
    private fun isThirdParty(reqHost: String, pageHost: String): Boolean {
        if (pageHost.isEmpty() || reqHost == pageHost) return false
        return !Hosts.sameSite(reqHost, pageHost)
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
        // The user's own rules (beta.18) beat the
        // compiled lists: an explicit tap to block
        // something is the strongest signal there is.
        // They still honour the global and per-site
        // blocking toggles (the early returns above
        // and the caller's blockingOff), and main
        // frame loads already returned above, so a
        // rule can never break a tapped link.
        if (blockAds) {
            val scheme = uri.scheme ?: ""
            val path = uri.path ?: ""
            if (userNetRules.any {
                    UserBlockRules.netMatches(it, scheme, host, path)
                }
            ) return true
        }
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

    /**
     * True when [host] is a known document/popup ad host: the lists
     * carry a pure ||host^ rule whose only type options are $document
     * and/or $popup - popunder networks, in practice. Consulted by
     * the navigation guard only; typed addresses and tapped links
     * never reach it.
     */
    fun isDocumentHost(host: String): Boolean {
        val h = host.lowercase()
        if (h.isEmpty()) return false
        if (matches(neverBlockHosts, h)) return false
        if (matches(exceptionHosts, h)) return false
        return matches(docHosts, h)
    }

    /**
     * Chunks selectors into rules of 25 with the standard
     * hiding suffix, under a total size budget - the same
     * shape [cosmeticCss] builds inline for its per-host
     * sheet (0.0.99995).
     */
    internal fun chunkCosmeticSelectors(
        sels: Collection<String>, budget: Int
    ): String {
        if (sels.isEmpty()) return ""
        val suffix = "{display:none!important}"
        val sb = StringBuilder()
        var chunk = StringBuilder()
        var inChunk = 0
        fun flushChunk() {
            if (chunk.isNotEmpty()) {
                sb.append(chunk).append(suffix)
                chunk = StringBuilder()
                inChunk = 0
            }
        }
        for (sel in sels) {
            val extra = if (inChunk == 0) sel.length else sel.length + 1
            if (sb.length + chunk.length + extra + suffix.length > budget) break
            if (inChunk > 0) chunk.append(',')
            chunk.append(sel)
            inChunk++
            if (inChunk >= 25) flushChunk()
        }
        flushChunk()
        return sb.toString()
    }

    /**
     * Builds the document-start script (0.0.99995): injects
     * [css] as a style element the moment the document
     * exists, skipping hosts in [offHosts] (the
     * $generichide set, checked in-script against
     * location.hostname because the script is registered
     * before the target host is known). Empty css yields
     * an empty script (nothing registered).
     */
    internal fun buildDocumentStartScript(
        css: String, offHosts: Set<String>
    ): String {
        if (css.isEmpty()) return ""
        val hosts = offHosts.joinToString(",") { h ->
            "\"" + h.filter { it != '"' && it != '\\' } + "\""
        }
        val esc = css
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("</", "<\\/")
        return "(function(){try{var OFF=[HOSTS];if(OFF.indexOf(location.hostname)>=0)return;var s=document.createElement('style');s.textContent='CSS';(document.documentElement||document).appendChild(s);}catch(e){}})();"
            .replace("HOSTS", hosts)
            .replace("CSS", esc)
    }

    /** CSS that hides ad containers on [pageHost]; "" when nothing applies. */
    fun cosmeticCss(pageHost: String): String {
        val host = pageHost.lowercase()
        if (host.isEmpty()) return ""
        cssCache?.let { if (it.first == host) return it.second }
        val sels = LinkedHashSet<String>()
        // A $generichide exception for this page host turns generic
        // hiding off here (domain rules still apply).
        if (!matches(genericHideOffHosts, host)) sels.addAll(genericSelectors)
        for (h in hostChain(host)) domainSelectors[h]?.let { sels.addAll(it) }
        // The user's own picked selectors (beta.18):
        // exact host only, never inherited up the
        // chain the way list selectors are.
        sels.addAll(UserBlockRules.selectorsForHost(userCosmeticRules, host))
        // #@# unhide exceptions subtract, exact selector for exact
        // selector - the shape list authors actually write.
        sels.removeAll(cosmeticExcGeneric)
        for (h in hostChain(host)) cosmeticExcDomain[h]?.let { sels.removeAll(it) }
        if (sels.isEmpty()) return ""
        // No silent count cap on selectors. The only guard is a
        // generous size budget for the injected <style>; past it,
        // selectors are trimmed (generic first) instead of dropping
        // the whole sheet. Rules are CHUNKED 25 selectors at a time:
        // one invalid selector voids only its own rule in CSS, so a
        // bad selector can never take the whole sheet down with it.
        val suffix = "{display:none!important}"
        val budget = 200_000
        val sb = StringBuilder()
        var chunk = StringBuilder()
        var inChunk = 0
        fun flushChunk() {
            if (chunk.isNotEmpty()) {
                sb.append(chunk).append(suffix)
                chunk = StringBuilder()
                inChunk = 0
            }
        }
        for (sel in sels) {
            val extra = if (inChunk == 0) sel.length else sel.length + 1
            if (sb.length + chunk.length + extra + suffix.length > budget) break
            if (inChunk > 0) chunk.append(',')
            chunk.append(sel)
            inChunk++
            if (inChunk >= 25) flushChunk()
        }
        flushChunk()
        if (sb.isEmpty()) return ""
        val css = sb.toString()
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
