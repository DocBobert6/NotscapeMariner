package net.notscape.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Audit4Parity1cTest {

    @Test
    fun incomingGateAcceptsHttpHttps() {
        assertEquals("https://example.com/a", sanitizeIncomingUrl("https://example.com/a"))
        assertEquals("http://example.com/", sanitizeIncomingUrl("  http://example.com/  "))
    }

    @Test
    fun incomingGateRejectsSchemesAndBlanks() {
        assertNull(sanitizeIncomingUrl("javascript:alert(1)"))
        assertNull(sanitizeIncomingUrl("intent://x#Intent;end"))
        assertNull(sanitizeIncomingUrl("file:///etc/passwd"))
        assertNull(sanitizeIncomingUrl(""))
        assertNull(sanitizeIncomingUrl(null))
        assertNull(sanitizeIncomingUrl("just some shared text"))
    }
}
