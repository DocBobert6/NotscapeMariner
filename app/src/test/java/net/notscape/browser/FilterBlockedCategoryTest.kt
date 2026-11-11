package net.notscape.browser

import net.notscape.browser.browser.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta.63 #26: blocked-count categories. The list a rule comes
 * from decides its category (ads vs trackers); the user's own
 * rules count as other. History is one line per day and
 * round-trips through load + snapshot.
 */
class FilterBlockedCategoryTest {

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

    private fun parse(
        text: String,
        category: Int = FilterEngine.CAT_ADS
    ): Sink {
        val s = Sink()
        FilterEngine.parse(
            text, s.hosts, s.exceptions, s.scoped, s.generic,
            s.perDomain, s.docHosts, s.genericExc, s.perDomainExc,
            s.hideOff, category, s.trackerHosts
        )
        return s
    }

    @Test
    fun categoryForListMapsAdsAndTrackers() {
        assertEquals(FilterEngine.CAT_ADS, FilterEngine.categoryForList(0))
        assertEquals(FilterEngine.CAT_TRACKER, FilterEngine.categoryForList(1))
        assertEquals(FilterEngine.CAT_ADS, FilterEngine.categoryForList(2))
        assertEquals(FilterEngine.CAT_TRACKER, FilterEngine.categoryForList(3))
        assertEquals(FilterEngine.CAT_ADS, FilterEngine.categoryForList(4))
        assertEquals(FilterEngine.CAT_ADS, FilterEngine.categoryForList(5))
        assertEquals(FilterEngine.CAT_ADS, FilterEngine.categoryForList(6))
    }

    @Test
    fun trackerPureHostLandsInTrackerHostsOnly() {
        val s = parse("||tracker.example^", FilterEngine.CAT_TRACKER)
        assertTrue(s.hosts.contains("tracker.example"))
        assertTrue(s.trackerHosts.contains("tracker.example"))
        val a = parse("||ads.example^", FilterEngine.CAT_ADS)
        assertTrue(a.hosts.contains("ads.example"))
        assertFalse(a.trackerHosts.contains("ads.example"))
    }

    @Test
    fun trackerExceptionDoesNotLandInTrackerHosts() {
        val s = parse("@@||tracker.example^", FilterEngine.CAT_TRACKER)
        assertTrue(s.exceptions.contains("tracker.example"))
        assertFalse(s.trackerHosts.contains("tracker.example"))
    }

    @Test
    fun scopedRuleIsStampedWithItsListCategory() {
        val s = parse(
            "||tracker.example^\$domain=pub.example",
            FilterEngine.CAT_TRACKER
        )
        val rule = s.scoped["tracker.example"]!!.single()
        assertEquals(FilterEngine.CAT_TRACKER, rule.category)
        val a = parse("||ads.example^\$domain=pub.example")
        assertEquals(
            FilterEngine.CAT_ADS,
            a.scoped["ads.example"]!!.single().category
        )
    }

    @Test
    fun historyRoundTripsAndKeepsNewestFirst() {
        FilterEngine.loadBlockedHistory(
            "2026-11-07=3,1,0\n2026-11-06=0,0,2\n"
        )
        val view = FilterEngine.blockedHistory()
        assertEquals(listOf(3L, 1L, 0L), view["2026-11-07"])
        assertEquals(listOf(0L, 0L, 2L), view["2026-11-06"])
    }

    @Test
    fun historyIgnoresMalformedLines() {
        FilterEngine.loadBlockedHistory(
            "not-a-date=1,2,3\n2099-01-01=3,1\n2026-11-05=1,2,3\n"
        )
        val view = FilterEngine.blockedHistory()
        assertFalse(view.containsKey("not-a-date"))
        assertFalse(view.containsKey("2099-01-01"))
        assertEquals(listOf(1L, 2L, 3L), view["2026-11-05"])
    }

    @Test
    fun historyFlushIsNullWhenNothingChanged() {
        FilterEngine.loadBlockedHistory("2026-11-05=1,2,3\n")
        assertEquals(null, FilterEngine.flushBlockedHistory())
    }
}
