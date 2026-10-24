package net.notscape.browser.media

/** One video rendition from a DASH manifest. */
data class DashVariant(
    val bandwidth: Long,
    val width: Int,
    val height: Int
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
}
