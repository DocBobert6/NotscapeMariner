package net.notscape.browser.emu

/** PS1 multi-disc helpers (N3): pure, JVM-safe. */
object DiscSwap {
    /** Picker label: the core's label when non-blank,
     * else "Disc N" (1-based). The core may return an
     * empty label, and the ext label callback is
     * optional, so the fallback is load-bearing. */
    fun displayLabel(index: Int, label: String?): String {
        val clean = label?.trim()
        return if (clean.isNullOrEmpty()) "Disc ${index + 1}" else clean
    }
}
