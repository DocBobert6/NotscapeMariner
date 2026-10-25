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

/** A piece of a chapter paragraph, for display (0.0.969). */
sealed interface ReaderPart {
    data class Text(val text: String) : ReaderPart
    data class Image(val token: String) : ReaderPart
}

private val imageTokenRegex = Regex("""\[\[img:(\w+)]]""")

/** Remove every image token from [text] (speech and the like). */
fun stripImageTokens(text: String): String =
    imageTokenRegex.replace(text, "")
        .replace(Regex("""\s{2,}"""), " ").trim()

/** One in-book search result (0.0.984). [offset] and
 * [pieceLength] are measured in the piece's token-stripped
 * text, the same text the reader renders. */
data class BookHit(
    val piece: Int,
    val offset: Int,
    val pieceLength: Int,
    val snippet: String
)

/** A one-line preview of a piece for the Chapters sheet:
 * its first text, image tokens gone, whitespace collapsed. */
fun chapterPreview(text: String): String {
    val clean = stripImageTokens(text)
        .replace(Regex("""\s+"""), " ").trim()
    return if (clean.length <= 110) {
        clean
    } else {
        clean.substring(0, 110).trimEnd() + "…"
    }
}

/** An in-book ribbon (0.0.985): a saved place in a book. */
data class Ribbon(
    val piece: Int,
    val fraction: Float,
    val snippet: String
)

/** Ribbons persist as a small JSON array in the settings
 * table (key ribbons:<bookId>) - no schema change. */
fun parseRibbons(json: String): List<Ribbon> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        val out = ArrayList<Ribbon>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Ribbon(
                    o.getInt("p"),
                    o.getDouble("f").toFloat().coerceIn(0f, 1f),
                    o.optString("s")
                )
            )
        }
        out
    }.getOrDefault(emptyList())
}

fun ribbonsToJson(ribbons: List<Ribbon>): String {
    val arr = org.json.JSONArray()
    for (r in ribbons) {
        arr.put(
            org.json.JSONObject()
                .put("p", r.piece)
                .put("f", r.fraction.toDouble())
                .put("s", r.snippet)
        )
    }
    return arr.toString()
}

/** A reading-time estimate from a character count
 * (0.0.985), at an average silent-reading pace. */
fun formatTimeLeft(charsRemaining: Int, charsPerMinute: Int = 950): String {
    if (charsRemaining <= 0) return ""
    val mins = (charsRemaining + charsPerMinute - 1) / charsPerMinute
    return when {
        mins < 60 -> "$mins min"
        mins % 60 == 0 -> "${mins / 60} h"
        else -> "${mins / 60} h ${mins % 60} min"
    }
}

/**
 * Whole-book text search (0.0.984): case-insensitive over
 * each piece's token-stripped text, at most [perPiece] hits
 * per piece and [cap] overall, each with a snippet centred
 * on the match. A query under two characters finds nothing.
 */
fun searchBook(
    chapters: List<Chapter>,
    query: String,
    perPiece: Int = 3,
    cap: Int = 120
): List<BookHit> {
    val q = query.trim().lowercase()
    if (q.length < 2) return emptyList()
    val hits = ArrayList<BookHit>()
    for ((idx, ch) in chapters.withIndex()) {
        val clean = stripImageTokens(ch.text)
        val hay = clean.lowercase()
        var from = 0
        var found = 0
        while (found < perPiece && hits.size < cap) {
            val at = hay.indexOf(q, from)
            if (at < 0) break
            val start = (at - 42).coerceAtLeast(0)
            val end = (at + q.length + 42).coerceAtMost(clean.length)
            var snippet = clean.substring(start, end)
                .replace(Regex("""\s+"""), " ").trim()
            if (start > 0) snippet = "…" + snippet
            if (end < clean.length) snippet += "…"
            hits.add(BookHit(idx, at, clean.length, snippet))
            from = at + q.length
            found++
        }
        if (hits.size >= cap) break
    }
    return hits
}

/**
 * Split chapter [paragraphs] into display parts: a token
 * anywhere in a paragraph becomes its own [ReaderPart.Image],
 * with the surrounding text kept, in order. (Before 0.0.969
 * the reader only recognised a paragraph that was nothing but
 * a token, so a picture inside a text paragraph - a chapter
 * heading decoration is the classic case - printed the raw
 * marker as text.)
 */
fun readerParts(paragraphs: List<String>): List<ReaderPart> {
    val out = ArrayList<ReaderPart>()
    for (para in paragraphs) {
        var rest = para
        while (true) {
            val m = imageTokenRegex.find(rest) ?: break
            val before = rest.substring(0, m.range.first).trim()
            if (before.isNotEmpty()) out += ReaderPart.Text(before)
            out += ReaderPart.Image(m.groupValues[1])
            rest = rest.substring(m.range.last + 1)
        }
        val tail = rest.trim()
        if (tail.isNotEmpty()) out += ReaderPart.Text(tail)
    }
    return out
}

