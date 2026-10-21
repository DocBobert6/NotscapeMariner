package net.notscape.browser

import net.notscape.browser.reader.BookParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure parts of the book parsers. */
class BookParserTest {

    @Test
    fun palmDocLiteralsPassThrough() {
        val data = "Hello".toByteArray(Charsets.US_ASCII)
        assertEquals("Hello", String(BookParser.palmDocDecompress(data), Charsets.US_ASCII))
    }

    @Test
    fun palmDocLiteralRunBlock() {
        // A count byte (3) followed by three literal bytes.
        val data = byteArrayOf(3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertEquals("abc", String(BookParser.palmDocDecompress(data), Charsets.US_ASCII))
    }

    @Test
    fun palmDocSpaceEscape() {
        // 0xC1 encodes a space followed by 'A'.
        val data = byteArrayOf(0xC1.toByte())
        assertEquals(" A", String(BookParser.palmDocDecompress(data), Charsets.US_ASCII))
    }

    @Test
    fun palmDocBackReferenceRepeats() {
        // 'a' then a copy pair: distance 1, length 3 -> "aaaa".
        val data = byteArrayOf('a'.code.toByte(), 0x80.toByte(), 0x08.toByte())
        assertEquals("aaaa", String(BookParser.palmDocDecompress(data), Charsets.US_ASCII))
    }

    @Test
    fun palmDocBackReferenceCopiesPhrase() {
        // "abc" then copy: distance 3, length 3 -> "abcabc".
        // pair value = (3 shl 3) or 0 = 24 -> bytes 0x80, 0x18.
        val data = byteArrayOf(
            'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(),
            0x80.toByte(), 0x18.toByte()
        )
        assertEquals("abcabc", String(BookParser.palmDocDecompress(data), Charsets.US_ASCII))
    }

    @Test
    fun palmDocLongOutputRoundTrips() {
        // 500 literal 'x' bytes via literal-run blocks of 8.
        val out = StringBuilder()
        val data = ArrayList<Byte>()
        repeat(62) {
            data.add(8)
            repeat(8) { data.add('x'.code.toByte()) }
            out.append("xxxxxxxx")
        }
        data.add(4)
        repeat(4) { data.add('x'.code.toByte()) }
        out.append("xxxx")
        val bytes = ByteArray(data.size) { data[it] }
        assertEquals(
            out.toString(),
            String(BookParser.palmDocDecompress(bytes), Charsets.US_ASCII)
        )
    }

    @Test
    fun utf8TextDecodesAsUtf8() {
        val text = "Café — naïve"
        assertEquals(text, BookParser.decodeText(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun windows1252FallsBackForSmartQuotes() {
        // 0x93/0x94 are windows-1252 smart quotes and invalid UTF-8.
        val bytes = byteArrayOf(0x93.toByte(), 'h'.code.toByte(), 'i'.code.toByte(), 0x94.toByte())
        assertEquals("“hi”", BookParser.decodeText(bytes))
    }

    @Test
    fun utf8BomIsStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Title".toByteArray(Charsets.UTF_8)
        assertEquals("Title", BookParser.decodeText(bytes))
    }
}
