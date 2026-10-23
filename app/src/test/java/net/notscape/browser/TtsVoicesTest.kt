package net.notscape.browser

import net.notscape.browser.tts.TtsVoices
import org.junit.Assert.assertEquals
import org.junit.Test

/** Read-aloud voice/speed configuration (0.0.968). */
class TtsVoicesTest {

    @Test
    fun kokoroEnglishVoicesInPackOrder() {
        // Speaker ids 0-27 of kokoro-multi-lang-v1_0; id 3 is
        // af_heart, the default since the 0.0.964 spike.
        assertEquals(28, TtsVoices.kokoro.size)
        assertEquals((0..27).toList(), TtsVoices.kokoro.map { it.sid })
        assertEquals("Heart", TtsVoices.kokoro[3].name)
        assertEquals(3, TtsVoices.defaultSid("kokoro"))
    }

    @Test
    fun piperHasTheSingleAmyVoice() {
        assertEquals(listOf(0), TtsVoices.piper.map { it.sid })
        assertEquals("Amy", TtsVoices.piper[0].name)
        assertEquals(0, TtsVoices.defaultSid("piper"))
    }

    @Test
    fun speedClampsToSpeakableRange() {
        assertEquals(0.5f, TtsVoices.clampSpeed(0.1f), 0.001f)
        assertEquals(2.0f, TtsVoices.clampSpeed(3.0f), 0.001f)
        assertEquals(1.25f, TtsVoices.clampSpeed(1.25f), 0.001f)
    }

    @Test
    fun sidClampsToPackAndLoadedEngine() {
        assertEquals(27, TtsVoices.clampSid("kokoro", 99, 0))
        assertEquals(9, TtsVoices.clampSid("kokoro", 99, 10))
        assertEquals(0, TtsVoices.clampSid("piper", 5, 0))
        assertEquals(0, TtsVoices.clampSid("kokoro", -2, 0))
    }
}
