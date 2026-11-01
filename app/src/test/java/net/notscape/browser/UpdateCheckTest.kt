package net.notscape.browser

import net.notscape.browser.browser.UpdateChecker
import net.notscape.browser.browser.isNewerVersion
import net.notscape.browser.browser.parseReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun digestParsesSha256Prefix() {
        assertEquals(
            "a".repeat(64),
            UpdateChecker.parseAssetDigest("sha256:" + "a".repeat(64))
        )
    }

    @Test
    fun digestNormalisesCase() {
        assertEquals(
            "ab".repeat(32),
            UpdateChecker.parseAssetDigest("sha256:" + "AB".repeat(32))
        )
    }

    @Test
    fun notesParseSectionsAndBullets() {
        val sections = parseReleaseNotes(
            "## Bug fixes\n" +
                "- Fixed the save dialog overflow\n" +
                "- FPS no longer reads 0 while paused\n" +
                "\n## Maintenance\n" +
                "- Bumped the core pins\n"
        )
        assertEquals(2, sections.size)
        assertEquals("Bug fixes", sections[0].title)
        assertEquals(2, sections[0].items.size)
        assertEquals(
            "Fixed the save dialog overflow",
            sections[0].items[0]
        )
        assertEquals("Maintenance", sections[1].title)
        assertEquals(listOf("Bumped the core pins"), sections[1].items)
    }

    @Test
    fun notesFallbackKeepsPlainBodies() {
        val sections = parseReleaseNotes("Just a one-line note")
        assertEquals(1, sections.size)
        assertEquals("", sections[0].title)
        assertEquals(listOf("Just a one-line note"), sections[0].items)
        assertEquals(0, parseReleaseNotes("").size)
        assertEquals(0, parseReleaseNotes("   \n\n").size)
    }

    @Test
    fun semverReleaseBeatsItsPrereleases() {
        assertEquals(true, isNewerVersion("0.1.0", "0.1.0-beta.2"))
        assertEquals(false, isNewerVersion("0.1.0-beta.2", "0.1.0"))
        assertEquals(true, isNewerVersion("0.1.0-beta.2", "0.1.0-beta.1"))
        assertEquals(true, isNewerVersion("0.1.0-beta.1", "0.0.9999999"))
        assertEquals(false, isNewerVersion("0.1.0-beta.1", "0.1.0-beta.1"))
        assertEquals(false, isNewerVersion("0.0.9999999", "0.1.0-beta.1"))
    }

    @Test
    fun downloadUrlAllowlist() {
        assertTrue(
            UpdateChecker.isAllowedDownloadUrl(
                "https://github.com/DocBobert6/NotscapeMariner" +
                    "/releases/download/v1/app-release.apk"
            )
        )
        assertTrue(
            UpdateChecker.isAllowedDownloadUrl(
                "https://objects.githubusercontent.com/abc"
            )
        )
        assertFalse(
            UpdateChecker.isAllowedDownloadUrl(
                "http://github.com/evil.apk"
            )
        )
        assertFalse(
            UpdateChecker.isAllowedDownloadUrl(
                "https://evil.com/app-release.apk"
            )
        )
        assertFalse(
            UpdateChecker.isAllowedDownloadUrl("not a url")
        )
    }

    @Test
    fun digestRejectsGarbage() {
        assertNull(UpdateChecker.parseAssetDigest(null))
        assertNull(UpdateChecker.parseAssetDigest(""))
        assertNull(UpdateChecker.parseAssetDigest("sha256:xyz"))
        assertNull(UpdateChecker.parseAssetDigest("deadbeef"))
        assertNull(UpdateChecker.parseAssetDigest("sha256:" + "g".repeat(64)))
    }
}
