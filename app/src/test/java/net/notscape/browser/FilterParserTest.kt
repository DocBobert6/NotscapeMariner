package net.notscape.browser

import net.notscape.browser.browser.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the ABP filter-list compiler. The parser is pure
 * (strings in, collections out), so these run on the JVM and guard the
 * behaviours that matter: scoped rules must never flatten into global
 * host blocks, and unsupported rules are skipped, never half-applied.
 */
class FilterParserTest {

    private class Sink {
        val hosts = HashSet<String>()
        val exceptions = HashSet<String>()
        val scoped = HashMap<String, MutableList<FilterEngine.ScopedRule>>()
        val generic = LinkedHashSet<String>()
        val perDomain = HashMap<String, MutableList<String>>()
    }

    private fun parse(text: String): Sink {
        val s = Sink()
        FilterEngine.parse(text, s.hosts, s.exceptions, s.scoped, s.generic, s.perDomain)
        return s
    }

    @Test
    fun pureHostRuleGoesToFlatSet() {
        val s = parse("||ads.example.com^")
        assertTrue(s.hosts.contains("ads.example.com"))
        assertTrue(s.scoped.isEmpty())
    }

    @Test
    fun exceptionHostRuleGoesToExceptions() {
        val s = parse("@@||cdn.example.com^")
        assertTrue(s.exceptions.contains("cdn.example.com"))
        assertFalse(s.hosts.contains("cdn.example.com"))
    }

    @Test
    fun domainScopedRuleDoesNotFlatten() {
        val s = parse("||jwplayer.com^\$domain=publisher.example|news.example")
        assertFalse("scoped rule must not become a global host block", s.hosts.contains("jwplayer.com"))
        val rules = s.scoped["jwplayer.com"] ?: error("missing scoped rule")
        assertEquals(setOf("publisher.example", "news.example"), rules.single().domains)
    }

    @Test
    fun negativeDomainSetIsCaptured() {
        val s = parse("||tracker.example^\$domain=~good.example")
        val r = s.scoped["tracker.example"]!!.single()
        assertEquals(setOf("good.example"), r.notDomains)
    }

    @Test
    fun thirdPartyRuleBecomesScoped() {
        val s = parse("||ads.example.net^\$third-party")
        assertFalse(s.hosts.contains("ads.example.net"))
        assertEquals(true, s.scoped["ads.example.net"]!!.single().thirdParty)
    }

    @Test
    fun firstPartyRuleBecomesScoped() {
        val s = parse("||cdn.example.net^\$~third-party")
        assertEquals(false, s.scoped["cdn.example.net"]!!.single().thirdParty)
        assertFalse(s.hosts.contains("cdn.example.net"))
    }

    @Test
    fun importantRuleBecomesScopedWithFlag() {
        val s = parse("||telemetry.example^\$important")
        assertFalse(s.hosts.contains("telemetry.example"))
        val r = s.scoped["telemetry.example"]!!.single()
        assertTrue(r.important)
        assertFalse(r.exception)
    }

    @Test
    fun pathRuleKeepsPrefix() {
        val s = parse("||ads.example.org/banners/*")
        val r = s.scoped["ads.example.org"]!!.single()
        assertEquals("/banners/", r.pathPrefix)
        assertFalse(s.hosts.contains("ads.example.org"))
    }

    @Test
    fun unsupportedOptionRuleIsSkipped() {
        val s = parse("||video.example^\$rewrite=abp-resource:blank-js")
        assertTrue(s.hosts.isEmpty())
        assertTrue(s.scoped.isEmpty())
    }

    @Test
    fun typedRuleAppliesToHost() {
        // Resource-type options no longer drop the rule (0.0.957):
        // a WebView interceptor cannot split requests by type, so
        // the host part is honoured for every type. The old test
        // here asserted the skip that silently lost thousands of
        // live list rules.
        val s = parse("||media.example^\$script")
        assertTrue(s.hosts.contains("media.example"))
    }

    @Test
    fun cosmeticRulesAreCollected() {
        val s = parse("##.ad-banner\nexample.com##.sponsored-box")
        assertTrue(s.generic.contains(".ad-banner"))
        assertEquals(listOf(".sponsored-box"), s.perDomain["example.com"])
    }

    @Test
    fun complexSelectorsAreRejected() {
        val s = parse("##div:has(> .ad)")
        assertTrue(s.generic.isEmpty())
    }

    @Test
    fun hostsFileFormatParses() {
        val hosts = HashSet<String>()
        FilterEngine.parseHosts(
            "# comment\n0.0.0.0 ads.example.com\n127.0.0.1 tracker.example.net\nbad line\n0.0.0.0 localhost",
            hosts
        )
        assertTrue(hosts.contains("ads.example.com"))
        assertTrue(hosts.contains("tracker.example.net"))
        assertFalse(hosts.contains("localhost"))
    }

    @Test
    fun documentHostRuleIsKept() {
        val s = parse("||popunder.example^\$document")
        assertTrue(s.hosts.contains("popunder.example"))
    }

    @Test
    fun adGuardPartyAliasesAreScoped() {
        val s = parse("||kiesta.example^\$3p")
        assertEquals(true, s.scoped["kiesta.example"]!!.single().thirdParty)
        val f = parse("||onep.example^\$1p")
        assertEquals(false, f.scoped["onep.example"]!!.single().thirdParty)
    }

    @Test
    fun typeAndPartyComboIsKeptScoped() {
        val s = parse("||chtbl.example^\$script,third-party")
        assertFalse(s.hosts.contains("chtbl.example"))
        assertEquals(true, s.scoped["chtbl.example"]!!.single().thirdParty)
    }

    @Test
    fun cspRuleIsSkippedNotFlattened() {
        val s = parse("||x.example^\$csp=script-src 'none'")
        assertTrue(s.hosts.isEmpty())
        assertTrue(s.scoped.isEmpty())
    }

    @Test
    fun generichideExceptionIsNotANetworkException() {
        val s = parse("@@||pub.example^\$generichide")
        assertTrue(s.exceptions.isEmpty())
        assertTrue(s.scoped.isEmpty())
    }

    @Test
    fun removeparamRuleIsSkipped() {
        val s = parse("||t.example^\$removeparam=ref")
        assertTrue(s.hosts.isEmpty())
        assertTrue(s.scoped.isEmpty())
    }

    @Test
    fun badfilterDisablesItsTwin() {
        val s = parse("||bad.example^\n||bad.example^\$badfilter")
        assertFalse(s.hosts.contains("bad.example"))
        val r = parse("||bad2.example^\$badfilter\n||bad2.example^")
        assertFalse(r.hosts.contains("bad2.example"))
    }
}
