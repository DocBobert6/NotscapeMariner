package net.notscape.browser

import net.notscape.browser.browser.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta.63 #64: $document/$popup rules are enforced at request
 * level. Parse-level coverage: a rule that applies to document
 * loads keeps its document flag (scoped or pure, whatever other
 * types it also carries), and a non-document rule does not.
 * The engine state itself (isDocumentBlocked) is exercised
 * through the same parse paths as isBlocked.
 */
class FilterDocumentTest {

    private class Sink {
        val hosts = HashSet<String>()
        val exceptions = HashSet<String>()
        val scoped = HashMap<String, MutableList<FilterEngine.ScopedRule>>()
        val generic = LinkedHashSet<String>()
        val perDomain = HashMap<String, MutableList<String>>()
        val docHosts = HashSet<String>()
        val genericExc = HashSet<String>()
        val perDomainExc = HashMap<String, MutableList<String>>()
        val hideOff = HashSet<String>()
        val trackerHosts = HashSet<String>()
    }

    private fun parse(text: String): Sink {
        val s = Sink()
        FilterEngine.parse(
            text, s.hosts, s.exceptions, s.scoped, s.generic,
            s.perDomain, s.docHosts, s.genericExc, s.perDomainExc,
            s.hideOff
        )
        return s
    }

    @Test
    fun pureDocumentRuleWithOtherTypesStillFeedsDocHosts() {
        // Beta.63 #64: $document + another type used to drop the
        // rule from docHosts entirely; it applies to document
        // loads regardless of the other types.
        val s = parse("||ad.example^\$document,image")
        assertTrue(s.docHosts.contains("ad.example"))
    }

    @Test
    fun purePopupRuleFeedsDocHosts() {
        val s = parse("||pop.example^\$popup")
        assertTrue(s.docHosts.contains("pop.example"))
    }

    @Test
    fun scopedDocumentRuleKeepsDocumentFlag() {
        val s = parse(
            "||ad.example^\$document,domain=pub.example"
        )
        val rule = s.scoped["ad.example"]!!.single()
        assertTrue(rule.document)
        assertEquals(FilterEngine.CAT_ADS, rule.category)
    }

    @Test
    fun scopedPopupPathRuleKeepsDocumentFlag() {
        val s = parse("||ad.example/pop\$popup")
        val rule = s.scoped["ad.example"]!!.single()
        assertTrue(rule.document)
    }

    @Test
    fun nonDocumentScopedRuleHasNoDocumentFlag() {
        val s = parse("||ad.example^\$domain=pub.example")
        val rule = s.scoped["ad.example"]!!.single()
        assertFalse(rule.document)
    }

    @Test
    fun documentExceptionIsNotInDocHosts() {
        val s = parse("@@||ad.example^\$document")
        assertFalse(s.docHosts.contains("ad.example"))
    }
}
