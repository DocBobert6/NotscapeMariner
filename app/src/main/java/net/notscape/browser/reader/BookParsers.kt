package net.notscape.browser.reader

import android.text.Html
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class Chapter(val title: String, val text: String)
data class ParsedBook(val title: String, val author: String, val chapters: List<Chapter>)

object BookParser {

    fun parse(file: File): ParsedBook {
        return when (file.extension.lowercase()) {
            "epub" -> parseEpub(file)
            "mobi", "azw", "azw3", "prc" -> parseMobi(file)
            else -> parseTxt(file)
        }
    }

    // ---------------- EPUB ----------------
    private fun parseEpub(file: File): ParsedBook {
        ZipFile(file).use { zip ->
            fun readEntry(name: String): String? {
                val e = zip.getEntry(name) ?: zip.entries().asSequence()
                    .firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
                return zip.getInputStream(e).readBytes().toString(Charsets.UTF_8)
            }
            val container = readEntry("META-INF/container.xml")
                ?: throw IllegalStateException("Not an EPUB (no container.xml)")
            val opfPath = Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1)
                ?: throw IllegalStateException("Not an EPUB (no rootfile)")
            val opf = readEntry(opfPath) ?: throw IllegalStateException("Missing OPF")
            val opfDir = opfPath.substringBeforeLast('/', "")

            val title = Regex("""<dc:title[^>]*>([^<]+)</dc:title>""").find(opf)?.groupValues?.get(1)?.trim()
                ?: file.nameWithoutExtension
            val author = Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""").find(opf)?.groupValues?.get(1)?.trim() ?: ""

            val manifest = mutableMapOf<String, String>()
            Regex("""<item\s+[^>]*id="([^"]+)"[^>]*href="([^"]+)"""").findAll(opf).forEach {
                manifest[it.groupValues[1]] = it.groupValues[2]
            }
            // Also catch attribute order href-before-id
            Regex("""<item\s+[^>]*href="([^"]+)"[^>]*id="([^"]+)"""").findAll(opf).forEach {
                val id = it.groupValues[2]
                if (id !in manifest) manifest[id] = it.groupValues[1]
            }
            val spine = Regex("""<itemref\s+[^>]*idref="([^"]+)"""").findAll(opf)
                .map { it.groupValues[1] }.toList()

            val chapters = mutableListOf<Chapter>()
            var index = 0
            for (idref in spine) {
                val href = manifest[idref] ?: continue
                val path = if (opfDir.isEmpty()) href else "$opfDir/$href"
                val html = readEntry(normalizeZipPath(path)) ?: continue
                val text = htmlToText(html)
                if (text.isBlank()) continue
                index++
                val chTitle = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                    ?: Regex("""<h[12][^>]*>(.*?)</h[12]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(html)?.groupValues?.get(1)?.let { htmlToText(it) }?.takeIf { it.isNotBlank() }
                    ?: "Chapter $index"
                chapters.add(Chapter(chTitle.take(120), text))
            }
            if (chapters.isEmpty()) throw IllegalStateException("EPUB had no readable chapters")
            return ParsedBook(title, author, chapters)
        }
    }

    private fun normalizeZipPath(path: String): String {
        val parts = mutableListOf<String>()
        for (p in path.split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }

    // ---------------- MOBI (PalmDOC) ----------------
    private fun parseMobi(file: File): ParsedBook {
        val data = file.readBytes()
        require(data.size > 100) { "File too small to be a MOBI" }
        fun u16(off: Int) = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
        fun u32(off: Int) = ((data[off].toInt() and 0xFF) shl 24) or ((data[off + 1].toInt() and 0xFF) shl 16) or
            ((data[off + 2].toInt() and 0xFF) shl 8) or (data[off + 3].toInt() and 0xFF)

        val numRecords = u16(76)
        require(numRecords in 1..10000) { "Bad MOBI record table" }
        val offsets = IntArray(numRecords) { u32(78 + it * 8) }

        fun record(i: Int): ByteArray {
            val start = offsets[i]
            val end = if (i + 1 < numRecords) offsets[i + 1] else data.size
            return data.copyOfRange(start, end)
        }

        val rec0 = record(0)
        fun r16(b: ByteArray, off: Int) = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
        fun r32(b: ByteArray, off: Int) = ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

        val compression = r16(rec0, 0)
        val textRecordCount = r16(rec0, 8)
        val encryption = r16(rec0, 12)
        require(encryption == 0) { "This MOBI is DRM-protected and cannot be read" }
        require(compression == 1 || compression == 2) { "Unsupported MOBI compression ($compression)" }

        var title = file.nameWithoutExtension
        // MOBI header: "MOBI" at rec0 offset 16; full name offset/length at MOBI+84/+88
        if (rec0.size > 16 + 92 && String(rec0, 16, 4, Charsets.US_ASCII) == "MOBI") {
            val nameOff = r32(rec0, 16 + 84)
            val nameLen = r32(rec0, 16 + 88)
            if (nameOff > 0 && nameLen in 1..512 && nameOff + nameLen <= rec0.size) {
                title = String(rec0, nameOff, nameLen, Charsets.UTF_8).trim().ifBlank { title }
            }
        }

        val textBytes = ByteArrayOutputStream()
        val last = minOf(textRecordCount, numRecords - 1)
        for (i in 1..last) {
            val rec = record(i)
            if (compression == 1) textBytes.write(rec) else textBytes.write(palmDocDecompress(rec))
        }
        val rawHtml = textBytes.toByteArray().toString(Charsets.UTF_8)
        val fullText = htmlToText(rawHtml)
        if (fullText.isBlank()) throw IllegalStateException("MOBI contained no readable text")
        return ParsedBook(title, "", splitIntoChapters(fullText))
    }

    /**
     * PalmDOC LZ77 decompression over a single growable buffer. (The old
     * version copied the whole output for every back-referenced byte —
     * quadratic — which froze the app on real books.) Internal so the
     * unit tests can cover it.
     */
    internal fun palmDocDecompress(data: ByteArray): ByteArray {
        var buf = ByteArray(maxOf(1024, data.size * 2))
        var size = 0
        fun put(b: Int) {
            if (size == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[size++] = b.toByte()
        }
        var i = 0
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            i++
            when {
                c == 0 -> put(0)
                c in 1..8 -> {
                    var n = c
                    while (n > 0 && i < data.size) { put(data[i].toInt()); i++; n-- }
                }
                c in 0x09..0x7F -> put(c)
                c >= 0xC0 -> {
                    put(0x20)
                    put(c xor 0x80)
                }
                else -> {
                    if (i >= data.size) break
                    val c2 = data[i].toInt() and 0xFF
                    i++
                    val dist = ((c shl 8) or c2) shr 3 and 0x7FF
                    val len = (c2 and 0x7) + 3
                    var pos = size - dist
                    repeat(len) {
                        if (pos in 0 until size) put(buf[pos].toInt() and 0xFF)
                        pos++
                    }
                }
            }
        }
        return buf.copyOf(size)
    }

    // ---------------- TXT ----------------
    /**
     * Decode text bytes as UTF-8 with a STRICT decoder, falling back to
     * windows-1252. (String(bytes, UTF_8) replaces bad sequences instead
     * of throwing, so a lenient decode never triggers the fallback and
     * older files show garbage.) Internal for unit tests.
     */
    internal fun decodeText(bytes: ByteArray): String {
        val strictUtf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val decoded = runCatching {
            strictUtf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrElse { String(bytes, Charset.forName("windows-1252")) }
        return decoded.removePrefix("\uFEFF")
    }

    private fun parseTxt(file: File): ParsedBook {
        val text = decodeText(file.readBytes())
        return ParsedBook(file.nameWithoutExtension, "", splitIntoChapters(text))
    }

    private fun splitIntoChapters(fullText: String): List<Chapter> {
        val heading = Regex("""(?im)^\s*(chapter|part|book)\s+([0-9]+|[ivxlcdm]+)\b.*$""")
        val matches = heading.findAll(fullText).toList()
        val chapters = mutableListOf<Chapter>()
        if (matches.size >= 2) {
            for (i in matches.indices) {
                val start = matches[i].range.first
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else fullText.length
                val body = fullText.substring(start, end).trim()
                if (body.isNotBlank()) {
                    chapters.add(Chapter(matches[i].value.trim().take(120), body))
                }
            }
            if (chapters.isNotEmpty()) return chapters
        }
        // Fall back to ~14k-character chunks at paragraph boundaries
        var rest = fullText.trim()
        var n = 1
        while (rest.isNotEmpty()) {
            val cut = if (rest.length <= 14_000) rest.length else {
                val idx = rest.lastIndexOf("\n\n", 14_000)
                if (idx > 4_000) idx else 14_000
            }
            chapters.add(Chapter("Part $n", rest.substring(0, cut).trim()))
            rest = rest.substring(cut).trim()
            n++
        }
        return chapters.ifEmpty { listOf(Chapter("Text", fullText)) }
    }

    // ---------------- Web article ----------------
    fun fetchArticle(url: String): ParsedBook {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            )
        }
        val html = conn.inputStream.bufferedReader().readText()
        val title = Regex("""<meta[^>]+property="og:title"[^>]+content="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
            ?: url
        var body = html
        val article = Regex("""<article[^>]*>(.*?)</article>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)
        if (article != null && article.length > 400) body = article
        val text = htmlToText(body)
        if (text.isBlank()) throw IllegalStateException("Could not extract article text")
        return ParsedBook(title, url, listOf(Chapter(title.take(120), text)))
    }

    fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("""<(br|/p|/div|/h[1-6]|/li|/tr)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        val rendered = runCatching {
            @Suppress("DEPRECATION")
            Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrElse {
            s.replace(Regex("""<[^>]+>"""), " ")
        }
        return rendered
            .replace(Regex("""[ \t\x0B\f]+"""), " ")
            .replace(Regex("""\n[ ]+"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
