package net.notscape.browser

import net.notscape.browser.vault.VaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta.68 #4: the form-fill AAD binding. The Keystore and
 * android.util.Base64 cannot run on the JVM, so the GCM
 * round-trip itself belongs to the device — like the password
 * records' crypto, which has no JVM test either. What the JVM
 * can pin down is the pure seam around it: the AAD string
 * binds kind + id, and the v3 record parser (shared by the
 * password and form-fill decrypt paths) splits key version
 * from body and refuses anything that is not a v3 record.
 */
class VaultCryptoAadTest {

    @Test fun aadBindsKindAndId() {
        assertEquals("card|42", VaultCrypto.aadForKind("card", 42))
        assertEquals("address|7", VaultCrypto.aadForKind("address", 7))
        assertTrue(VaultCrypto.aadForKind("card", 1) != VaultCrypto.aadForKind("address", 1))
        assertTrue(VaultCrypto.aadForKind("card", 1) != VaultCrypto.aadForKind("card", 2))
    }

    @Test fun v3RecordParsesKeyVersionAndBody() {
        assertEquals("2" to "aGVsbG8=:Ym9keQ==", VaultCrypto.parseV3("v3:2:aGVsbG8=:Ym9keQ=="))
        assertEquals("1" to "eHl6", VaultCrypto.parseV3("v3:1:eHl6"))
    }

    @Test fun nonV3RecordsAreRefused() {
        assertNull(VaultCrypto.parseV3("2:AAAA"))
        assertNull(VaultCrypto.parseV3("AAAA"))
        assertNull(VaultCrypto.parseV3(""))
        assertNull(VaultCrypto.parseV3("v3:"))
        assertNull(VaultCrypto.parseV3("v3:2:"))
        assertNull(VaultCrypto.parseV3("v3:2"))
    }

    @Test fun decryptForKindRefusesNonV3Records() {
        // A legacy password record must never decrypt as a
        // form-fill record, even before any key is consulted.
        try {
            VaultCrypto.decryptForKind("2:AAAA", "card", 1)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("form-fill"))
        }
    }
}
