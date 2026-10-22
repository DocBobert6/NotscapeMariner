package net.notscape.browser

import net.notscape.browser.browser.Article
import net.notscape.browser.browser.ArticleExtractor
import net.notscape.browser.browser.buildReaderShell
import net.notscape.browser.browser.readerThemeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleReaderTest {

    @Test
    fun sanitizeStripsActiveContent() {
        val dirty = """
            <h2>Head</h2>
            <script>alert(1)</script>
            <iframe src="https://evil.example"></iframe>
            <p onclick="steal()">Body</p>
            <a href="javascript:alert(2)">click</a>
            <form action="https://evil.example"><input type="text"></form>
            <img src="https://img.example/a.jpg" onerror="hack()">
        """.trimIndent()
        val clean = ArticleExtractor.sanitize(dirty)
        assertFalse(clean.contains("<script"))
        assertFalse(clean.contains("<iframe"))
        assertFalse(clean.contains("onclick"))
        assertFalse(clean.contains("onerror"))
        assertFalse(clean.contains("javascript:"))
        assertFalse(clean.contains("<form"))
        assertFalse(clean.contains("<input"))
        assertTrue(clean.contains("<h2>Head</h2>"))
        assertTrue(clean.contains("https://img.example/a.jpg"))
        assertTrue(clean.contains("Body"))
    }

    @Test
    fun sanitizeKeepsArticleStructure() {
        val html = "<ul><li>one</li></ul><figure><img src=\"a.png\">" +
            "<figcaption>cap</figcaption></figure><pre>code</pre>"
        val clean = ArticleExtractor.sanitize(html)
        assertEquals(html, clean)
    }

    @Test
    fun shellUsesSafariThemeAndEscapesTitle() {
        val article = Article(
            url = "https://example.com/story",
            title = "A <b>bold</b> claim",
            byline = "Jane Doe",
            siteName = "Example",
            publishedTime = "2026-01-01",
            lang = "en",
            dir = "ltr",
            contentHtml = "<p>Hello</p>"
        )
        val shell = buildReaderShell(article, "sepia", "serif", 19)
        assertTrue(shell.contains("#F8F1E3"))
        assertTrue(shell.contains("#4F321C"))
        assertTrue(shell.contains("A &lt;b&gt;bold&lt;/b&gt; claim"))
        assertTrue(shell.contains("Jane Doe"))
        assertTrue(shell.contains("<base href=\"https://example.com/story\">"))
        assertTrue(shell.contains("font-size: 19px"))
        assertTrue(shell.contains("Georgia"))
        val night = buildReaderShell(article, "night", "sans", 19)
        assertTrue(night.contains("#121212"))
        assertTrue(night.contains("color-scheme: dark"))
    }

    @Test
    fun legacyThemeKeysNormalize() {
        assertEquals("white", readerThemeKey("light"))
        assertEquals("night", readerThemeKey("dark"))
        assertEquals("sepia", readerThemeKey("sepia"))
        assertEquals("gray", readerThemeKey("gray"))
        assertEquals("white", readerThemeKey("bogus"))
    }
}
