package net.notscape.browser.media

import java.net.URL

/**
 * Pure HLS playlist parsing (RFC 8216) for the offline downloader —
 * Android-free so it is unit-testable. URL resolution uses java.net
 * only. The downloader (Downloads) executes the plan this produces.
 */
object HlsPlaylist {

    data class ByteRange(val length: Long, val offset: Long?)

    data class Key(
        val method: String,
        val uri: String?,
        val ivHex: String?
    )

    data class Segment(
        val url: String,
        val range: ByteRange?,
        val key: Key?,
        val sequence: Long
    )

    data class MediaPlan(
        val segments: List<Segment>,
        val initUrl: String?,
        val initRange: ByteRange?,
        /** True when the stream is fMP4/CMAF (has an init segment):
         *  init + fragments concatenate into a playable .mp4. */
        val isCmaf: Boolean,
        val unsupportedKeyMethod: String?
    )

    data class Variant(val url: String, val bandwidth: Long, val audioGroup: String?)

    data class MasterPlan(
        val variants: List<Variant>,
        /** Audio renditions that live in their own playlist — joining
         *  the video alone would silently lose the sound. */
        val separateAudioGroups: Set<String>
    ) {
        fun bestVariant(): Variant? = variants.maxByOrNull { it.bandwidth }
        fun variantHasSeparateAudio(v: Variant): Boolean =
            v.audioGroup != null && separateAudioGroups.contains(v.audioGroup)
    }

    private fun resolve(base: String, ref: String): String =
        runCatching { URL(URL(base), ref).toString() }.getOrDefault(ref)

    private fun attrs(tag: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val body = tag.substringAfter(':', "")
        // key=value pairs; values may be quoted and contain commas.
        var i = 0
        while (i < body.length) {
            val eq = body.indexOf('=', i)
            if (eq < 0) break
            val key = body.substring(i, eq).trim()
            var j = eq + 1
            val value: String
            if (j < body.length && body[j] == '"') {
                val end = body.indexOf('"', j + 1)
                if (end < 0) break
                value = body.substring(j + 1, end)
                j = end + 1
            } else {
                val end = body.indexOf(',', j)
                if (end < 0) {
                    value = body.substring(j).trim(); j = body.length
                } else {
                    value = body.substring(j, end).trim(); j = end
                }
            }
            out[key] = value
            val comma = body.indexOf(',', j)
            i = if (comma < 0) body.length else comma + 1
        }
        return out
    }

    private fun parseRange(value: String, previousEnd: Long?): ByteRange? {
        val parts = value.split('@')
        val length = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: return null
        val offset = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: previousEnd
        return ByteRange(length, offset)
    }

    fun isMaster(text: String): Boolean = text.contains("#EXT-X-STREAM-INF")

    fun parseMaster(text: String, baseUrl: String): MasterPlan {
        val lines = text.lines().map { it.trim() }
        val audioGroups = mutableSetOf<String>()
        for (line in lines) {
            if (line.startsWith("#EXT-X-MEDIA:")) {
                val a = attrs(line)
                if (a["TYPE"] == "AUDIO" && !a["URI"].isNullOrBlank()) {
                    a["GROUP-ID"]?.let { audioGroups.add(it) }
                }
            }
        }
        val variants = mutableListOf<Variant>()
        var pending: Map<String, String>? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> pending = attrs(line)
                line.isNotEmpty() && !line.startsWith("#") && pending != null -> {
                    variants.add(
                        Variant(
                            url = resolve(baseUrl, line),
                            bandwidth = pending["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                            audioGroup = pending["AUDIO"]
                        )
                    )
                    pending = null
                }
            }
        }
        return MasterPlan(variants, audioGroups)
    }

    fun parseMedia(text: String, baseUrl: String): MediaPlan {
        val segments = mutableListOf<Segment>()
        var initUrl: String? = null
        var initRange: ByteRange? = null
        var currentKey: Key? = null
        var unsupported: String? = null
        var sequence = 0L
        var pendingRange: ByteRange? = null
        var lastRangeEnd: Long? = null
        var pendingSegmentRange = false

        fun noteRangeEnd(r: ByteRange?) {
            if (r != null && r.offset != null) lastRangeEnd = r.offset + r.length
        }

        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val a = attrs(line)
                    val uri = a["URI"]
                    if (!uri.isNullOrBlank()) initUrl = resolve(baseUrl, uri)
                    initRange = a["BYTERANGE"]?.let { parseRange(it, null) }
                    noteRangeEnd(initRange)
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val a = attrs(line)
                    val method = (a["METHOD"] ?: "NONE").uppercase()
                    when (method) {
                        "NONE" -> currentKey = null
                        "AES-128" -> currentKey = Key(
                            method,
                            a["URI"]?.let { resolve(baseUrl, it) },
                            a["IV"]
                        )
                        else -> {
                            unsupported = method
                            currentKey = Key(method, null, null)
                        }
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    pendingRange = parseRange(line.substringAfter(':'), lastRangeEnd)
                    pendingSegmentRange = true
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val r = if (pendingSegmentRange) pendingRange else null
                    segments.add(Segment(resolve(baseUrl, line), r, currentKey, sequence))
                    noteRangeEnd(r)
                    sequence++
                    pendingRange = null
                    pendingSegmentRange = false
                }
            }
        }
        return MediaPlan(
            segments = segments,
            initUrl = initUrl,
            initRange = initRange,
            isCmaf = initUrl != null,
            unsupportedKeyMethod = unsupported
        )
    }

    /** The implicit IV for an AES-128 segment without an explicit
     *  IV: its media sequence number as a 128-bit big-endian value. */
    fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var v = sequence
        for (i in 15 downTo 8) {
            iv[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return iv
    }

    fun parseIvHex(ivHex: String): ByteArray? {
        val hex = ivHex.removePrefix("0x").removePrefix("0X")
        if (hex.length != 32) return null
        return runCatching {
            ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }
}
