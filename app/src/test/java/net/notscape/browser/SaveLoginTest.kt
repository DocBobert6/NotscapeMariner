package net.notscape.browser

import net.notscape.browser.data.VaultEntry
import net.notscape.browser.vault.SaveLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Save-prompt gating and dedup — the prompt itself is UI-only. */
class SaveLoginTest {

    @Test
    fun offerableGates() {
        assertTrue(SaveLogin.offerable("https://example.com/login", false, true))
        assertFalse(SaveLogin.offerable("http://example.com/login", false, true))
        assertFalse(SaveLogin.offerable("https://example.com/login", true, true))
        assertFalse(SaveLogin.offerable("https://example.com/login", false, false))
        assertFalse(SaveLogin.offerable(null, false, true))
    }

    @Test
    fun findExistingMatchesHostAndUsername() {
        val entries = listOf(
            VaultEntry(1, "https://example.com/login", "alice", "enc1"),
            VaultEntry(2, "other.org", "alice", "enc2")
        )
        assertEquals(1L, SaveLogin.findExisting(entries, "example.com", "alice")?.id)
        assertNull(SaveLogin.findExisting(entries, "example.com", "bob"))
        assertNull(SaveLogin.findExisting(entries, "other.org", "bob"))
    }

    @Test
    fun alreadySavedComparesDecryptedPassword() {
        val entry = VaultEntry(1, "example.com", "alice", "enc")
        assertTrue(SaveLogin.alreadySaved(entry, "pw") { "pw" })
        assertFalse(SaveLogin.alreadySaved(entry, "other") { "pw" })
        assertFalse(SaveLogin.alreadySaved(null, "pw") { "pw" })
        assertFalse(SaveLogin.alreadySaved(entry, "pw") { null })
    }
}
