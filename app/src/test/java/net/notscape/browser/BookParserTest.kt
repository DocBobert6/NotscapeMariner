package net.notscape.browser

import net.notscape.browser.reader.BookParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun epubWithFragmentAndEncodedHrefsImportsFully() {
        // Real-world EPUBs reference chapters as "Text/Chapter%201.xhtml"
        // and "Text/ch2.xhtml#start". Both used to be silently skipped,
        // producing "partial" imports.
        val zipFile = java.io.File.createTempFile("testbook", ".epub")
        try {
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { z ->
                fun entry(name: String, content: String) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(content.toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                }
                entry(
                    "META-INF/container.xml",
                    """<?xml version="1.0"?><container><rootfiles>""" +
                        """<rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                )
                entry(
                    "OEBPS/content.opf",
                    """<?xml version="1.0"?><package><metadata>""" +
                        """<dc:title>Test Book</dc:title><dc:creator>An Author</dc:creator></metadata>""" +
                        """<manifest>""" +
                        """<item id="c1" href="Text/Chapter%201.xhtml"/>""" +
                        """<item id="c2" href="Text/ch2.xhtml#start"/>""" +
                        """</manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
                )
                entry(
                    "OEBPS/Text/Chapter 1.xhtml",
                    "<html><head><title>One</title></head><body><p>First chapter text.</p></body></html>"
                )
                entry(
                    "OEBPS/Text/ch2.xhtml",
                    "<html><head><title>Two</title></head><body><p>Second chapter text.</p></body></html>"
                )
            }
            val parsed = BookParser.parse(zipFile)
            assertEquals("Test Book", parsed.title)
            assertEquals(2, parsed.chapters.size)
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun epubNavTocCoverAndInlineImages() {
        // Phase E parser: chapter titles from the EPUB 3 nav (which
        // wins over the NCX), cover from the metadata reference, and
        // inline images tokenised into the chapter text.
        val zipFile = java.io.File.createTempFile("navbook", ".epub")
        try {
            val coverBytes = byteArrayOf(1, 2, 3, 4)
            val picBytes = byteArrayOf(9, 8, 7)
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { z ->
                fun entry(name: String, content: String) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(content.toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                }
                fun entryBytes(name: String, content: ByteArray) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(content)
                    z.closeEntry()
                }
                entry(
                    "META-INF/container.xml",
                    """<?xml version="1.0"?><container><rootfiles>""" +
                        """<rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                )
                entry(
                    "OEBPS/content.opf",
                    """<?xml version="1.0"?><package><metadata>""" +
                        """<dc:title>Nav Book</dc:title><dc:creator>T. Oc</dc:creator>""" +
                        """<meta name="cover" content="coverimg"/></metadata>""" +
                        """<manifest>""" +
                        """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" +
                        """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""" +
                        """<item id="coverimg" href="Images/cover.png" media-type="image/png"/>""" +
                        """<item id="pic" href="Images/pic.png" media-type="image/png"/>""" +
                        """<item id="c1" href="Text/ch1.xhtml"/>""" +
                        """<item id="c2" href="Text/ch2.xhtml"/>""" +
                        """</manifest><spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
                )
                entry(
                    "OEBPS/nav.xhtml",
                    """<!DOCTYPE html><html><body><nav epub:type="toc"><ol>""" +
                        """<li><a href="Text/ch1.xhtml">The Beginning</a></li>""" +
                        """<li><a href="Text/ch2.xhtml">The Middle</a></li>""" +
                        """</ol></nav></body></html>"""
                )
                entry(
                    "OEBPS/toc.ncx",
                    """<?xml version="1.0"?><ncx><navMap>""" +
                        """<navPoint id="n1"><navLabel><text>NCX One</text></navLabel><content src="Text/ch1.xhtml"/></navPoint>""" +
                        """<navPoint id="n2"><navLabel><text>NCX Two</text></navLabel><content src="Text/ch2.xhtml"/></navPoint>""" +
                        """</navMap></ncx>"""
                )
                entry(
                    "OEBPS/Text/ch1.xhtml",
                    "<html><head><title>Ignored Title</title></head><body>" +
                        "<p>Alpha text.</p><p><img src=\"../Images/pic.png\" alt=\"dot\"/></p>" +
                        "<p>Beta text.</p></body></html>"
                )
                entry(
                    "OEBPS/Text/ch2.xhtml",
                    "<html><body><p>Second chapter body.</p></body></html>"
                )
                entryBytes("OEBPS/Images/cover.png", coverBytes)
                entryBytes("OEBPS/Images/pic.png", picBytes)
            }
            val parsed = BookParser.parse(zipFile)
            assertEquals("Nav Book", parsed.title)
            assertEquals("T. Oc", parsed.author)
            assertEquals(2, parsed.chapters.size)
            assertEquals("The Beginning", parsed.chapters[0].title)
            assertEquals("The Middle", parsed.chapters[1].title)
            assertTrue(parsed.cover?.contentEquals(coverBytes) == true)
            assertEquals(1, parsed.images.size)
            assertTrue(parsed.images.values.first().contentEquals(picBytes))
            assertTrue(parsed.chapters[0].text.contains("[[img:"))
            assertTrue(parsed.chapters[0].text.contains("Alpha text."))
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun utf8BomIsStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Title".toByteArray(Charsets.UTF_8)
        assertEquals("Title", BookParser.decodeText(bytes))
    }

    @Test
    fun epubLongChapterSplitsAtBlockBoundaries() {
        // One spine item holding a whole short book: the reader
        // must still get honest pages, split between paragraphs,
        // and the split must be deterministic.
        val zipFile = java.io.File.createTempFile("longbook", ".epub")
        try {
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { z ->
                fun entry(name: String, content: String) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(content.toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                }
                entry(
                    "META-INF/container.xml",
                    """<?xml version="1.0"?><container><rootfiles>""" +
                        """<rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                )
                entry(
                    "OEBPS/content.opf",
                    """<?xml version="1.0"?><package><metadata>""" +
                        """<dc:title>Long Book</dc:title><dc:creator>A. Uthor</dc:creator></metadata>""" +
                        """<manifest><item id="c1" href="Text/ch1.xhtml"/></manifest>""" +
                        """<spine><itemref idref="c1"/></spine></package>"""
                )
                val paras = (1..30).joinToString("") { n ->
                    "<p>" + "Sentence number $n of the play. ".repeat(12) + "</p>"
                }
                entry(
                    "OEBPS/Text/ch1.xhtml",
                    "<html><head><title>The Play</title></head><body>$paras</body></html>"
                )
            }
            val parsed = BookParser.parse(zipFile)
            assertTrue(parsed.chapters.size >= 3)
            assertEquals("The Play", parsed.chapters[0].title)
            assertEquals("The Play (2)", parsed.chapters[1].title)
            assertTrue(parsed.chapters.all { it.text.length <= 4200 })
            val again = BookParser.parse(zipFile)
            assertEquals(parsed.chapters.map { it.title }, again.chapters.map { it.title })
            assertEquals(parsed.chapters.map { it.text }, again.chapters.map { it.text })
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun epubCoverViaWrapperPageAndCoverPageReadsAsImageOnly() {
        // Gutenberg shape: the metadata cover reference points at
        // the wrapper page, not the image; the wrapper is also the
        // first spine item and must read as the image alone.
        val zipFile = java.io.File.createTempFile("wrapbook", ".epub")
        try {
            val pngBytes = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3
            )
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { z ->
                fun entry(name: String, content: String) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(content.toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                }
                fun entryBytes(name: String, content: ByteArray) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(content)
                    z.closeEntry()
                }
                entry(
                    "META-INF/container.xml",
                    """<?xml version="1.0"?><container><rootfiles>""" +
                        """<rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                )
                entry(
                    "OEBPS/content.opf",
                    """<?xml version="1.0"?><package><metadata>""" +
                        """<dc:title>Wrap Book</dc:title><dc:creator>W. Rapper</dc:creator>""" +
                        """<meta name="cover" content="coverpage-wrapper"/></metadata>""" +
                        """<manifest>""" +
                        """<item id="coverpage-wrapper" href="wrap0000.html" media-type="application/xhtml+xml"/>""" +
                        """<item id="coverimg" href="cover.png" media-type="image/png"/>""" +
                        """<item id="c1" href="Text/ch1.xhtml"/>""" +
                        """</manifest><spine><itemref idref="coverpage-wrapper"/>""" +
                        """<itemref idref="c1"/></spine></package>"""
                )
                entry(
                    "OEBPS/wrap0000.html",
                    "<html><head><title>\"Cover\"</title></head><body>" +
                        "<div><img src=\"cover.png\" alt=\"\"/></div></body></html>"
                )
                entry(
                    "OEBPS/Text/ch1.xhtml",
                    "<html><body><p>Real first chapter text.</p></body></html>"
                )
                entryBytes("OEBPS/cover.png", pngBytes)
            }
            val parsed = BookParser.parse(zipFile)
            assertTrue(parsed.cover?.contentEquals(pngBytes) == true)
            assertEquals(2, parsed.chapters.size)
            assertTrue(parsed.chapters[0].text.contains("[[img:"))
            assertFalse(parsed.chapters[0].text.contains("Cover"))
            assertTrue(parsed.chapters[1].text.contains("Real first chapter text."))
        } finally {
            zipFile.delete()
        }
    }
}
