package net.notscape.browser.media

/** Media-merge role logic (pure, JVM-safe). */
object MergePick {
    enum class Role { VIDEO, AUDIO }

    /** Default role from the detector's kind: only
     * 'audio' defaults to audio; everything else
     * defaults to video. The user can flip either
     * side in the merge dialog — a "black screen with
     * sound" file is detected as video yet must be
     * usable as the audio side. */
    fun defaultRole(kind: String): Role =
        if (kind == "audio") Role.AUDIO else Role.VIDEO

    /** A merge needs exactly one video side and one
     * audio side. */
    fun rolesValid(a: Role, b: Role): Boolean = a != b
}
