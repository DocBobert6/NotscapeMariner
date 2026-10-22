package net.notscape.browser.reader

import android.text.Html
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class Chapter(val title: String, val text: String)

/**
 * A parsed book. [cover] is the EPUB cover image bytes when the book
 * declares one. [images] holds inline EPUB images by token — chapter
 * text carries a `[[img:token]]` paragraph where each image belongs,
 * and the reader renders the image in place of that paragraph.
 */
data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
    val cover: ByteArray? = null,
    val images: Map<String, ByteArray> = emptyMap()
)

object BookParser {

    fun parse(file: File): ParsedBook {
        return when (file.extension.lowercase()) {
            "epub" -> parseEpub(file)
            "mobi", "azw", "azw3", "prc" -> parseMobi(file)
            else -> parseTxt(file)
        }
    }

    // ---------------- EPUB ----------------
    /**
     * A real EPUB reader (Phase E): the package document is parsed
     * as XML (javax.xml — present on Android and the JVM alike), the
     * table of contents comes from the EPUB 3 navigation document
     * or the EPUB 2 NCX, the cover is extracted (metadata cover
     * reference or a `cover-image` manifest item), and inline images
     * are pulled out of the zip and tokenised into the chapter text.
     * Badly-formed packages fall back to the older regex extraction
     * for metadata/manifest/spine rather than failing outright.
     */
    private data class ManifestItem(
        val href: String,
        val mediaType: String = "",
        val properties: String = ""
    )

    private fun parseXml(text: String): org.w3c.dom.Document {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // No external entities or DTDs — an EPUB must never make the
        // parser reach outside the file.
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    /** All descendant elements whose tag is [local] (prefix ignored). */
    private fun org.w3c.dom.Element.allNamed(local: String): List<org.w3c.dom.Element> {
        val out = mutableListOf<org.w3c.dom.Element>()
        val all = getElementsByTagName("*")
        for (i in 0 until all.length) {
            val el = all.item(i) as? org.w3c.dom.Element ?: continue
            if (el.tagName == local || el.tagName.endsWith(":$local")) out.add(el)
        }
        return out
    }

    private fun org.w3c.dom.Element.firstNamed(local: String): org.w3c.dom.Element? =
        allNamed(local).firstOrNull()

    private fun decodePath(path: String): String =
        runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)

