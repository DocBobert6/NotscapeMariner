package net.notscape.browser.browser

import android.content.Context
import org.json.JSONObject

/**
 * Reader-mode extraction with Mozilla Readability 0.6.0 (Apache-2.0,
 * vendored under assets/readability/). The library runs inside the live
 * tab's WebView against a CLONE of the document — parse() destroys the
 * DOM it is given — gated by isProbablyReaderable, exactly as Firefox
 * does. The extracted HTML is sanitized in Kotlin and rendered by
 * ReaderScreen in a self-built shell; the reader WebView also runs with
 * JavaScript disabled, as defense in depth.
 */
data class Article(
    val url: String,
    val title: String,
    val byline: String,
    val siteName: String,
    val publishedTime: String,
    val lang: String,
    val dir: String,
    val contentHtml: String
)

/** The most recently extracted article, handed from the browser tab to
 *  the reader screen without re-fetching the page. */
object ArticleCache {
    @Volatile
    var current: Article? = null
}

object ArticleExtractor {

    @Volatile
    private var librarySource: String? = null

    /** Readability.js + Readability-readerable.js, concatenated once. */
    fun loadLibrary(context: Context): String {
        librarySource?.let { return it }
        val src = buildString {
            append(context.assets.open("readability/Readability.js")
                .bufferedReader().use { it.readText() })
            append("\n;\n")
            append(context.assets.open("readability/Readability-readerable.js")
                .bufferedReader().use { it.readText() })
        }
        librarySource = src
        return src
    }

    /**
     * The extraction glue, appended after the library source and passed
     * as ONE evaluateJavascript string. Injecting a <script> element
     * instead would be subject to the page's CSP; the evaluated string
     * is not. Returns a JS object (never a stringified string) so the
     * callback hands us clean JSON.
     */
    const val GLUE_JS = """
;
(function () {
  try {
    if (typeof Readability === 'undefined' ||
        typeof isProbablyReaderable === 'undefined') return null;
    if (!isProbablyReaderable(document)) return null;
    var article = new Readability(
      document.cloneNode(true), { maxElemsToParse: 80000 }
    ).parse();
    if (!article || !article.content) return null;
    return {
      title: article.title || '',
      byline: article.byline || '',
      siteName: article.siteName || '',
      publishedTime: article.publishedTime || '',
      lang: article.lang || '',
      dir: article.dir || '',
      content: article.content
    };
  } catch (e) { return null; }
})()
"""

    /** Decode the evaluateJavascript callback into an [Article]. */
    fun parseResult(raw: String?, url: String): Article? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val o = JSONObject(raw)
            val content = o.optString("content")
            if (content.isBlank()) return null
            Article(
                url = url,
                title = o.optString("title"),
                byline = o.optString("byline"),
                siteName = o.optString("siteName"),
                publishedTime = o.optString("publishedTime"),
                lang = o.optString("lang"),
                dir = o.optString("dir"),
                contentHtml = sanitize(content)
            )
        }.getOrNull()
    }

    /**
     * Readability does not guarantee script-free output (Mozilla's own
     * README says to sanitize), so: drop active-content elements whole,
     * strip inline event handlers, and neutralize javascript: URLs.
     * The reader WebView additionally disables JavaScript entirely.
     */
    fun sanitize(html: String): String {
        var out = html
        for (tag in listOf(
            "script", "iframe", "object", "form", "noscript",
            "template", "svg", "button", "select", "textarea"
        )) {
            out = out.replace(
                Regex("(?is)<$tag\\b[^>]*>.*?</$tag\\s*>"), ""
            )
            out = out.replace(Regex("(?is)<$tag\\b[^>]*/>"), "")
        }
        for (tag in listOf("embed", "link", "meta", "base", "title", "input")) {
            out = out.replace(Regex("(?is)<$tag\\b[^>]*>"), "")
        }
        out = out.replace(
            Regex("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"), ""
        )
        out = out.replace(
            Regex("(?i)(href|src)\\s*=\\s*\"\\s*javascript:[^\"]*\""), "$1=\"#\""
        )
        out = out.replace(
            Regex("(?i)(href|src)\\s*=\\s*'\\s*javascript:[^']*'"), "$1='#'"
        )
        return out
    }
}

/** Safari Reader page themes (values from Safari's own Reader CSS). */
data class ReaderPalette(
    val bg: String,
    val fg: String,
    val link: String,
    val dark: Boolean
)

fun readerPalette(theme: String): ReaderPalette = when (theme) {
    "sepia" -> ReaderPalette("#F8F1E3", "#4F321C", "#4F321C", dark = false)
    "gray" -> ReaderPalette("#4A4A4D", "#D7D7D9", "#D7D7D9", dark = true)
    "night" -> ReaderPalette("#121212", "#B0B0B0", "#B0B0B0", dark = true)
    else -> ReaderPalette("#FFFFFF", "#1B1B1B", "#1B1B1B", dark = false)
}

