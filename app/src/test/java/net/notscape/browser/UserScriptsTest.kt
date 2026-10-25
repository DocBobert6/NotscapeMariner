package net.notscape.browser

import net.notscape.browser.browser.UserScript
import net.notscape.browser.browser.UserScripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** User scripts (0.0.99996): patterns, blobs, storage. */
class UserScriptsTest {

    @Test
    fun patternEscapesAndAnchors() {
        assertEquals(
            "^https:\\/\\/example\\.com\\/.*$",
            UserScripts.patternToRegexSource("https://example.com/*")
        )
        assertEquals(
            "^.*:\\/\\/.*\\.example\\.com\\/.*$",
            UserScripts.patternToRegexSource("*://*.example.com/*")
        )
    }

    @Test
    fun blobGatesOnPatternAndWrapsCode() {
        val blob = UserScripts.startScriptFor(
            UserScript("1", "x", "*://a.com/*", "doThing()", true)
        )
        assertTrue(blob.contains("location.href"))
        assertTrue(blob.contains("doThing()"))
        assertEquals(
            "",
            UserScripts.startScriptFor(
                UserScript("1", "x", "*://a.com/*", "doThing()", false)
            )
        )
        assertEquals(
            "",
            UserScripts.startScriptFor(
                UserScript("1", "x", "*://a.com/*", "  ", true)
            )
        )
    }

    @Test
    fun jsonRoundTrips() {
        val list = listOf(
            UserScript("a", "One", "*://x/*", "1+1", true),
            UserScript("b", "Two", "https://y/", "alert('q')", false)
        )
        assertEquals(list, UserScripts.parse(UserScripts.toJson(list)))
        assertEquals(emptyList<UserScript>(), UserScripts.parse(""))
        assertEquals(emptyList<UserScript>(), UserScripts.parse("junk"))
    }
}
