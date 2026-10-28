package net.notscape.browser

import net.notscape.browser.data.VaultEntry
import net.notscape.browser.vault.VaultFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vault fill gate logic (Phase D security tests): the functions
 *  that decide whether a credential may be offered or injected. */
class VaultFillTest {

    private fun entry(site: String) = VaultEntry(1, site, "user", "enc")

    @Test
    fun hostOfParsesSiteStringsAndUrls() {
        assertEquals("example.com", VaultFill.hostOf("example.com"))
        assertEquals("example.com", VaultFill.hostOf("https://example.com/login"))
        assertEquals("example.com", VaultFill.hostOf("https://user:secret@Example.COM:8443/a?b#c"))
        assertEquals("login.example.co.uk", VaultFill.hostOf("login.example.co.uk"))
        assertNull(VaultFill.hostOf("   "))
        assertNull(VaultFill.hostOf("https://"))
    }

    @Test
    fun fillAllowedRequiresHttpsAndExactHostByDefault() {
        val e = entry("example.com")
        assertTrue(VaultFill.fillAllowed(e, "https://example.com/login"))
        // Audit S-m7: a subdomain no longer matches by default —
        // only when the entry opts into subdomains.
        assertFalse(VaultFill.fillAllowed(e, "https://login.example.com/"))
        assertTrue(
            VaultFill.fillAllowed(e, "https://login.example.com/", allowSubdomains = true)
        )
        assertFalse(VaultFill.fillAllowed(e, "http://example.com/login"))
        assertFalse(VaultFill.fillAllowed(e, "https://evil-example.com/"))
        assertFalse(VaultFill.fillAllowed(e, "https://example.com.evil.com/"))
        assertFalse(VaultFill.fillAllowed(e, null))
        assertFalse(VaultFill.fillAllowed(e, "about:blank"))
    }

    @Test
    fun matchesEntriesExactHostByDefaultSubdomainOnlyWhenOptedIn() {
        val entries = listOf(
            VaultEntry(1, "example.com", "a", "x"),
            VaultEntry(2, "other.org", "b", "x"),
            VaultEntry(3, "no host here", "c", "x")
        )
        // Audit S-m7: exact host always matches; a subdomain matches
        // only for entries whose opt-in lambda says so.
        assertEquals(
            listOf(1L),
            VaultFill.matchesEntries(entries, "example.com").map { it.id }
        )
        assertTrue(
            VaultFill.matchesEntries(entries, "login.example.com").isEmpty()
        )
        assertEquals(
            listOf(1L),
            VaultFill.matchesEntries(
                entries, "login.example.com"
            ) { it.id == 1L }.map { it.id }
        )
        assertTrue(VaultFill.matchesEntries(entries, "").isEmpty())
    }

    @Test
    fun readbackParsing() {
        val raw = "\"{\\\"u\\\":\\\"alice\\\",\\\"p\\\":\\\"s3cret!\\\"}\""
        val (u, p) = VaultFill.parseReadback(raw)
        assertEquals("alice", u)
        assertEquals("s3cret!", p)
        val (u2, p2) = VaultFill.parseReadback("\"{\\\"u\\\":null,\\\"p\\\":null}\"")
        assertNull(u2); assertNull(p2)
        assertEquals("up", VaultFill.parseLocate("\"up\""))
        assertEquals("", VaultFill.parseLocate("null"))
    }
}
