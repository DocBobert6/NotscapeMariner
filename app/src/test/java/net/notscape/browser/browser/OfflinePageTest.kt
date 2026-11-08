package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflinePageTest {
    private val offlineDir: File =
        Files.createTempDirectory("offline").toFile()

    @Test fun fileUrlIsJustThePath() {
        assertEquals(
            "file:///a/b/page.mht",
            offlineFileUrl("/a/b/page.mht")
        )
    }

    @Test fun mhtInsideTheOfflineDirCounts() {
        val file = File(offlineDir, "123.mht")
        assertTrue(isOfflineFilePath(file.path, offlineDir))
    }

    @Test fun nonMhtIsRejected() {
        val file = File(offlineDir, "page.html")
        assertFalse(isOfflineFilePath(file.path, offlineDir))
    }

    @Test fun fileOutsideTheDirIsRejected() {
        val elsewhere = File(offlineDir.parentFile, "other.mht")
        assertFalse(isOfflineFilePath(elsewhere.path, offlineDir))
    }

    @Test fun dotDotEscapeIsRejected() {
        val escape = offlineDir.path + "/../secret.mht"
        assertFalse(isOfflineFilePath(escape, offlineDir))
    }
}
