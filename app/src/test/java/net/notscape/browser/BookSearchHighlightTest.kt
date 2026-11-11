package net.notscape.browser

import net.notscape.browser.reader.highlightOccurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchHighlightTest {

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(highlightOccurrences("some text", "").isEmpty())
        assertTrue(highlightOccurrences("some text", "   ").isEmpty())
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertEquals(listOf(0..2), highlightOccurrences("The cat sat", "the"))
        assertEquals(listOf(4..6), highlightOccurrences("The cat sat", "CAT"))
    }

    @Test
    fun multipleMatchesInOrder() {
        assertEquals(
            listOf(0..0, 2..2, 4..4),
            highlightOccurrences("a a a", "a")
        )
    }

    @Test
    fun scanResumesPastEachMatch() {
        // "aa" in "aaaa" finds 0..1 and 2..3 — never overlapping.
        assertEquals(listOf(0..1, 2..3), highlightOccurrences("aaaa", "aa"))
    }

    @Test
    fun noMatchIsEmpty() {
        assertTrue(highlightOccurrences("hello", "z").isEmpty())
        assertTrue(highlightOccurrences("hi", "hello").isEmpty())
    }
}
