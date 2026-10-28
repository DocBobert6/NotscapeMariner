package net.notscape.browser.media

/** One video rendition from a DASH manifest. */
data class DashVariant(
    val bandwidth: Long,
    val width: Int,
    val height: Int
)

/** One downloadable DASH track: an optional init segment plus
 * the media segments in play order, fully resolved. */
data class DashTrackPlan(
    val kind: String,
    val initUrl: String?,
    val segmentUrls: List<String>
)

/** A DASH download plan (0.0.9996): the best video rendition,
 * the best audio rendition when the manifest carries one. */
data class DashDownloadPlan(
    val video: DashTrackPlan,
    val audio: DashTrackPlan?
)

/**
 * Minimal DASH (MPD) manifest reading (0.0.994): the video
 * Representations and their bandwidths, enough to offer the
 * same quality choices the HLS master parser offers. A
 * Representation counts as video when it - or the
 * AdaptationSet wrapping it - declares a video mime type,
 * or when no mime type is declared and it carries
 * dimensions.
 */
object DashManifest {

    private val token =
        Regex("<(AdaptationSet|Representation)\\b[^>]*>")
    private val attr = Regex("(\\w+)\\s*=\\s*\"([^\"]*)\"")

    private fun attrs(tag: String): Map<String, String> =
        attr.findAll(tag)
            .associate { it.groupValues[1] to it.groupValues[2] }

    fun parse(text: String): List<DashVariant> {
        if (!text.contains("<MPD")) return emptyList()
        var setMime = ""
        val out = mutableListOf<DashVariant>()
        for (m in token.findAll(text)) {
            val tag = m.value
            val a = attrs(tag)
            if (tag.startsWith("<AdaptationSet")) {
                setMime = a["mimeType"] ?: ""
            } else {
                val mime = a["mimeType"] ?: setMime
                val w = a["width"]?.toIntOrNull() ?: 0
                val h = a["height"]?.toIntOrNull() ?: 0
                val bw = a["bandwidth"]?.toLongOrNull() ?: 0L
                val isVideo = mime.startsWith("video") ||
                    (mime.isEmpty() && (w > 0 || h > 0))
                if (isVideo && bw > 0) {
                    out.add(DashVariant(bw, w, h))
                }
            }
        }
        return out.sortedByDescending { it.bandwidth }
            .distinctBy { it.bandwidth }
    }

    // ---- Download plans (0.0.9996) ----

    private data class TemplateInfo(
        val media: String,
        val initialization: String?,
        val timescale: Long,
        val duration: Long,
        val startNumber: Int,
        val timeline: List<Long>?
    )

    private data class ListInfo(
        val initUrl: String?,
        val mediaUrls: List<String>
    )

    private fun tagEnd(text: String, from: Int): Int {
        val gt = text.indexOf('>', from)
        return if (gt < 0) text.length else gt + 1
    }

    /** The span [start, end) of element [name] whose open tag sits
     * at or after [from]; self-closing tags end at their own tag. */
    private fun elementSpan(
        text: String,
        name: String,
        from: Int
    ): Pair<Int, Int>? {
        val start = text.indexOf("<$name", from)
        if (start < 0) return null
        val openEnd = tagEnd(text, start)
        if (text.substring(start, openEnd).endsWith("/>")) {
            return start to openEnd
        }
        val close = text.indexOf("</$name>", openEnd)
        return if (close < 0) {
            start to text.length
        } else {
            start to close + name.length + 3
        }
    }

    private fun openTagAttrs(
        text: String,
        span: Pair<Int, Int>
    ): Map<String, String> =
        attrs(text.substring(span.first, tagEnd(text, span.first)))

    private fun baseUrlIn(text: String, from: Int, until: Int): String? {
        val s = text.indexOf("<BaseURL", from)
        if (s < 0 || s >= until) return null
        val e = text.indexOf("</BaseURL>", s)
        if (e < 0 || e > until) return null
        return text.substring(tagEnd(text, s), e).trim()
            .ifEmpty { null }
    }

    private fun resolve(base: String, rel: String): String {
        if (rel.startsWith("http://") || rel.startsWith("https://")) {
            return rel
        }
        return runCatching { java.net.URI(base).resolve(rel).toString() }
            .getOrDefault(rel)
    }

    private fun isoDurationSec(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val m = Regex(
            """P(?:(\d+(?:\.\d+)?)D)?T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?"""
        ).matchEntire(s.trim()) ?: return null
        val d = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val h = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val min = m.groupValues[3].toDoubleOrNull() ?: 0.0
        val sec = m.groupValues[4].toDoubleOrNull() ?: 0.0
        val total = d * 86400 + h * 3600 + min * 60 + sec
        return if (total > 0) total else null
    }

