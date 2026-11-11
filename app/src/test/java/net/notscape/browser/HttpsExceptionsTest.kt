package net.notscape.browser

import net.notscape.browser.data.isHttpsException
import net.notscape.browser.data.parseHttpsExceptions
import net.notscape.browser.data.withHttpsException
import net.notscape.browser.data.withoutHttpsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta.63 #24: per-site HTTPS-only exceptions are a comma-separated
 * host list in the settings, like the vault's never-save list.
 * The list math is pure and pinned here; the store methods are
 * thin wrappers over it.
 */
class HttpsExceptionsTest {

    @Test
    fun parseSkipsBlanksAndLowercases() {
        assertEquals(
            setOf("example.com", "plain.example"),
            parseHttpsExceptions("Example.COM,, plain.example ,")
        )
    }

    @Test
    fun parseEmptyIsEmpty() {
        assertTrue(parseHttpsExceptions("").isEmpty())
    }

    @Test
    fun withAddsSortedAndDedupes() {
        assertEquals(
            "a.example,b.example",
            withHttpsException("b.example", "A.example")
        )
        assertEquals(
            "b.example",
            withHttpsException("b.example", "b.example")
        )
    }

    @Test
    fun withoutRemovesCaseInsensitively() {
        assertEquals(
            "a.example",
            withoutHttpsException("a.example,b.example", "B.EXAMPLE")
        )
        assertEquals(
            "a.example,b.example",
            withoutHttpsException("a.example,b.example", "c.example")
        )
    }

    @Test
    fun isExceptionMatchesCaseInsensitively() {
        assertTrue(isHttpsException("plain.example", "PLAIN.example"))
        assertFalse(isHttpsException("plain.example", "other.example"))
    }
}
