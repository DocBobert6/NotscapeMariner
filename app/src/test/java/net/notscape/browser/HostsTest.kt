package net.notscape.browser

import net.notscape.browser.browser.Hosts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registrable-domain rules — the vault fill gate rests on these. */
class HostsTest {

    @Test
    fun registrableDomainBasics() {
        assertEquals("example.com", Hosts.registrableDomain("example.com"))
        assertEquals("example.com", Hosts.registrableDomain("www.example.com"))
        assertEquals("example.com", Hosts.registrableDomain("a.b.c.example.com"))
    }

    @Test
    fun registrableDomainMultiPartSuffixes() {
        assertEquals("example.co.uk", Hosts.registrableDomain("login.example.co.uk"))
        assertEquals("example.com.au", Hosts.registrableDomain("www.example.com.au"))
    }

    @Test
    fun sameSiteRules() {
        assertTrue(Hosts.sameSite("example.com", "example.com"))
        assertTrue(Hosts.sameSite("login.example.com", "www.example.com"))
        assertFalse(Hosts.sameSite("example.com", "example.org"))
        assertFalse(Hosts.sameSite("example.com", "badexample.com"))
        assertFalse(Hosts.sameSite("example.co.uk", "example.com"))
    }

    @Test
    fun ipsAndLocalhostAreTheirOwnSite() {
        assertEquals("192.168.1.10", Hosts.registrableDomain("192.168.1.10"))
        assertTrue(Hosts.sameSite("192.168.1.10", "192.168.1.10"))
        assertFalse(Hosts.sameSite("192.168.1.10", "192.168.1.11"))
    }
}
