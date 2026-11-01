package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Thumbnail sizing for tab-grid previews: fit a tab's viewport into
 * a max-width box without changing its aspect ratio. */
class TabThumbnailTest {

    @Test
    fun phoneViewportScalesToMaxWidth() {
        assertEquals(320 to 711, thumbnailSizeFor(1080, 2400))
    }

    @Test
    fun wideViewportScalesHeightDown() {
        assertEquals(320 to 180, thumbnailSizeFor(1920, 1080))
    }

    @Test
    fun smallViewportKeepsNativeSize() {
        assertEquals(200 to 400, thumbnailSizeFor(200, 400))
    }

    @Test
    fun zeroViewportGivesEmptySize() {
        assertEquals(0 to 0, thumbnailSizeFor(0, 0))
    }

    @Test
    fun zeroMaxWidthGivesEmptySize() {
        assertEquals(0 to 0, thumbnailSizeFor(1080, 2400, 0))
    }
}
