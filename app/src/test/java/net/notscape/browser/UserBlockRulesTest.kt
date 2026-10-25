package net.notscape.browser

import net.notscape.browser.browser.UserBlockRules
import net.notscape.browser.browser.UserBlockRules.CosmeticRule
import net.notscape.browser.browser.UserBlockRules.NetRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserBlockRulesTest {
    @Test
    fun netRoundTrip() {
        val rules = listOf(
            NetRule.Host("ads.example.com"),
            NetRule.Url("https://cdn.example.com/a/b.png")
        )
        val raw = UserBlockRules.serializeNet(rules)
        assertEquals(rules, UserBlockRules.parseNet(raw))
    }

    @Test
    fun netParseSkipsMalformed() {
        val raw = "net\tHOST\tads.example.com\n" +
            "bogus line\n" +
            "net\tWHAT\tx\n" +
            "net\tHOST\t\n"
        assertEquals(
            listOf(NetRule.Host("ads.example.com")),
            UserBlockRules.parseNet(raw)
        )
    }

    @Test
    fun hostRuleMatchesSubdomains() {
        val rule = NetRule.Host("ads.example.com")
        assertTrue(
            UserBlockRules.netMatches(
                rule, "https", "ads.example.com", "/x.js"
            )
        )
        assertTrue(
            UserBlockRules.netMatches(
                rule, "https", "a.ads.example.com", "/x.js"
            )
        )
        assertFalse(
            UserBlockRules.netMatches(
                rule, "https", "notads.example.com", "/x.js"
            )
        )
        assertFalse(
            UserBlockRules.netMatches(
                rule, "https", "example.com", "/x.js"
            )
        )
    }

    @Test
    fun urlRuleIgnoresQuery() {
        val rule = NetRule.Url("https://cdn.example.com/a/b.png")
        assertTrue(
            UserBlockRules.netMatches(
                rule, "https", "cdn.example.com", "/a/b.png"
            )
        )
        assertFalse(
            UserBlockRules.netMatches(
                rule, "https", "cdn.example.com", "/a/c.png"
            )
        )
        assertFalse(
            UserBlockRules.netMatches(
                rule, "http", "cdn.example.com", "/a/b.png"
            )
        )
    }

    @Test
    fun cosmeticRoundTripAndHostFilter() {
        val rules = listOf(
            CosmeticRule("news.example", "div.ad-box"),
            CosmeticRule("other.example", "#promo")
        )
        val raw = UserBlockRules.serializeCosmetic(rules)
        assertEquals(rules, UserBlockRules.parseCosmetic(raw))
        assertEquals(
            listOf("div.ad-box"),
            UserBlockRules.selectorsForHost(rules, "news.example")
        )
        assertEquals(
            emptyList<String>(),
            UserBlockRules.selectorsForHost(rules, "third.example")
        )
    }

    @Test
    fun unsafeSelectorsRejected() {
        assertFalse(UserBlockRules.isSafeSelector(""))
        assertFalse(
            UserBlockRules.isSafeSelector("div{} .x")
        )
        assertFalse(
            UserBlockRules.isSafeSelector("div; color:red")
        )
        assertFalse(
            UserBlockRules.isSafeSelector("div < span")
        )
        assertTrue(
            UserBlockRules.isSafeSelector("div.ad-box > a[href]")
        )
        assertTrue(UserBlockRules.isSafeSelector("#top-promo"))
    }

    @Test
    fun cosmeticParseDropsUnsafe() {
        val raw = "cos\tnews.example\tdiv.ad-box\n" +
            "cos\tnews.example\tdiv{} .evil\n"
        assertEquals(
            listOf(CosmeticRule("news.example", "div.ad-box")),
            UserBlockRules.parseCosmetic(raw)
        )
    }
}
