package net.notscape.browser

import net.notscape.browser.browser.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The compiled filter cache (0.0.997): what writeCompiled
 * stores, readCompiled must return exactly - and any doubt
 * (missing file) yields null, never a partial rule set. */
class FilterCompiledTest {

    private fun sampleData(): FilterEngine.FilterData {
        val data = FilterEngine.FilterData()
        data.hosts.addAll(setOf("ads.example.com", "tracker.example.net"))
        data.exceptions.add("good.example.com")
        data.scoped["video.example.com"] = mutableListOf(
            FilterEngine.ScopedRule(
                pathPrefix = "/vast",
                domains = setOf("publisher.example"),
                notDomains = setOf("blocked.example"),
                exception = false,
                thirdParty = true,
                important = true
            ),
            FilterEngine.ScopedRule(
                pathPrefix = null,
                domains = null,
                notDomains = null,
                exception = true,
                thirdParty = null,
                important = false
            ),
            FilterEngine.ScopedRule(
                pathPrefix = null,
                domains = setOf("a.example"),
                notDomains = null,
                exception = false,
                thirdParty = false,
                important = false
            )
        )
        data.generic.addAll(listOf(".ad-banner", "#sponsor"))
        data.perDomain["news.example"] =
            mutableListOf(".paywall-pop")
        data.docHosts.add("elemhide.example")
        data.excGeneric.add(".keep-me")
        data.excDomain["shop.example"] = mutableListOf(".keep-too")
        data.hideOff.add("generichide.example")
        return data
    }

    @Test
    fun compiledRoundTrips() {
        val dir = Files.createTempDirectory("compiled-test").toFile()
        try {
            val data = sampleData()
            FilterEngine.writeCompiled(dir, data, 123456789L)
            val back = FilterEngine.readCompiled(dir)
            assertTrue(back != null)
            val (restored, newest) = back!!
            assertEquals(123456789L, newest)
            assertEquals(data.hosts, restored.hosts)
            assertEquals(data.exceptions, restored.exceptions)
            assertEquals(data.generic.toList(), restored.generic.toList())
            assertEquals(data.perDomain, restored.perDomain)
            assertEquals(data.docHosts, restored.docHosts)
            assertEquals(data.excGeneric, restored.excGeneric)
            assertEquals(data.excDomain, restored.excDomain)
            assertEquals(data.hideOff, restored.hideOff)
            val rules = restored.scoped["video.example.com"]!!
            assertEquals(3, rules.size)
            assertEquals("/vast", rules[0].pathPrefix)
            assertEquals(setOf("publisher.example"), rules[0].domains)
            assertEquals(setOf("blocked.example"), rules[0].notDomains)
            assertEquals(false, rules[0].exception)
            assertEquals(true, rules[0].thirdParty)
            assertEquals(true, rules[0].important)
            assertNull(rules[1].pathPrefix)
            assertNull(rules[1].domains)
            assertNull(rules[1].thirdParty)
            assertEquals(true, rules[1].exception)
            assertEquals(false, rules[2].thirdParty)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingCacheReadsNull() {
        val dir = Files.createTempDirectory("compiled-empty").toFile()
        try {
            assertNull(FilterEngine.readCompiled(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fingerprintMismatchReadsNull() {
        val dir = Files.createTempDirectory("compiled-fp").toFile()
        try {
            FilterEngine.writeCompiled(dir, sampleData(), 1L)
            // A list file appearing/changing invalidates the
            // cache: the fingerprint no longer matches.
            File(dir, "list0.txt").writeText("||ads.example^")
            assertNull(FilterEngine.readCompiled(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