/**
 * Cut [parts] into pages of about [budget] characters
 * (Robert's spec, 0.0.971): pages fill to the budget, a
 * paragraph straddling a page edge splits at a sentence
 * boundary, and an image always gets a page of its own.
 * Pure; unit-tested.
 */
fun paginateParts(
    parts: List<ReaderPart>,
    budget: Int = 1450
): List<List<ReaderPart>> {
    val pages = ArrayList<List<ReaderPart>>()
    var page = ArrayList<ReaderPart>()
    var used = 0
    fun flush() {
        if (page.isNotEmpty()) {
            pages += page
            page = ArrayList()
            used = 0
        }
    }
    fun addText(text: String) {
        var rest = text
        while (rest.isNotEmpty()) {
            if (used >= budget) flush()
            val room = budget - used
            if (rest.length <= room) {
                page += ReaderPart.Text(rest)
                used += rest.length
                rest = ""
            } else if (room < 200 && page.isNotEmpty()) {
                flush()
            } else {
                val window = rest.substring(0, room)
                val sentence = maxOf(
                    window.lastIndexOf(". "),
                    window.lastIndexOf("? "),
                    window.lastIndexOf("! ")
                )
                val end = when {
                    sentence >= room / 2 -> sentence + 1
                    window.lastIndexOf(' ') > 0 -> window.lastIndexOf(' ')
                    else -> room
                }
                page += ReaderPart.Text(rest.substring(0, end).trim())
                used += end
                rest = rest.substring(end).trim()
            }
        }
    }
    for (part in parts) {
        when (part) {
            is ReaderPart.Image -> {
                flush()
                pages += listOf(part)
            }
            is ReaderPart.Text -> addText(part.text)
        }
    }
    flush()
    return pages
}

object BookParser {

    fun parse(file: File): ParsedBook {
        return when (file.extension.lowercase()) {
            "epub" -> parseEpub(file)
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

    // EPUB size caps (0.0.9993): a hostile book must not
    // expand without bound. Real EPUBs sit far below these.
    private const val MAX_EPUB_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_EPUB_TOTAL_BYTES = 768L * 1024 * 1024

    private fun readCapped(
        input: java.io.InputStream, cap: Long
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > cap) {
                throw IllegalStateException("EPUB entry too large")
            }
        }
        return out.toByteArray()
    }

