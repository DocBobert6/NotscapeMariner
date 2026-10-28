package net.notscape.browser

import net.notscape.browser.media.shouldAskDownloadNotif
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotifAskTest {

    @Test
    fun asksOnceOnAndroid13PlusWhenMissingAndUnasked() {
        assertTrue(shouldAskDownloadNotif(33, granted = false, alreadyAsked = false))
        assertTrue(shouldAskDownloadNotif(36, granted = false, alreadyAsked = false))
    }

    @Test
    fun neverAsksBeforeAndroid13() {
        assertFalse(shouldAskDownloadNotif(32, granted = false, alreadyAsked = false))
    }

    @Test
    fun neverAsksWhenGranted() {
        assertFalse(shouldAskDownloadNotif(36, granted = true, alreadyAsked = false))
    }

    @Test
    fun neverAsksTwice() {
        assertFalse(shouldAskDownloadNotif(36, granted = false, alreadyAsked = true))
    }
}
