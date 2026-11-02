package net.notscape.browser

import net.notscape.browser.data.StorageInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageInfoTest {

    @Test
    fun formatSizeScales() {
        assertEquals("0 B", StorageInfo.formatSize(0))
        assertEquals("512 B", StorageInfo.formatSize(512))
        assertEquals("2.0 KB", StorageInfo.formatSize(2048))
        assertEquals("5.0 MB", StorageInfo.formatSize(5L * 1024 * 1024))
        assertEquals(
            "2.50 GB",
            StorageInfo.formatSize(2684354560L)
        )
    }

    @Test
    fun dirSizeCountsNestedFiles() {
        val root = Files.createTempDirectory("storage-test").toFile()
        try {
            File(root, "a.bin").writeBytes(ByteArray(100))
            val sub = File(root, "sub").apply { mkdirs() }
            File(sub, "b.bin").writeBytes(ByteArray(50))
            assertEquals(150L, StorageInfo.dirSize(root))
            assertEquals(0L, StorageInfo.dirSize(File(root, "missing")))
        } finally {
            root.deleteRecursively()
        }
    }
}