    private fun parseEpub(file: File): ParsedBook {
        ZipFile(file).use { zip ->
            var totalReadBytes = 0L
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
                    if (e != null) {
                        // The declared size is checked first,
                        // then the read itself is capped, so a
                        // lying header cannot smuggle a zip bomb
                        // past the limit (0.0.9993).
                        if (e.size > MAX_EPUB_ENTRY_BYTES) {
                            throw IllegalStateException(
                                "EPUB entry too large: ${e.name}"
                            )
                        }
                        val bytes = readCapped(
                            zip.getInputStream(e), MAX_EPUB_ENTRY_BYTES
                        )
                        totalReadBytes += bytes.size
                        if (totalReadBytes > MAX_EPUB_TOTAL_BYTES) {
                            throw IllegalStateException(
                                "EPUB expands beyond the size limit"
                            )
                        }
                        return bytes
                    }
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

            // ---- Cover image bytes. The declared reference does
            // not always point at the image itself: some packages
            // (Gutenberg's among them) point it at the cover
            // wrapper page, and some name the cover only in the
            // guide. Follow the chain until real image bytes turn up.
            fun looksLikeImage(b: ByteArray?): Boolean {
                if (b == null || b.size < 4) return false
                val jpeg = b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()
                val png = b[0] == 0x89.toByte() && b[1] == 0x50.toByte() &&
                    b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()
                val gif = b[0] == 0x47.toByte() && b[1] == 0x49.toByte() &&
                    b[2] == 0x46.toByte()
                return jpeg || png || gif
            }
            fun imageFromXhtml(xhtml: ByteArray, baseDir: String): ByteArray? {
                val s = xhtml.toString(Charsets.UTF_8)
                val tag = Regex("<(?:img|image)\\b[^>]*>", RegexOption.IGNORE_CASE)
                    .find(s)?.value ?: return null
                val src = Regex("(?:src|xlink:href|href)\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1)
                    ?: Regex("(?:src|xlink:href|href)\\s*=\\s*'([^']+)'", RegexOption.IGNORE_CASE)
                        .find(tag)?.groupValues?.get(1) ?: return null
                val bytes = readEntryBytes(resolvePath(baseDir, src))
                return if (looksLikeImage(bytes)) bytes else null
            }
            fun coverBytesFor(href: String): ByteArray? {
                val path = resolvePath(opfDir, href)
                val bytes = readEntryBytes(path) ?: return null
                val declaredImage = manifest.values.any {
                    it.mediaType.startsWith("image/") &&
                        resolvePath(opfDir, it.href) == path
                }
                if (declaredImage || looksLikeImage(bytes)) return bytes
                return imageFromXhtml(bytes, path.substringBeforeLast('/', ""))
            }
            var coverBytes = coverId?.let { manifest[it]?.href }?.let { coverBytesFor(it) }
            if (coverBytes == null) {
                val guideHref = opfDoc?.documentElement?.firstNamed("guide")
                    ?.allNamed("reference")
                    ?.firstOrNull { it.getAttribute("type") == "cover" }
                    ?.getAttribute("href")?.takeIf { it.isNotBlank() }
                    ?: Regex("<reference[^>]*type=\"cover\"[^>]*href=\"([^\"]+)\"")
                        .find(opf)?.groupValues?.get(1)
                    ?: Regex("<reference[^>]*href=\"([^\"]+)\"[^>]*type=\"cover\"")
                        .find(opf)?.groupValues?.get(1)
                if (guideHref != null) coverBytes = coverBytesFor(guideHref)
            }

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
            // Cover-wrapper pages — a spine item that is only the
            // cover image plus scraps of furniture text (Gutenberg's
            // wrap0000.html is the classic case) — read as the image
            // alone. And where a file opens by printing the book's
            // title twice (its own h1 + h2), one of them goes.
            val coverRef = coverBytes
            val cleaned = chapters.map { ch ->
                val paras = ch.text.split("\n\n")
                val imgParas = paras.filter { it.contains("[[img:") }
                val textChars = paras.filterNot { it.contains("[[img:") }
                    .sumOf { it.trim().length }
                var body = ch.text
                // Only a page carrying the book's own cover image
                // counts as the cover wrapper — a short chapter
                // that merely has an inline picture keeps its text.
                val carriesCover = coverRef != null && imgParas.any { p ->
                    Regex("""\[\[img:(\w+)\]\]""").findAll(p).any { m ->
                        images[m.groupValues[1]]?.contentEquals(coverRef) == true
                    }
                }
                if (imgParas.isNotEmpty() && textChars < 300 && carriesCover) {
                    body = imgParas.joinToString("\n\n")
                } else {
                    val lines = body.lines().toMutableList()
                    val nonBlank = lines.withIndex().filter { it.value.isNotBlank() }
                    if (nonBlank.size >= 2) {
                        val head = nonBlank[0].value.trim()
                        val next = nonBlank[1].value.trim()
                        if (head.isNotEmpty() && head.equals(next, ignoreCase = true) &&
                            (head.equals(title, ignoreCase = true) ||
                                head.equals(ch.title, ignoreCase = true))
                        ) {
                            lines.removeAt(nonBlank[1].index)
                            body = lines.joinToString("\n").trim()
                        }
                    }
                }
                if (body == ch.text) ch else Chapter(ch.title, body)
            }
            val split = splitLongChapters(cleaned)
            if (split.isEmpty()) throw IllegalStateException("EPUB had no readable chapters")
            return ParsedBook(title, author, split, coverBytes, images)
        }
    }

    /**
     * Split chapters longer than [maxChars] at block boundaries,
     * so a book whose spine items are huge (a whole one-act play
     * in a single file) still pages honestly in the reader.
     * Deterministic — the same input always splits the same way,
     * so saved chapter positions stay meaningful. Follow-on parts
     * are titled "Name (2)", "Name (3)", ...
     */
    internal fun splitLongChapters(
        chapters: List<Chapter>,
        maxChars: Int = 3600
    ): List<Chapter> {
        val out = mutableListOf<Chapter>()
        for (ch in chapters) {
            if (ch.text.length <= maxChars) {
                out.add(ch)
                continue
            }
            val blocks = ch.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val parts = mutableListOf<String>()
            val cur = StringBuilder()
            for (b in blocks) {
                if (cur.isNotEmpty() && cur.length + b.length + 2 > maxChars) {
                    parts.add(cur.toString())
                    cur.clear()
                }
                if (cur.isNotEmpty()) cur.append("\n\n")
                cur.append(b)
            }
            if (cur.isNotEmpty()) parts.add(cur.toString())
            if (parts.size <= 1) {
                out.add(ch)
                continue
            }
            parts.forEachIndexed { i, partText ->
                out.add(
                    Chapter(
                        if (i == 0) ch.title else "${ch.title} (${i + 1})",
                        partText
                    )
                )
            }
        }
        return out
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

    // ---------------- MOBI (PalmDOC) — LEGACY ----------------
    /**
     * MOBI import was removed in Phase E. This parser survives for
     * exactly one job: the bookshelf's one-time migration converts
     * already-imported MOBI books to extracted text files, so books
     * the user already has keep their text and stay readable. New
     * MOBI files are refused at import.
     */
    internal fun parseMobiLegacy(file: File): ParsedBook {
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
