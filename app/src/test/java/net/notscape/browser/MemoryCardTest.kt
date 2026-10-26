package net.notscape.browser

import java.io.ByteArrayInputStream
import net.notscape.browser.emu.MemoryCard
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PS1 memory-card handling (Phase 4): a card is
 *  exactly 128 KB, and a huge picked file must never
 *  be read whole before the size check rejects it. */
class MemoryCardTest {

    @Test
    fun validSizeIsExactly128K() {
        assertTrue(MemoryCard.isValidSize(ByteArray(MemoryCard.SIZE_BYTES)))
        assertFalse(MemoryCard.isValidSize(ByteArray(MemoryCard.SIZE_BYTES - 1)))
        assertFalse(MemoryCard.isValidSize(ByteArray(MemoryCard.SIZE_BYTES + 1)))
        assertFalse(MemoryCard.isValidSize(ByteArray(0)))
    }

    @Test
    fun cappedReadKeepsSmallFilesWhole() {
        val data = ByteArray(100) { it.toByte() }
        assertArrayEquals(
            data,
            MemoryCard.readCapped(ByteArrayInputStream(data))
        )
    }

    @Test
    fun cappedReadStopsAtSizePlusOne() {
        val data = ByteArray(MemoryCard.SIZE_BYTES + 10_000) { 7 }
        val read = MemoryCard.readCapped(ByteArrayInputStream(data))
        assertEquals(MemoryCard.SIZE_BYTES + 1, read.size)
    }
}