    private fun parseEpub(file: File): ParsedBook {
        ZipFile(file).use { zip ->
            fun readEntryBytes(name: String): ByteArray? {
                // EPUB hrefs arrive URL-encoded, sometimes with
                // fragments; the zip stores real file names. Try the
                // raw name and the decoded name, exact and
                // case-insensitively — a miss here silently dropped
                // whole chapters ("partial" imports).
                val decoded = decodePath(name)
                for (candidate in listOf(name, decoded).distinct()) {
                    val e = zip.getEntry(candidate) ?: zip.entries().asSequence()
                        .firstOrNull { it.name.equals(candidate, ignoreCase = true) }
                    if (e != null) return zip.getInputStream(e).readBytes()
                }
                return null
            }
            fun readEntry(name: String): String? =
                readEntryBytes(name)?.toString(Charsets.UTF_8)

            val container = readEntry("META-INF/container.xml")
                ?: throw IllegalStateException("Not an EPUB (no container.xml)")
            val opfPath = Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1)
                ?: throw IllegalStateException("Not an EPUB (no rootfile)")
            val opf = readEntry(opfPath) ?: throw IllegalStateException("Missing OPF")
            val opfDir = opfPath.substringBeforeLast('/', "")

            var title = file.nameWithoutExtension
            var author = ""
            val manifest = mutableMapOf<String, ManifestItem>()
            var spineIds: List<String> = emptyList()
            var coverId: String? = null
            var navId: String? = null
            var ncxId: String? = null

            val opfDoc = runCatching { parseXml(opf) }.getOrNull()
            if (opfDoc != null) {
                val root = opfDoc.documentElement
                root.firstNamed("metadata")?.let { md ->
                    md.firstNamed("title")?.textContent?.trim()
                        ?.takeIf { it.isNotBlank() }?.let { title = it }
                    md.firstNamed("creator")?.textContent?.trim()?.let { author = it }
                    md.allNamed("meta").firstOrNull { it.getAttribute("name") == "cover" }
                        ?.getAttribute("content")?.takeIf { it.isNotBlank() }
                        ?.let { coverId = it }
                }
                root.firstNamed("manifest")?.allNamed("item")?.forEach { item ->
                    val id = item.getAttribute("id")
                    val href = item.getAttribute("href")
                    if (id.isNotBlank() && href.isNotBlank()) {
                        manifest[id] = ManifestItem(
                            href,
                            item.getAttribute("media-type"),
                            item.getAttribute("properties")
                        )
                    }
                }
                root.firstNamed("spine")?.let { spine ->
                    spineIds = spine.allNamed("itemref")
                        .map { it.getAttribute("idref") }.filter { it.isNotBlank() }
                    spine.getAttribute("toc").takeIf { it.isNotBlank() }?.let { ncxId = it }
                }
                for ((id, item) in manifest) {
                    if (item.properties.contains("cover-image")) coverId = id
                    if (item.properties.split(' ').any { it == "nav" }) navId = id
                    if (item.mediaType == "application/x-dtbncx+xml" && ncxId == null) {
                        ncxId = id
                    }
                }
            } else {
                // Regex fallback for packages whose OPF won't parse.
                title = Regex("""<dc:title[^>]*>([^<]+)</dc:title>""").find(opf)
                    ?.groupValues?.get(1)?.trim() ?: title
                author = Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""").find(opf)
                    ?.groupValues?.get(1)?.trim() ?: ""
                Regex("""<item\s+[^>]*id="([^"]+)"[^>]*href="([^"]+)"""").findAll(opf).forEach {
                    manifest[it.groupValues[1]] = ManifestItem(it.groupValues[2])
                }
                Regex("""<item\s+[^>]*href="([^"]+)"[^>]*id="([^"]+)"""").findAll(opf).forEach {
                    val id = it.groupValues[2]
                    if (id !in manifest) manifest[id] = ManifestItem(it.groupValues[1])
                }
                Regex("""<item\s+[^>]*id='([^']+)'[^>]*href='([^']+)'""").findAll(opf).forEach {
                    if (it.groupValues[1] !in manifest) {
                        manifest[it.groupValues[1]] = ManifestItem(it.groupValues[2])
                    }
                }
                Regex("""<item\s+[^>]*href='([^']+)'[^>]*id='([^']+)'""").findAll(opf).forEach {
                    val id = it.groupValues[2]
                    if (id !in manifest) manifest[id] = ManifestItem(it.groupValues[1])
                }
                spineIds = Regex("""<itemref\s+[^>]*idref="([^"]+)"""").findAll(opf)
                    .map { it.groupValues[1] }.toList()
            }

            fun resolvePath(baseDir: String, href: String): String {
                val clean = href.substringBefore('#')
                val joined = when {
                    clean.startsWith("/") -> clean.removePrefix("/")
                    baseDir.isEmpty() -> clean
                    else -> "$baseDir/$clean"
                }
                return decodePath(normalizeZipPath(joined))
            }

            // ---- Table of contents: EPUB 3 nav, then EPUB 2 NCX.
            // Keys are resolved, decoded zip paths (fragment-free);
            // first label for a target wins.
            val toc = mutableMapOf<String, String>()
            fun addToc(baseDir: String, href: String, label: String) {
                val clean = label.replace(Regex("""\s+"""), " ").trim()
                if (clean.isEmpty() || href.isBlank()) return
                val key = resolvePath(baseDir, href)
                if (key !in toc) toc[key] = clean.take(120)
            }
            navId?.let { manifest[it] }?.let { navItem ->
                val navPath = resolvePath(opfDir, navItem.href)
                val navText = readEntry(navPath)
                val navDoc = navText?.let { runCatching { parseXml(it) }.getOrNull() }
                if (navDoc != null) {
                    val navs = navDoc.documentElement.allNamed("nav")
                    val tocNav = navs.firstOrNull {
                        it.getAttribute("epub:type").contains("toc")
                    } ?: navs.firstOrNull()
                    tocNav?.allNamed("a")?.forEach { a ->
                        addToc(navPath.substringBeforeLast('/', ""), a.getAttribute("href"), a.textContent ?: "")
                    }
                }
            }
            ncxId?.let { manifest[it] }?.let { ncxItem ->
                val ncxPath = resolvePath(opfDir, ncxItem.href)
                val ncxText = readEntry(ncxPath)
                val ncxDoc = ncxText?.let { runCatching { parseXml(it) }.getOrNull() }
                if (ncxDoc != null) {
                    val ncxDir = ncxPath.substringBeforeLast('/', "")
                    ncxDoc.documentElement.allNamed("navPoint").forEach { point ->
                        val label = point.firstNamed("text")?.textContent ?: ""
                        val src = point.firstNamed("content")?.getAttribute("src") ?: ""
                        addToc(ncxDir, src, label)
                    }
                }
            }

            // ---- Cover image bytes.
            val coverBytes = coverId?.let { manifest[it] }
                ?.let { readEntryBytes(resolvePath(opfDir, it.href)) }

            // ---- Chapters in spine order (manifest fallback kept).
            val orderedHrefs = spineIds.mapNotNull { manifest[it]?.href }
                .ifEmpty {
                    manifest.values.map { it.href }.filter { h ->
                        val clean = h.lowercase().substringBefore('#')
                        clean.endsWith(".xhtml") || clean.endsWith(".html") || clean.endsWith(".htm")
                    }
                }
            val images = mutableMapOf<String, ByteArray>()
            var imageSeq = 0
            val imgTag = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
            val svgImageTag = Regex("""<image\b[^>]*>""", RegexOption.IGNORE_CASE)
            val srcAttr = Regex("""(?:src|xlink:href|href)\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            val srcAttrSq = Regex("""(?:src|xlink:href|href)\s*=\s*'([^']+)'""", RegexOption.IGNORE_CASE)

