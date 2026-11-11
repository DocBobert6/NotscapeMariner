package net.notscape.browser.browser

/**
 * Netscape-format HTML bookmark import (audit P-m26):
 * parses the <DL>/<DT> folder nesting every browser
 * exports, returning (title, url, folder) triples.
 * Tolerates malformed input by skipping what does
 * not parse — a bad line never kills the import.
 */
object BookmarkHtml {

    fun parse(text: String): List<Triple<String, String, String>> {
        val out = mutableListOf<Triple<String, String, String>>()
        val folders = mutableListOf<String>()
        var pendingFolder: String? = null
        var depth = 0
        // Tokenize tags and text runs in document order.
        val tokenRegex = Regex("<[^>]*>|[^<]+")
        for (m in tokenRegex.findAll(text)) {
            val tok = m.value
            if (tok.startsWith("<")) {
                val tag = tok.lowercase()
                when {
                    tag.startsWith("<dl") -> {
                        depth++
                        pendingFolder?.let {
                            folders.add(it)
                            pendingFolder = null
                        }
                    }
                    tag.startsWith("</dl") -> {
                        depth--
                        // Folders track DL nesting above the root list:
                        // closing the inner list (depth back to 1) must
                        // drop its folder, not keep it for later links.
                        while (folders.size > maxOf(0, depth - 1)) folders.removeAt(folders.lastIndex)
                    }
                    tag.startsWith("<h3") -> {
                        // The folder name is the following text run.
                        pendingFolder = ""
                    }
                    tag.startsWith("</h3") -> Unit
                    tag.startsWith("<a ") || tag == "<a>" -> {
                        val href = Regex(
                            """href\s*=\s*"([^"]*)"""",
                            RegexOption.IGNORE_CASE
                        ).find(tok)?.groupValues?.get(1).orEmpty()
                        // Title is the following text run; handled
                        // by the text branch below via pendingHref.
                        pendingHref = unescapeHtml(href)
                        pendingTitle = StringBuilder()
                    }
                    tag.startsWith("</a") -> {
                        val href = pendingHref
                        if (!href.isNullOrBlank() &&
                            (href.startsWith("http://") || href.startsWith("https://"))
                        ) {
                            out.add(
                                Triple(
                                    pendingTitle.toString().trim(),
                                    href,
                                    folders.joinToString("/")
                                )
                            )
                        }
                        pendingHref = null
                        pendingTitle = null
                    }
                }
            } else {
                if (pendingFolder != null && pendingFolder!!.isEmpty()) {
                    pendingFolder = unescapeHtml(tok.trim())
                }
                pendingTitle?.append(unescapeHtml(tok))
            }
        }
        return out
    }

    private var pendingHref: String? = null
    private var pendingTitle: StringBuilder? = null

    /** One bookmark for export: title, URL, folder path
     *  ("/"-separated, "" for unfiled), and the epoch-ms
     *  creation time written as ADD_DATE. */
    data class ExportItem(
        val title: String, val url: String,
        val folder: String = "", val addedAt: Long = 0L
    )

    /** HTML-escape a value for an attribute or a text
     *  run. &amp; is replaced first here because the
     *  export writes fresh text (nothing is pre-escaped);
     *  [unescapeHtml] undoes it in the opposite order. */
    fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Undo the four entities [escapeHtml] writes. &amp;
     *  is replaced last so "&amp;lt;" stays "&lt;"
     *  instead of collapsing to "<". */
    fun unescapeHtml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")

    /** Netscape-format HTML bookmark export (beta.64
     *  #17): the inverse of [parse]. Folder paths nest
     *  as <DL> lists (sorted by path, so the output is
     *  deterministic), unfiled links sit at the root,
     *  and a re-import reproduces the same triples. */
    fun export(items: List<ExportItem>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file.\n")
        sb.append("     It will be read and overwritten.\n")
        sb.append("     DO NOT EDIT! -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n")
        sb.append("<H1>Bookmarks</H1>\n")
        sb.append("<DL><p>\n")
        // Folder tree, sorted by path.
        val root = FolderNode("")
        for (item in items) {
            if (item.folder.isBlank()) continue
            var node = root
            for (seg in item.folder.split("/")) {
                node = node.children.getOrPut(seg) { FolderNode(seg) }
            }
            node.items.add(item)
        }
        fun emit(node: FolderNode, depth: Int) {
            val pad = "    ".repeat(depth)
            for ((_, child) in node.children) {
                sb.append("$pad<DT><H3>${escapeHtml(child.name)}</H3>\n")
                sb.append("$pad<DL><p>\n")
                for (item in child.items) sb.emitLink(item, depth + 1)
                emit(child, depth + 1)
                sb.append("$pad</DL><p>\n")
            }
        }
        emit(root, 1)
        for (item in items.filter { it.folder.isBlank() }) {
            sb.emitLink(item, 1)
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    private fun StringBuilder.emitLink(item: ExportItem, depth: Int) {
        val pad = "    ".repeat(depth)
        append("$pad<DT><A HREF=\"${escapeHtml(item.url)}\"")
        if (item.addedAt > 0) append(" ADD_DATE=\"${item.addedAt / 1000}\"")
        append(">${escapeHtml(item.title.ifBlank { item.url })}</A>\n")
    }

    private class FolderNode(val name: String) {
        val children = sortedMapOf<String, FolderNode>()
        val items = mutableListOf<ExportItem>()
    }
}
