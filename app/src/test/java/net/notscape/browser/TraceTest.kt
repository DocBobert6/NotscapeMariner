package net.notscape.browser

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceTest {

    @After
    fun resetTrace() {
        Trace.stop()
    }

    @Test
    fun sanitizeStripsQueryAndFragment() {
        assertEquals(
            "https://example.com/path/page",
            Trace.sanitizeUrl("https://example.com/path/page?token=abc&x=1#section")
        )
        assertEquals(
            "https://example.com/path",
            Trace.sanitizeUrl("https://example.com/path?token=abc")
        )
        assertEquals(
            "https://example.com/path",
            Trace.sanitizeUrl("https://example.com/path#section")
        )
    }

    @Test
    fun sanitizeKeepsCleanUrlsAndHandlesBlanks() {
        assertEquals(
            "https://example.com/path/page",
            Trace.sanitizeUrl("https://example.com/path/page")
        )
        assertEquals("about:blank", Trace.sanitizeUrl("about:blank"))
        assertEquals("", Trace.sanitizeUrl(null))
        assertEquals("", Trace.sanitizeUrl("   "))
    }

    @Test
    fun sanitizeStripsUserinfo() {
        assertEquals(
            "https://example.com/path",
            Trace.sanitizeUrl("https://user:secret@example.com/path")
        )
    }

    @Test
    fun formatLineIsTimestampPlusMessage() {
        val line = Trace.formatLine("tab opened: 3")
        assertTrue(
            line.matches(Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} tab opened: 3$"))
        )
    }

    @Test
    fun startEventStopWritesLinesInOrder() {
        val dir = Files.createTempDirectory("trace-test").toFile()
        Trace.init(dir)
        Trace.start()
        Trace.event("menu: Share page")
        Trace.stop()
        val lines = File(dir, "trace.txt").readLines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith("trace started"))
        assertTrue(lines[1].endsWith("menu: Share page"))
        assertTrue(lines[2].endsWith("trace stopped"))
        assertTrue(Trace.hasContent())
        assertEquals(3, Trace.eventCount.value)
    }

    @Test
    fun eventBeforeStartWritesNothing() {
        val dir = Files.createTempDirectory("trace-test-idle").toFile()
        Trace.init(dir)
        Trace.event("menu: Share page")
        assertFalse(Trace.hasContent())
    }

    @Test
    fun newStartClearsPreviousTrace() {
        val dir = Files.createTempDirectory("trace-test-clear").toFile()
        Trace.init(dir)
        Trace.start()
        Trace.event("menu: Share page")
        Trace.stop()
        Trace.start()
        val lines = File(dir, "trace.txt").readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].endsWith("trace started"))
        Trace.stop()
    }
}