            val chapters = mutableListOf<Chapter>()
            var index = 0
            for (rawHref in orderedHrefs) {
                val path = resolvePath(opfDir, rawHref)
                val html = readEntry(path) ?: continue
                val chapterDir = path.substringBeforeLast('/', "")
                fun markImages(tagged: String): String {
                    var out = imgTag.replace(tagged) { m ->
                        val src = srcAttr.find(m.value)?.groupValues?.get(1)
                            ?: srcAttrSq.find(m.value)?.groupValues?.get(1) ?: ""
                        val bytes = if (src.isBlank()) null
                            else readEntryBytes(resolvePath(chapterDir, src))
                        if (bytes == null) "" else {
                            val token = "img${imageSeq++}"
                            images[token] = bytes
                            "\n\n[[img:$token]]\n\n"
                        }
                    }
                    out = svgImageTag.replace(out) { m ->
                        val src = srcAttr.find(m.value)?.groupValues?.get(1)
                            ?: srcAttrSq.find(m.value)?.groupValues?.get(1) ?: ""
                        val bytes = if (src.isBlank()) null
                            else readEntryBytes(resolvePath(chapterDir, src))
                        if (bytes == null) "" else {
                            val token = "img${imageSeq++}"
                            images[token] = bytes
                            "\n\n[[img:$token]]\n\n"
                        }
                    }
                    return out
                }
                val text = htmlToText(markImages(html))
                if (text.isBlank()) continue
                index++
                val chTitle = toc[path]
                    ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                        .find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                    ?: Regex("""<h[12][^>]*>(.*?)</h[12]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(html)?.groupValues?.get(1)?.let { htmlToText(it) }?.takeIf { it.isNotBlank() }
                    ?: "Chapter $index"
                chapters.add(Chapter(chTitle.take(120), text))
            }
            if (chapters.isEmpty()) throw IllegalStateException("EPUB had no readable chapters")
            return ParsedBook(title, author, chapters, coverBytes, images)
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