    private fun templateIn(
        text: String,
        from: Int,
        until: Int
    ): TemplateInfo? {
        val local = elementSpan(text, "SegmentTemplate", from)
            ?: return null
        if (local.first >= until) return null
        val a = openTagAttrs(text, local)
        val media = a["media"] ?: return null
        var timeline: List<Long>? = null
        val openEnd = tagEnd(text, local.first)
        if (!text.substring(local.first, openEnd).endsWith("/>")) {
            val close = text.indexOf("</SegmentTemplate>", openEnd)
            if (close >= 0 && close <= until) {
                val inner = text.substring(openEnd, close)
                if (inner.contains("<S")) {
                    val times = mutableListOf<Long>()
                    var ok = true
                    var cursor = 0L
                    for (sm in Regex("<S\\b[^>]*>").findAll(inner)) {
                        val sa = attrs(sm.value)
                        val d = sa["d"]?.toLongOrNull() ?: 0L
                        var tt = sa["t"]?.toLongOrNull() ?: cursor
                        val r = sa["r"]?.toIntOrNull() ?: 0
                        if (r < 0 || d <= 0) {
                            ok = false
                            break
                        }
                        repeat(r + 1) {
                            times.add(tt)
                            tt += d
                        }
                        cursor = tt
                    }
                    if (ok && times.isNotEmpty()) timeline = times
                }
            }
        }
        return TemplateInfo(
            media = media,
            initialization = a["initialization"],
            timescale = (a["timescale"]?.toLongOrNull() ?: 1L)
                .coerceAtLeast(1L),
            duration = a["duration"]?.toLongOrNull() ?: 0L,
            startNumber = a["startNumber"]?.toIntOrNull() ?: 1,
            timeline = timeline
        )
    }

    private fun listIn(
        text: String,
        from: Int,
        until: Int
    ): ListInfo? {
        val local = elementSpan(text, "SegmentList", from)
            ?: return null
        if (local.first >= until) return null
        val inner = text.substring(local.first, local.second)
        var init: String? = null
        val medias = mutableListOf<String>()
        for (m in Regex("<(Initialization|SegmentURL)\\b[^>]*>")
            .findAll(inner)) {
            val a = attrs(m.value)
            if (m.value.startsWith("<Initialization")) {
                init = a["sourceURL"]
            } else {
                a["media"]?.let { medias.add(it) }
            }
        }
        return ListInfo(init, medias)
    }

