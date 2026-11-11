package net.notscape.browser

import net.notscape.browser.data.AddressEntry
import net.notscape.browser.data.CardEntry
import net.notscape.browser.data.VaultEntry
import net.notscape.browser.vault.AddressData
import net.notscape.browser.vault.CardData
import net.notscape.browser.vault.VaultBackup
import net.notscape.browser.vault.VaultCollections
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64 as JavaBase64

/**
 * Beta.68 #4: passphrase vault export, now format v3. The
 * envelope's Base64 is injected (java.util.Base64 on the JVM;
 * android.util.Base64 in production, which needs no API 26)
 * and the Keystore encrypt/decrypt calls are injected too, so
 * the full round-trip runs on the JVM with a fake store.
 * The real Keystore path is device-verified, like the
 * password records' crypto.
 */
class VaultBackupTest {

    private class FakeVault : VaultCollections {
        override val vault = mutableListOf<VaultEntry>()
        override val addresses = mutableListOf<AddressEntry>()
        override val cards = mutableListOf<CardEntry>()
        override fun addVault(entry: VaultEntry) {
            vault.add(0, entry)
        }

        override fun addAddress(entry: AddressEntry) {
            addresses.add(0, entry)
        }

        override fun addCard(entry: CardEntry) {
            cards.add(0, entry)
        }
    }

    private val javaEncode: (ByteArray) -> String =
        { JavaBase64.getEncoder().encodeToString(it) }
    private val javaDecode: (String) -> ByteArray =
        { JavaBase64.getDecoder().decode(it) }

    private val home = AddressData(
        name = "Robert", street1 = "1 Main St", city = "Dry Ridge",
        postal = "41035", country = "USA"
    )
    private val mainCard = CardData(
        name = "Robert", number = "4111111111111111", expMonth = 9, expYear = 2028
    )

    private fun sourceVault(): FakeVault = FakeVault().also {
        it.addVault(VaultEntry(1, "example.com", "robert", "enc-pw-1"))
        it.addAddress(AddressEntry(2, "Home", "enc-addr-1"))
        it.addCard(CardEntry(3, "Main card", "1111", "Visa", "enc-card-1"))
    }

    private val decryptPw: (VaultEntry) -> String = { "hunter2" }
    private val decryptAddr: (AddressEntry) -> AddressData? = { home }
    private val decryptCard: (CardEntry) -> CardData? = { mainCard }
    private val encryptPw: (String, String, Long) -> String = { p, _, _ -> "enc:$p" }
    private val encryptAddr: (AddressData, Long) -> String =
        { d, _ -> "enc-addr:${d.street1}" }
    private val encryptCard: (CardData, Long) -> String =
        { d, _ -> "enc-card:${d.number}" }

    private fun importInto(
        target: FakeVault,
        file: String,
        passphrase: String
    ): Int = VaultBackup.import(
        target, file, passphrase, encryptPw, encryptAddr, encryptCard,
        decryptAddr, decryptCard, javaDecode
    )

    @Test fun payloadCarriesAllThreeKinds() {
        val payload = VaultBackup.buildPayload(
            sourceVault(), decryptPw, decryptAddr, decryptCard
        )
        assertEquals(1, payload.getJSONArray("passwords").length())
        val addr = payload.getJSONArray("addresses").getJSONObject(0)
        assertEquals("Home", addr.getString("label"))
        assertEquals("1 Main St", addr.getJSONObject("data").getString("street1"))
        val card = payload.getJSONArray("cards").getJSONObject(0)
        assertEquals("Main card", card.getString("label"))
        assertEquals("1111", card.getString("last4"))
        assertEquals("Visa", card.getString("brand"))
        assertEquals("4111111111111111", card.getJSONObject("data").getString("number"))
    }

    @Test fun v3ExportImportRoundTripsAllThreeKinds() {
        val file = VaultBackup.export(
            sourceVault(), "correct horse",
            decryptPw, decryptAddr, decryptCard, javaEncode
        )
        val target = FakeVault()
        assertEquals(3, importInto(target, file, "correct horse"))
        assertEquals(1, target.vault.size)
        assertEquals("robert", target.vault[0].username)
        assertEquals(1, target.addresses.size)
        assertEquals("Home", target.addresses[0].label)
        assertEquals(1, target.cards.size)
        assertEquals("1111", target.cards[0].last4)
        assertEquals("Visa", target.cards[0].brand)
        // Re-import is a no-op: dedup on all three kinds.
        assertEquals(0, importInto(target, file, "correct horse"))
    }

    @Test fun v2FileStillImportsPasswordsOnly() {
        val payload = JSONArray().put(
            JSONObject().put("site", "example.com").put("user", "robert")
                .put("pass", "hunter2")
        )
        val file = VaultBackup.envelopeEncrypt(payload.toString(), "correct horse", javaEncode)
            .put("v", 2).toString(2)
        val target = FakeVault()
        assertEquals(1, importInto(target, file, "correct horse"))
        assertEquals(1, target.vault.size)
        assertEquals("example.com", target.vault[0].site)
        assertTrue(target.addresses.isEmpty())
        assertTrue(target.cards.isEmpty())
    }

    @Test fun wrongPassphraseReturnsMinusOne() {
        val file = VaultBackup.export(
            sourceVault(), "correct horse",
            decryptPw, decryptAddr, decryptCard, javaEncode
        )
        val target = FakeVault()
        assertEquals(-1, importInto(target, file, "wrong horse"))
        assertTrue(target.vault.isEmpty())
        assertTrue(target.addresses.isEmpty())
        assertTrue(target.cards.isEmpty())
    }

    @Test fun unknownVersionReturnsMinusOne() {
        val file = VaultBackup.envelopeEncrypt("[]", "correct horse", javaEncode)
            .put("v", 99).toString(2)
        assertEquals(-1, importInto(FakeVault(), file, "correct horse"))
    }

    @Test fun wrongAppReturnsMinusOne() {
        val file = VaultBackup.envelopeEncrypt("[]", "correct horse", javaEncode)
            .put("app", "something-else").toString(2)
        assertEquals(-1, importInto(FakeVault(), file, "correct horse"))
    }
}
