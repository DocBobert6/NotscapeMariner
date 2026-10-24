package net.notscape.browser

import net.notscape.browser.browser.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun digestRejectsGarbage() {
        assertNull(UpdateChecker.parseAssetDigest(null))
        assertNull(UpdateChecker.parseAssetDigest(""))
        assertNull(UpdateChecker.parseAssetDigest("sha256:xyz"))
        assertNull(UpdateChecker.parseAssetDigest("deadbeef"))
        assertNull(UpdateChecker.parseAssetDigest("sha256:" + "g".repeat(64)))
    }
}
