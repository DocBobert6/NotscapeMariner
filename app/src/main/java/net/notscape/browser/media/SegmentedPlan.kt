package net.notscape.browser.media

/**
 * Pure, JVM-safe planning for the segmented download engine (v1):
 * chunk math, probe parsing, eligibility decisions, and the resume
 * journal format. Nothing here touches Android or the network, so it
 * is unit-testable on the JVM. The engine itself lives in
 * [Downloads.runSegmentedJob]; the worker in
 * SegmentedDownloadWorker.kt.
 */
object SegmentedPlan {

    /** Default parallel connections (decision D5). */
    const val DEFAULT_CONNECTIONS = 4

    /** Files below this size never segment (decision D4). */
    const val MIN_FILE_SIZE = 8L * 1024 * 1024

    /** A chunk is never smaller than this (decision D4). */
    const val MIN_CHUNK_SIZE = 1L * 1024 * 1024

    /** One chunk: byte range [start, end], both inclusive. */
    data class ChunkRange(val index: Int, val start: Long, val end: Long) {
        val length: Long get() = end - start + 1
    }

    /**
     * Split [total] bytes into chunks. Returns an empty list when the
     * file is below the segmentation floor. The connection count is
     * reduced when total / connections would fall below the minimum
     * chunk size; the last chunk takes the remainder.
     */
    fun plan(total: Long, connections: Int = DEFAULT_CONNECTIONS): List<ChunkRange> {
        if (total < MIN_FILE_SIZE) return emptyList()
        val bySize = (total / MIN_CHUNK_SIZE).toInt().coerceAtLeast(1)
        val n = minOf(connections.coerceAtLeast(1), bySize)
        if (n <= 1) return listOf(ChunkRange(0, 0, total - 1))
        val base = total / n
        val out = ArrayList<ChunkRange>(n)
        for (i in 0 until n) {
            val start = i * base
            val end = if (i == n - 1) total - 1 else (i + 1) * base - 1
            out.add(ChunkRange(i, start, end))
        }
        return out
    }

    /**
     * Parse a Content-Range header value. Accepts "bytes 0-0/12345"
     * and "bytes star/12345"; returns the total, or null when the
     * header is absent, malformed, or carries an unknown total.
     */
    fun parseContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val slash = header.indexOf('/')
        if (slash < 0) return null
        val total = header.substring(slash + 1).trim()
        if (total == "*") return null
        return total.toLongOrNull()?.takeIf { it > 0 }
    }

    /** Outcome of the eligibility probe (spec §4). */
    sealed class ProbeDecision {
        data class Eligible(val total: Long) : ProbeDecision()
        data class Fallback(val reason: String) : ProbeDecision()
    }

    /**
     * Decide segmented vs DownloadManager fallback from probe results.
     * [rangeStatus] is the status of the Range: bytes=0-0 request
     * (null when the request itself failed); [rangeTotal] is the total
     * parsed from its Content-Range. [headLength] and
     * [headAcceptRanges] come from the HEAD fallback, when used.
     */
    fun decideProbe(
        rangeStatus: Int?,
        rangeTotal: Long?,
        headLength: Long?,
        headAcceptRanges: String?
    ): ProbeDecision {
        if (rangeStatus == 206 && rangeTotal != null) {
            return if (rangeTotal >= MIN_FILE_SIZE) {
                ProbeDecision.Eligible(rangeTotal)
            } else {
                ProbeDecision.Fallback("below ${MIN_FILE_SIZE / 1024 / 1024} MB floor")
            }
        }
        if (rangeStatus == 200) {
            return ProbeDecision.Fallback("server ignores Range requests")
        }
        if (rangeStatus != null && rangeStatus >= 400) {
            return ProbeDecision.Fallback("probe refused: HTTP $rangeStatus")
        }
        // Range inconclusive (or 206 without a total): the HEAD
        // fallback must prove BOTH a usable length and Range support.
        if (headLength != null && headLength >= MIN_FILE_SIZE &&
            headAcceptRanges?.trim()?.lowercase() == "bytes"
        ) {
            return ProbeDecision.Eligible(headLength)
        }
        return ProbeDecision.Fallback("no usable length or no Range support")
    }

    /**
     * Journal format: one line per chunk, "index:start:end:done".
     * Plain text (no org.json — the Android stub throws under local
     * JVM unit tests). Lines that do not parse are skipped, never
     * fatal: a corrupt journal degrades to a fresh chunk, not a
     * failed download.
     */
    fun journalText(chunks: List<ChunkRange>, done: LongArray): String {
        val sb = StringBuilder()
        for (c in chunks) {
            val d = if (c.index < done.size) done[c.index] else 0L
            sb.append(c.index).append(':').append(c.start).append(':')
                .append(c.end).append(':').append(d.coerceIn(0L, c.length))
                .append('\n')
        }
        return sb.toString()
    }

    /** Parse journal text into chunk index → bytes completed. */
    fun parseJournal(text: String): Map<Int, Long> {
        val out = HashMap<Int, Long>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(':')
            if (parts.size != 4) continue
            val index = parts[0].toIntOrNull() ?: continue
            val done = parts[3].toLongOrNull() ?: continue
            if (done >= 0) out[index] = done
        }
        return out
    }
}
