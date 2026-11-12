package net.notscape.browser

import net.notscape.browser.browser.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The document-start script builder (0.0.99995). */
class DocumentStartTest {

    @Test
    fun emptyCssBuildsNothing() {
        assertEquals(
            "", FilterEngine.buildDocumentStartScript("", setOf("x.com"))
        )
    }

    @Test
    fun scriptCarriesOffHostsAndCss() {
        val js = FilterEngine.buildDocumentStartScript(
            ".ad{display:none!important}", setOf("example.com")
        )
        assertTrue(js.contains("\"example.com\""))
        assertTrue(js.contains(".ad{display:none!important}"))
        assertTrue(js.contains("location.hostname"))
    }

    @Test
    fun scriptWalksParentDomainsForOffHosts() {
        val js = FilterEngine.buildDocumentStartScript(
            ".ad{display:none!important}", setOf("example.com")
        )
        // Subdomain-aware (beta.56): the script walks
        // location.hostname up to parent domains instead of
        // one exact match, so a $generichide page scope covers
        // its subdomains exactly like the per-navigation sheet.
        assertTrue(js.contains("location.hostname"))
        assertTrue(js.contains("indexOf('.')"))
        assertTrue(js.contains("substring(d+1)"))
        assertFalse(js.contains("OFF.indexOf(location.hostname)>=0"))
    }

    @Test
    fun scriptEscapesQuotesAndBackslashes() {
        val js = FilterEngine.buildDocumentStartScript(
            "a[href='x']{display:none!important}", emptySet()
        )
        assertTrue(js.contains("a[href=\\'x\\']"))
    }

    @Test
    fun chunkerCapsAndSuffixes() {
        val css = FilterEngine.chunkCosmeticSelectors(
            (1..30).map { ".c$it" }, budget = 100_000
        )
        // 25 in the first rule, 5 in the second.
        assertEquals(
            2, css.split("{display:none!important}").size - 1
        )
        assertTrue(css.startsWith(".c1,.c2"))
    }
}
