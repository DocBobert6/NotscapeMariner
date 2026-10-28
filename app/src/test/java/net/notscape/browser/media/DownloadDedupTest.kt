package net.notscape.browser.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** Filename de-duplication behind the no-overwrite rule:
 *  a colliding download name gets a " (n)" suffix, never
 *  silently replacing the earlier file. */
class DownloadDedupTest {

    @Test
    fun noCollisionReturnsNameUnchanged() {
        assertEquals(
            "photo.jpg",
            Downloads.dedupFileName({ false }, "photo.jpg")
        )
    }

    @Test
    fun singleCollisionSuffixesOneBeforeExtension() {
        val existing = setOf("photo.jpg")
        assertEquals(
            "photo (1).jpg",
            Downloads.dedupFileName({ it in existing }, "photo.jpg")
        )
    }

    @Test
    fun multipleCollisionsIncrement() {
        val existing = setOf("photo.jpg", "photo (1).jpg", "photo (2).jpg")
        assertEquals(
            "photo (3).jpg",
            Downloads.dedupFileName({ it in existing }, "photo.jpg")
        )
    }

    @Test
    fun extensionlessNameSuffixesAtEnd() {
        val existing = setOf("README")
        assertEquals(
            "README (1)",
            Downloads.dedupFileName({ it in existing }, "README")
        )
    }

    @Test
    fun multiDotNameKeepsOnlyLastExtension() {
        val existing = setOf("archive.tar.gz")
        assertEquals(
            "archive.tar (1).gz",
            Downloads.dedupFileName({ it in existing }, "archive.tar.gz")
        )
    }
}
