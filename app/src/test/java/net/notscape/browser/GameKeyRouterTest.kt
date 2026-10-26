package net.notscape.browser

import android.view.KeyEvent
import net.notscape.browser.emu.GameKeyRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gamepad routing (Phase 4): keys and axes into the
 *  core's input mask, with the analogue-mode split. */
class GameKeyRouterTest {

    private class Rec {
        val bits = mutableMapOf<Int, Boolean>()
        var activity = 0
        var ticks = 0
        val analog = mutableListOf<Triple<Int, Float, Float>>()
        fun router() = GameKeyRouter(
            setBit = { bit, down -> bits[bit] = down },
            onActivity = { activity++ },
            onTick = { ticks++ }
        )
    }

    @Test
    fun knownKeySetsBitAndTicksOnDownOnly() {
        val rec = Rec()
        val router = rec.router()
        assertTrue(router.onKey(KeyEvent.KEYCODE_BUTTON_A, true))
        assertEquals(true, rec.bits[0])
        assertEquals(1, rec.activity)
        assertEquals(1, rec.ticks)
        assertTrue(router.onKey(KeyEvent.KEYCODE_BUTTON_A, false))
        assertEquals(false, rec.bits[0])
        assertEquals(2, rec.activity)
        assertEquals(1, rec.ticks)
    }

    @Test
    fun unknownKeyIsIgnored() {
        val rec = Rec()
        val router = rec.router()
        assertFalse(router.onKey(KeyEvent.KEYCODE_ENTER, true))
        assertTrue(rec.bits.isEmpty())
        assertEquals(0, rec.activity)
        assertEquals(0, rec.ticks)
    }

    @Test
    fun digitalStickMapsToDPadAndClearsOnCentre() {
        val rec = Rec()
        val router = rec.router()
        router.onAxes(0f, 0f, -1f, 0f)
        assertEquals(true, rec.bits[6])
        assertEquals(1, rec.ticks)
        router.onAxes(0f, 0f, 0f, 0f)
        assertEquals(false, rec.bits[6])
    }

    @Test
    fun analogueSticksReportWithoutBuzz() {
        val rec = Rec()
        val router = rec.router()
        router.analogMode = true
        router.onAnalog = { stick, x, y ->
            rec.analog.add(Triple(stick, x, y))
        }
        router.onAxes(0f, 0f, 0.5f, -0.25f, -0.75f, 0.5f)
        // No hat deflection: no D-pad bits, no tick — but the
        // stick move still counts as activity, and both
        // sticks are reported.
        assertTrue(rec.bits.values.none { it })
        assertEquals(0, rec.ticks)
        assertEquals(1, rec.activity)
        assertEquals(2, rec.analog.size)
        assertEquals(Triple(0, 0.5f, -0.25f), rec.analog[0])
        assertEquals(Triple(1, -0.75f, 0.5f), rec.analog[1])
    }

    @Test
    fun analogueHatStillDrivesDPad() {
        val rec = Rec()
        val router = rec.router()
        router.analogMode = true
        router.onAxes(-1f, 0f, 0f, 0f)
        assertEquals(true, rec.bits[6])
        assertEquals(1, rec.ticks)
        router.onAxes(0f, 0f, 0f, 0f)
        assertEquals(false, rec.bits[6])
    }
}