/** Normalize the pre-0.0.57 stored theme keys to the Safari set. */
fun readerThemeKey(stored: String): String = when (stored) {
    "light" -> "white"
    "dark" -> "night"
    "white", "sepia", "gray", "night" -> stored
    else -> "white"
}

/** Normalize stored reader-font keys (Phase E): the typeset options
 *  are Open Sans / Roboto / Inter; the pre-Phase-E keys (serif, sans,
 *  mono) fold onto them. */
fun readerFontKey(stored: String): String = when (stored) {
    "opensans", "serif", "mono" -> "opensans"
    "roboto", "sans" -> "roboto"
    "inter" -> "inter"
    else -> "opensans"
}

fun readerFontStack(font: String): String = when (readerFontKey(font)) {
    "inter" -> "'Inter', 'Roboto', system-ui, sans-serif"
    "roboto" -> "'Roboto', system-ui, sans-serif"
    else -> "'Open Sans', 'Roboto', system-ui, sans-serif"
}

/**
 * The @font-face rule for a bundled reader font, with the font
 * embedded as a data URI (the reader document's base URL is the
 * article's own, so asset files can't be referenced by path).
 * Roboto is a system font and needs no embedding.
 */
fun readerFontFaceCss(context: android.content.Context, font: String): String {
    val (family, asset) = when (readerFontKey(font)) {
        "inter" -> "Inter" to "fonts/inter.ttf"
        "roboto" -> return ""
        else -> "Open Sans" to "fonts/opensans.ttf"
    }
    val b64 = runCatching {
        context.assets.open(asset).use { input ->
            android.util.Base64.encodeToString(input.readBytes(), android.util.Base64.NO_WRAP)
        }
    }.getOrNull() ?: return ""
    return "@font-face { font-family: '$family'; " +
        "src: url(data:font/ttf;base64,$b64) format('truetype'); font-weight: 100 900; }"
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;")

/**
 * The reader document shell: Safari-style single column with a site /
 * title / byline header block over the extracted article HTML. All
 * presentation lives here; the article HTML is bare semantic markup.
 */
fun buildReaderShell(
    article: Article,
    theme: String,
    font: String,
    fontSizeSp: Int,
    fontFaceCss: String = ""
): String {
    val p = readerPalette(theme)
    val bylineBits = listOf(article.byline, article.publishedTime)
        .filter { it.isNotBlank() }
    val byline = if (bylineBits.isEmpty()) "" else
        """<div class="byline">${escapeHtml(bylineBits.joinToString(" · "))}</div>"""
    val site = if (article.siteName.isBlank()) "" else
        """<div class="site">${escapeHtml(article.siteName)}</div>"""
    val lang = article.lang.ifBlank { "en" }
    val dir = article.dir.ifBlank { "ltr" }
    return """<!DOCTYPE html>
<html lang="${escapeHtml(lang)}" dir="${escapeHtml(dir)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<base href="${escapeHtml(article.url)}">
<title>${escapeHtml(article.title)}</title>
<style>
  $fontFaceCss
  :root { color-scheme: ${if (p.dark) "dark" else "light"}; }
  html, body { margin: 0; padding: 0; background: ${p.bg}; }
  body {
    color: ${p.fg};
    font-family: ${readerFontStack(font)};
    font-size: ${fontSizeSp}px;
    line-height: 1.55;
    -webkit-text-size-adjust: 100%;
  }
  main { max-width: 680px; margin: 0 auto; padding: 28px 20px 72px; }
  .site {
    font-size: 13px; letter-spacing: 0.05em; text-transform: uppercase;
    opacity: 0.6; margin-bottom: 10px;
  }
  h1 { font-size: 1.5em; line-height: 1.22; margin: 0 0 10px; font-weight: 700; }
  .byline { font-size: 0.82em; opacity: 0.65; margin-bottom: 26px; }
  img, video, picture { max-width: 100%; height: auto; border-radius: 10px; }
  figure { margin: 1.2em 0; }
  figcaption { font-size: 0.8em; opacity: 0.65; margin-top: 6px; text-align: center; }
  a { color: ${p.link}; }
  pre {
    overflow-x: auto; background: rgba(127,127,127,0.14);
    padding: 12px; border-radius: 10px; font-size: 0.86em;
  }
  code { font-family: 'Roboto Mono', ui-monospace, Menlo, monospace; }
  blockquote {
    margin: 1em 0; padding-left: 14px;
    border-left: 3px solid rgba(127,127,127,0.4);
  }
  table {
    display: block; overflow-x: auto; border-collapse: collapse;
    font-size: 0.9em; max-width: 100%;
  }
  td, th { border: 1px solid rgba(127,127,127,0.35); padding: 6px 8px; }
  hr { border: none; border-top: 1px solid rgba(127,127,127,0.35); margin: 1.6em 0; }
  * { overflow-wrap: break-word; }
</style>
</head>
<body>
<main>
  $site
  <h1>${escapeHtml(article.title)}</h1>
  $byline
  ${article.contentHtml}
</main>
</body>
</html>"""
}
