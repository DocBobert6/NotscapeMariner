package net.notscape.browser

import net.notscape.browser.tts.chunkForSpeech
import net.notscape.browser.tts.chunkParagraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The read-aloud sentence chunker (0.0.964). */
class TtsChunkTest {

    @Test
    fun paragraphBreakEndsAChunk() {
        val out = chunkForSpeech(listOf("First para.", "Second para."))
        assertEquals(listOf("First para.", "Second para."), out)
    }

    @Test
    fun sentencesPackUpToTheLimit() {
        val s = "This is one fairly short sentence."
        val out = chunkForSpeech(listOf(List(20) { s }.joinToString(" ")), maxChars = 100)
        assertTrue(out.size > 1)
        assertTrue(out.all { it.length <= 100 })
        assertEquals(20, out.joinToString(" ").split(s).size - 1)
    }

    @Test
    fun overLongSentenceIsHardSplit() {
        val long = ("word ".repeat(120)).trim() // 599 chars, no terminator
        val out = chunkForSpeech(listOf(long), maxChars = 100)
        assertTrue(out.size >= 6)
        assertTrue(out.all { it.length <= 100 })
    }

    @Test
    fun blankParagraphsVanish() {
        assertEquals(listOf("Real."), chunkForSpeech(listOf("", "  ", "Real.")))
        assertTrue(chunkForSpeech(emptyList()).isEmpty())
    }

    @Test
    fun defaultChunksStaySentenceSized() {
        // The 0.0.99994 default cap: chunks stay small
        // enough that generation keeps ahead of playback.
        val long = ("word ".repeat(120)).trim()
        val out = chunkForSpeech(listOf(long))
        assertTrue(out.size >= 4)
        assertTrue(out.all { it.length <= 150 })
    }

    @Test
    fun chunkParagraphsMapsEveryChunkToItsParagraph() {
        val long = ("word ".repeat(120)).trim()
        val (chunks, map) = chunkParagraphs(
            listOf("Short one.", long, "", "Last one.")
        )
        assertEquals(chunks.size, map.size)
        assertEquals(0, map.first())
        assertEquals(3, map.last())
        // The long paragraph (index 1) owns a run of chunks.
        assertTrue(map.count { it == 1 } >= 2)
        assertTrue(map.zipWithNext().all { (a, b) -> a <= b })
        // The chunk sequence is the whole-list chunking,
        // paragraph for paragraph.
        assertEquals(
            chunkForSpeech(listOf("Short one.", long, "Last one.")),
            chunks
        )
    }

    @Test
    fun chunkParagraphsHonoursStartAt() {
        val (chunks, map) = chunkParagraphs(
            listOf("One.", "Two.", "Three."), startAt = 2
        )
        assertEquals(listOf("Three."), chunks)
        assertEquals(listOf(2), map)
    }
}
