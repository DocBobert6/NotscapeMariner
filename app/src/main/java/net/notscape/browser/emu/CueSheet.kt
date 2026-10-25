package net.notscape.browser.emu

/**
 * Disc track lists (beta.17): a .cue or .m3u names the
 * disc image files a game needs, and the importer must
 * copy those files beside the track list or the core
 * loads a list pointing at nothing. A .cue is a list
 * of FILE lines ("FILE \"game.bin\" BINARY", quoted
 * or bare, possibly several for multi-bin rips); an
 * .m3u is one file name per line, # lines comments.
 * Only bare names come back: a track list may name a
 * file in a subfolder, but the importer stores every
 * sibling beside the track list, so the bare name is
 * what the core resolves against.
 */
object CueSheet {
    /** Bare file names from a cue sheet's FILE lines. */
    fun cueFiles(cueText: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in cueText.lines()) {
            val line = raw.trim()
            if (!line.startsWith("FILE", ignoreCase = true)) continue
            if (line.length > 4 && !line[4].isWhitespace()) continue
            val rest = line.substring(4).trim()
            if (rest.isEmpty()) continue
            val name = when {
                rest.startsWith("\"") ->
                    rest.substring(1).substringBefore("\"")
                rest.startsWith("'") ->
                    rest.substring(1).substringBefore("'")
                else -> rest.split(' ', '\t').first()
            }
            val bare = name.replace(0x5C.toChar(), '/')
                .substringAfterLast('/')
            if (bare.isNotBlank()) out.add(bare)
        }
        return out
    }

    /** Bare file names from an .m3u playlist. */
    fun m3uFiles(m3uText: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in m3uText.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val bare = line.replace(0x5C.toChar(), '/')
                .substringAfterLast('/')
            if (bare.isNotBlank()) out.add(bare)
        }
        return out
    }

    /**
     * A zip/folder entry matches a reference when the
     * bare names agree ignoring case: rips often
     * differ in case between the track list and the
     * file on disk, and the filesystem here is
     * case-sensitive, so the importer stores the
     * sibling under the reference's exact spelling.
     */
    fun matches(reference: String, candidateBareName: String): Boolean =
        reference.equals(candidateBareName, ignoreCase = true)
}
