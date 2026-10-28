package net.notscape.browser

import net.notscape.browser.emu.CoreDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase M2: only the JVM-safe helpers are tested here
 * (SHA-256, console -> core mapping). Manifest parsing
 * uses org.json, which is an Android stub under local
 * unit tests, so it is verified live against the real
 * cores.json instead. */
class CoreDownloadTest {
    @Test
    fun sha256KnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CoreDownload.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun coreKeyMapping() {
        assertEquals("mgba", CoreDownload.coreKeyFor("gb"))
        assertEquals("mgba", CoreDownload.coreKeyFor("gbc"))
        assertEquals("mgba", CoreDownload.coreKeyFor("gba"))
        assertEquals("fceumm", CoreDownload.coreKeyFor("nes"))
        assertEquals("snes9x", CoreDownload.coreKeyFor("snes"))
        assertEquals(
            "genesis_plus_gx", CoreDownload.coreKeyFor("genesis")
        )
        assertEquals("genesis_plus_gx", CoreDownload.coreKeyFor("sms"))
        assertEquals("genesis_plus_gx", CoreDownload.coreKeyFor("gg"))
        assertEquals("swanstation", CoreDownload.coreKeyFor("ps1"))
        assertEquals("melonds", CoreDownload.coreKeyFor("ds"))
        assertEquals(
            "genesis_plus_gx", CoreDownload.coreKeyFor("segacd")
        )
        assertEquals("picodrive", CoreDownload.coreKeyFor("32x"))
        assertNull(CoreDownload.coreKeyFor(""))
    }

    @Test
    fun allKeysCoversEveryConsole() {
        val consoles = listOf(
            "gb", "gbc", "gba", "nes", "snes",
            "genesis", "sms", "gg", "ps1", "ds",
            "segacd", "32x"
        )
        for (system in consoles) {
            val key = CoreDownload.coreKeyFor(system)
            assertEquals(
                true,
                key != null && CoreDownload.allKeys.contains(key)
            )
        }
        assertEquals(7, CoreDownload.allKeys.size)
    }

    // S-M1: the manifest signature check. The app-side
    // verifier uses only JCA plus a hand-rolled Base64
    // decoder (no org.json, no Android stubs), so these
    // run on the JVM against a generated keypair.

    private fun testKeyPair(): java.security.KeyPair {
        val gen = java.security.KeyPairGenerator.getInstance("EC")
        gen.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    private fun testSign(
        key: java.security.PrivateKey,
        bytes: ByteArray
    ): ByteArray {
        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(key)
        signer.update(bytes)
        return signer.sign()
    }

    private fun testPubB64(key: java.security.PublicKey): String =
        java.util.Base64.getEncoder().encodeToString(key.encoded)

    @Test
    fun validSignatureVerifies() {
        val pair = testKeyPair()
        val manifest = "{\"cores\":[]}".toByteArray()
        assertTrue(
            CoreDownload.verifyManifestSignature(
                manifest,
                testSign(pair.private, manifest),
                testPubB64(pair.public)
            )
        )
    }

    @Test
    fun tamperedManifestFails() {
        val pair = testKeyPair()
        val manifest = "{\"cores\":[]}".toByteArray()
        val sig = testSign(pair.private, manifest)
        val tampered = manifest.copyOf().also { it[2] = 'x'.code.toByte() }
        assertFalse(
            CoreDownload.verifyManifestSignature(
                tampered, sig, testPubB64(pair.public)
            )
        )
    }

    @Test
    fun wrongKeyFails() {
        val signer = testKeyPair()
        val other = testKeyPair()
        val manifest = "{\"cores\":[]}".toByteArray()
        assertFalse(
            CoreDownload.verifyManifestSignature(
                manifest,
                testSign(signer.private, manifest),
                testPubB64(other.public)
            )
        )
    }

    @Test
    fun malformedSignatureFails() {
        val pair = testKeyPair()
        assertFalse(
            CoreDownload.verifyManifestSignature(
                "{\"cores\":[]}".toByteArray(),
                byteArrayOf(1, 2, 3, 4),
                testPubB64(pair.public)
            )
        )
        assertFalse(
            CoreDownload.verifyManifestSignature(
                "{\"cores\":[]}".toByteArray(),
                byteArrayOf(),
                testPubB64(pair.public)
            )
        )
        assertFalse(
            CoreDownload.verifyManifestSignature(
                "{\"cores\":[]}".toByteArray(),
                testSign(pair.private, "{\"cores\":[]}".toByteArray()),
                "not-base64!!!"
            )
        )
    }

    @Test
    fun base64DecodeMatchesJavaEncoder() {
        for (len in 0..20) {
            val bytes = ByteArray(len) { (it * 37 + 11).toByte() }
            val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
            assertEquals(
                bytes.toList(),
                CoreDownload.base64Decode(encoded).toList()
            )
        }
        // The pinned key decodes to a 91-byte X.509
        // SubjectPublicKeyInfo for P-256.
        assertEquals(
            91,
            CoreDownload.base64Decode(
                CoreDownload.MANIFEST_PUBLIC_KEY_BASE64
            ).size
        )
    }
}
