package net.notscape.browser

import net.notscape.browser.data.PrivateDownloadsGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta.64 #20: the private-downloads confirm gate. */
class PrivateDownloadsGateTest {

    @Test
    fun gateStartsLockedAndOnlyConfirmUnlocks() {
        val gate = PrivateDownloadsGate()
        assertFalse(gate.unlocked)
        gate.confirm()
        assertTrue(gate.unlocked)
        gate.hide()
        assertFalse(gate.unlocked)
    }
}
