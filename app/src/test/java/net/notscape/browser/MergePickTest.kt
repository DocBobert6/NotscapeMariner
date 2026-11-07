package net.notscape.browser

import net.notscape.browser.media.MergePick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MergePickTest {
    @Test
    fun audioKindDefaultsToAudio() {
        assertEquals(MergePick.Role.AUDIO, MergePick.defaultRole("audio"))
    }

    @Test
    fun videoAndOtherKindsDefaultToVideo() {
        assertEquals(MergePick.Role.VIDEO, MergePick.defaultRole("video"))
        assertEquals(MergePick.Role.VIDEO, MergePick.defaultRole("page"))
        assertEquals(MergePick.Role.VIDEO, MergePick.defaultRole(""))
    }

    @Test
    fun mixedRolesAreValid() {
        assertTrue(
            MergePick.rolesValid(MergePick.Role.VIDEO, MergePick.Role.AUDIO)
        )
        assertTrue(
            MergePick.rolesValid(MergePick.Role.AUDIO, MergePick.Role.VIDEO)
        )
    }

    @Test
    fun sameRolesAreInvalid() {
        assertFalse(
            MergePick.rolesValid(MergePick.Role.VIDEO, MergePick.Role.VIDEO)
        )
        assertFalse(
            MergePick.rolesValid(MergePick.Role.AUDIO, MergePick.Role.AUDIO)
        )
    }
}