    /** $RepresentationID$ / $Bandwidth$ / $Time$ / $Number$ and
     * $Number%0Nd$ substitution, per the DASH template rules. */
    private fun fillTemplate(
        template: String,
        repId: String,
        bandwidth: Long,
        number: Int?,
        time: Long?
    ): String {
        var out = template
            .replace("\$RepresentationID\$", repId)
            .replace("\$Bandwidth\$", bandwidth.toString())
        if (time != null) {
            out = out.replace("\$Time\$", time.toString())
        }
        if (number == null) return out
        val sb = StringBuilder()
        var i = 0
        while (i < out.length) {
            var handled = false
            if (out.startsWith("\$Number", i)) {
                val j = i + 7
                if (j < out.length && out[j] == '%') {
                    var k = j + 1
                    val digits = StringBuilder()
                    while (k < out.length && out[k].isDigit()) {
                        digits.append(out[k])
                        k++
                    }
                    if (digits.isNotEmpty() && k + 1 < out.length &&
                        out[k] == 'd' && out[k + 1] == '$'
                    ) {
                        val w = digits.toString().toIntOrNull() ?: 0
                        sb.append(number.toString().padStart(w, '0'))
                        i = k + 2
                        handled = true
                    }
                }
                if (!handled && j < out.length && out[j] == '$') {
                    sb.append(number)
                    i = j + 1
                    handled = true
                }
            }
            if (!handled) {
                sb.append(out[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun trackKind(
        mime: String?,
        contentType: String?,
        hasDims: Boolean
    ): String? = when {
        mime?.startsWith("video") == true || contentType == "video" ->
            "video"
        mime?.startsWith("audio") == true || contentType == "audio" ->
            "audio"
        (mime.isNullOrEmpty()) && hasDims -> "video"
        else -> null
    }

    private fun repPlan(
        text: String,
        repSpan: Pair<Int, Int>,
        repBase: String,
        repAttrs: Map<String, String>,
        setMime: String?,
        setContentType: String?,
        setTpl: TemplateInfo?,
        setList: ListInfo?,
        totalSec: Double?
    ): DashTrackPlan? {
        val kind = trackKind(
            repAttrs["mimeType"] ?: setMime,
            setContentType,
            (repAttrs["width"]?.toIntOrNull() ?: 0) > 0
        ) ?: return null
        val repId = repAttrs["id"] ?: ""
        val bw = repAttrs["bandwidth"]?.toLongOrNull() ?: 0L
        val list = listIn(text, repSpan.first, repSpan.second)
            ?: setList
        if (list != null) {
            if (list.mediaUrls.isEmpty()) return null
            return DashTrackPlan(
                kind,
                list.initUrl?.let { resolve(repBase, it) },
                list.mediaUrls.map { resolve(repBase, it) }
            )
        }
        val tpl = templateIn(text, repSpan.first, repSpan.second)
            ?: setTpl ?: return null
        val times = tpl.timeline
        val count: Int
        if (times != null) {
            count = times.size
        } else {
            if (tpl.media.contains("\$Time\$")) return null
            if (tpl.duration <= 0) return null
            val total = totalSec ?: return null
            count = kotlin.math.ceil(
                total * tpl.timescale / tpl.duration
            ).toInt()
            if (count <= 0) return null
        }
        val segs = ArrayList<String>(count)
        for (i in 0 until count) {
            segs.add(
                resolve(
                    repBase,
                    fillTemplate(
                        tpl.media, repId, bw,
                        number = tpl.startNumber + i,
                        time = times?.get(i)
                    )
                )
            )
        }
        val init = tpl.initialization?.let {
            resolve(repBase, fillTemplate(it, repId, bw, null, null))
        }
        return DashTrackPlan(kind, init, segs)
    }

    /**
     * The download plan for a static (video-on-demand) MPD, or
     * null when the manifest is outside the downloader's reach:
     * live/dynamic manifests, multi-period documents,
     * SegmentBase addressing, $Time$ templates with no
     * SegmentTimeline, and anything whose segment count cannot
     * be determined. Relative addresses resolve against the
     * manifest's own URL, honouring BaseURL at the MPD, Period,
     * AdaptationSet and Representation levels.
     */
    fun parseDownloadPlan(
        text: String,
        mpdUrl: String
    ): DashDownloadPlan? {
        if (!text.contains("<MPD")) return null
        if (Regex("<Period\\b").findAll(text).count() > 1) return null
        val mpdSpan = elementSpan(text, "MPD", 0) ?: return null
        val mpdAttrs = openTagAttrs(text, mpdSpan)
        if (mpdAttrs["type"] == "dynamic") return null
        var base = mpdUrl
        baseUrlIn(text, mpdSpan.first, mpdSpan.second)?.let {
            base = resolve(base, it)
        }
        val periodSpan = elementSpan(text, "Period", mpdSpan.first)
            ?: return null
        baseUrlIn(text, periodSpan.first, periodSpan.second)?.let {
            base = resolve(base, it)
        }
        val periodAttrs = openTagAttrs(text, periodSpan)
        val totalSec = isoDurationSec(periodAttrs["duration"])
            ?: isoDurationSec(mpdAttrs["mediaPresentationDuration"])
        var video: DashTrackPlan? = null
        var audio: DashTrackPlan? = null
        var setFrom = periodSpan.first
        while (true) {
            val setSpan = elementSpan(text, "AdaptationSet", setFrom)
                ?: break
            if (setSpan.first >= periodSpan.second) break
            setFrom = setSpan.second
            val setAttrs = openTagAttrs(text, setSpan)
            var setBase = base
            val firstRep = text.indexOf(
                "<Representation", setSpan.first
            ).takeIf { it >= 0 && it < setSpan.second }
                ?: setSpan.second
            baseUrlIn(text, setSpan.first, firstRep)?.let {
                setBase = resolve(setBase, it)
            }
            val setTpl = templateIn(text, setSpan.first, firstRep)
            val setList = listIn(text, setSpan.first, firstRep)
            var best: DashTrackPlan? = null
            var bestBw = -1L
            var repFrom = setSpan.first
            while (true) {
                val repSpan = elementSpan(
                    text, "Representation", repFrom
                ) ?: break
                if (repSpan.first >= setSpan.second) break
                repFrom = repSpan.second
                val ra = openTagAttrs(text, repSpan)
                val bw = ra["bandwidth"]?.toLongOrNull() ?: 0L
                if (bw < bestBw) continue
                var repBase = setBase
                baseUrlIn(text, repSpan.first, repSpan.second)?.let {
                    repBase = resolve(repBase, it)
                }
                val plan = repPlan(
                    text, repSpan, repBase, ra,
                    setAttrs["mimeType"], setAttrs["contentType"],
                    setTpl, setList, totalSec
                ) ?: continue
                if (bw >= bestBw) {
                    best = plan
                    bestBw = bw
                }
            }
            when (best?.kind) {
                "video" -> if (video == null) video = best
                "audio" -> if (audio == null) audio = best
            }
        }
        val v = video ?: return null
        return DashDownloadPlan(v, audio)
    }
}
