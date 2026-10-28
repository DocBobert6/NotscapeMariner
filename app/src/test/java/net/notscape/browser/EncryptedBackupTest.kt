package net.notscape.browser

import net.notscape.browser.data.EncryptedBackup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Envelope detection for the encrypted backup (0.0.9993).
 * The encrypt/decrypt round-trip itself uses android.util
 * .Base64 and so belongs to the device, like the vault
 * export's; what the JVM can pin down is that detection
 * never mistakes a plain backup - or garbage - for an
 * encrypted one.
 */
class EncryptedBackupTest {

    @Test
    fun detectsEnvelope() {
        val env = """{"app":"notscape-backup","v":1,""" +
            """"iter":600000,"salt":"AA==","iv":"AA==",""" +
            """"data":"AA=="}"""
        assertTrue(EncryptedBackup.isEncrypted(env))
    }

    @Test
    fun plainBackupIsNotEncrypted() {
        val plain = """{"bookmarks":[],"history":[],"playlists":[]}"""
        assertFalse(EncryptedBackup.isEncrypted(plain))
    }

    @Test
    fun vaultExportIsNotBackupEnvelope() {
        val vault = """{"app":"notscape-vault","v":2,""" +
            """"iter":600000,"salt":"AA==","iv":"AA==",""" +
            """"data":"AA=="}"""
        assertFalse(EncryptedBackup.isEncrypted(vault))
    }

    @Test
    fun garbageIsNotEncrypted() {
        assertFalse(EncryptedBackup.isEncrypted("not json at all"))
        assertFalse(EncryptedBackup.isEncrypted(""))
    }

    @Test
    fun iterationBoundsAreEnforced() {
        // In range: the counts this app ever wrote.
        EncryptedBackup.checkIterations(1_000)
        EncryptedBackup.checkIterations(120_000)
        EncryptedBackup.checkIterations(600_000)
    }

    @Test
    fun iterationOutOfRangeThrows() {
        for (bad in listOf(0, 999, 600_001, 10_000_000)) {
            try {
                EncryptedBackup.checkIterations(bad)
                fail("expected IllegalArgumentException for $bad")
            } catch (e: IllegalArgumentException) {
                // Expected: a crafted file must not dictate
                // an unbounded PBKDF2 count.
            }
        }
    }
}
