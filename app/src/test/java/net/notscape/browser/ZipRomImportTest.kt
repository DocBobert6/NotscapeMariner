package net.notscape.browser

import net.notscape.browser.emu.ZipRomImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Phase K, beta.7: zip ROM import entry rules. */
class ZipRomImportTest {

    private fun zipBytes(
        entries: List<Pair<String, ByteArray>>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun extractsMatchingRomsByBaseName() {
        val rom = "SMW".toByteArray()
        val found = mutableListOf<Triple<String, String, ByteArray>>()
        val result = ZipRomImport.forEachEntry(
            ByteArrayInputStream(
                zipBytes(
                    listOf(
                        "folder/Super Mario World.sfc" to rom,
                        "readme.txt" to "hi".toByteArray(),
                        "inner.zip" to "PK".toByteArray()
                    )
                )
            ),
            null
        ) { name, system, bytes ->
            found.add(Triple(name, system, bytes))
        }
        assertEquals(1, result.matched)
        assertEquals(1, found.size)
        // ZipRomImport hands callers the sanitized file name,
        // matching the single-file import path.
        assertEquals("Super_Mario_World.sfc", found[0].first)
        assertEquals("snes", found[0].second)
        assertTrue(found[0].third.contentEquals(rom))
    }

    @Test
    fun targetSystemFiltersAndReportsOthers() {
        val found = mutableListOf<String>()
        val result = ZipRomImport.forEachEntry(
            ByteArrayInputStream(
                zipBytes(
                    listOf(
                        "sonic.md" to ByteArray(4),
                        "zelda.sfc" to ByteArray(4)
                    )
                )
            ),
            "snes"
        ) { name, _, _ -> found.add(name) }
        assertEquals(1, result.matched)
        assertEquals(listOf("zelda.sfc"), found)
        assertEquals("genesis", result.firstOtherSystem)
    }

    @Test
    fun noMatchReportsNothing() {
        val result = ZipRomImport.forEachEntry(
            ByteArrayInputStream(
                zipBytes(listOf("notes.txt" to ByteArray(1)))
            ),
            "gb"
        ) { _, _, _ -> }
        assertEquals(0, result.matched)
        assertEquals(null, result.firstOtherSystem)
    }
}
