package net.notscape.browser

import net.notscape.browser.tts.chunkForSpeech
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
}
