package net.notscape.browser.emu

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * PS1 memory-card file handling (beta.17 import flow):
 * a card is exactly 128 KB. [readCapped] never
 * allocates more than SIZE + 1 bytes, so picking a
 * huge file cannot OOM the app before the size
 * check rejects it.
 */
object MemoryCard {

    const val SIZE_BYTES = 128 * 1024

    /** Read at most SIZE + 1 bytes; the caller checks the size. */
    fun readCapped(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total <= SIZE_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            val take = minOf(n, SIZE_BYTES + 1 - total)
            out.write(buf, 0, take)
            total += take
        }
        return out.toByteArray()
    }

    fun isValidSize(bytes: ByteArray): Boolean =
        bytes.size == SIZE_BYTES
}
