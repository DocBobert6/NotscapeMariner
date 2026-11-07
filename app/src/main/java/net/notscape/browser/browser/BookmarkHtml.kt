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
                        pendingHref = href
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
                                    folders.lastOrNull() ?: ""
                                )
                            )
                        }
                        pendingHref = null
                        pendingTitle = null
                    }
                }
            } else {
                if (pendingFolder != null && pendingFolder!!.isEmpty()) {
                    pendingFolder = tok.trim()
                }
                pendingTitle?.append(tok)
            }
        }
        return out
    }

    private var pendingHref: String? = null
    private var pendingTitle: StringBuilder? = null
}
