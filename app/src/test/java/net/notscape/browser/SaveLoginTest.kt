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
    fun resolveSaveInsertsWhenNoEntry() {
        val action = SaveLogin.resolveSave(
            emptyList(), "example.com", "alice", "pw"
        ) { "pw" }
        assertEquals(SaveLogin.SaveAction.Insert, action)
    }

    @Test
    fun resolveSaveUpdatesExistingEntry() {
        val entry = VaultEntry(7, "example.com", "alice", "enc")
        val action = SaveLogin.resolveSave(
            listOf(entry), "example.com", "alice", "newpw"
        ) { "oldpw" }
        assertEquals(SaveLogin.SaveAction.Update(entry), action)
    }

    @Test
    fun resolveSaveNoopsWhenPasswordMatches() {
        val entry = VaultEntry(7, "example.com", "alice", "enc")
        val action = SaveLogin.resolveSave(
            listOf(entry), "example.com", "alice", "pw"
        ) { "pw" }
        assertEquals(SaveLogin.SaveAction.AlreadySaved, action)
    }

    @Test
    fun resolveSaveIgnoresOtherUsernames() {
        val entry = VaultEntry(7, "example.com", "bob", "enc")
        val action = SaveLogin.resolveSave(
            listOf(entry), "example.com", "alice", "pw"
        ) { "pw" }
        assertEquals(SaveLogin.SaveAction.Insert, action)
    }

    @Test
    fun pickFetchLoginAcceptsClearSameOriginPost() {
        val picked = SaveLogin.pickFetchLogin(
            listOf("email" to "alice@example.com", "password" to "pw123"),
            "POST",
            "https://example.com/api/login",
            "https://example.com/login"
        )
        assertEquals("alice@example.com" to "pw123", picked)
    }

    @Test
    fun pickFetchLoginRejectsGet() {
        assertNull(
            SaveLogin.pickFetchLogin(
                listOf("username" to "alice", "password" to "pw"),
                "GET",
                "https://example.com/api/login",
                "https://example.com/login"
            )
        )
    }

    @Test
    fun pickFetchLoginRejectsCrossOrigin() {
        assertNull(
            SaveLogin.pickFetchLogin(
                listOf("username" to "alice", "password" to "pw"),
                "POST",
                "https://auth.other.com/login",
                "https://example.com/login"
            )
        )
    }

    @Test
    fun pickFetchLoginRejectsAmbiguity() {
        // Two username-like keys: never capture.
        assertNull(
            SaveLogin.pickFetchLogin(
                listOf(
                    "username" to "alice", "email" to "alice@example.com",
                    "password" to "pw"
                ),
                "POST",
                "https://example.com/api/login",
                "https://example.com/login"
            )
        )
        // No password key: never capture.
        assertNull(
            SaveLogin.pickFetchLogin(
                listOf("username" to "alice", "token" to "xyz"),
                "POST",
                "https://example.com/api/login",
                "https://example.com/login"
            )
        )
        // Blank password: never capture.
        assertNull(
            SaveLogin.pickFetchLogin(
                listOf("username" to "alice", "password" to "  "),
                "POST",
                "https://example.com/api/login",
                "https://example.com/login"
            )
        )
    }

    @Test
    fun originOfCases() {
        assertEquals(
            "https://example.com:8443",
            SaveLogin.originOf("https://user@example.com:8443/a?b=c#d")
        )
        assertEquals("https://example.com", SaveLogin.originOf("https://EXAMPLE.com/"))
        assertNull(SaveLogin.originOf("example.com/login"))
        assertNull(SaveLogin.originOf("ftp://example.com/x"))
    }

    @Test
    fun neverSaveListMath() {
        assertEquals(emptySet<String>(), SaveLogin.parseNeverSave(""))
        assertEquals(
            setOf("a.com", "b.org"),
            SaveLogin.parseNeverSave(" B.org ,a.com,, ")
        )
        assertEquals("a.com,b.org", SaveLogin.withNeverSave("b.org", "A.com"))
        assertEquals("a.com", SaveLogin.withoutNeverSave("a.com,b.org", "B.ORG"))
        assertTrue(SaveLogin.isNeverSave("a.com,b.org", "A.COM"))
        assertFalse(SaveLogin.isNeverSave("a.com", "b.org"))
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
