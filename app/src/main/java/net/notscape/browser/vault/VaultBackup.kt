package net.notscape.browser.vault

import android.util.Base64
import net.notscape.browser.data.AddressEntry
import net.notscape.browser.data.CardEntry
import net.notscape.browser.data.EncryptedBackup
import net.notscape.browser.data.VaultEntry
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-protected vault export. The vault's normal encryption key lives
 * in this device's Keystore and can never leave it, so moving passwords to
 * another device needs a separate file encrypted with a passphrase the user
 * chooses (PBKDF2 + AES-256-GCM).
 *
 * Format v3 (beta.68 #4): the envelope is unchanged (PBKDF2 600k +
 * AES-256-GCM), but the payload is now an object with "passwords",
 * "addresses" and "cards" arrays (plaintext inside the envelope,
 * same as passwords today). v2 files — a bare password array —
 * still import. The general full backup still excludes the vault
 * (and the new tables with it); these kinds export only here.
 *
 * The crypto lambdas and the Base64 codec are injected with
 * production defaults so the round-trip is unit-testable on the
 * JVM: android.util.Base64 and the Keystore cannot run there
 * (java.util.Base64 needs API 26, so production keeps the
 * Android codec). Callers use the plain export/import signatures.
 */
object VaultBackup {

    /** Current exports: PBKDF2-HMAC-SHA256 at the OWASP-recommended count. */
    private const val ITERATIONS = 600_000
    /** Iteration count used by v1 exports (kept so they still import). */
    private const val LEGACY_ITERATIONS = 120_000

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): javax.crypto.SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        // The factory returns a PBKDF2-labelled key, which an
        // AES/GCM cipher refuses ("Wrong algorithm: AES or
        // Rijndael required" — proven on the JVM, beta.68
        // push 1, run 43043152463). Wrap the same derived
        // bytes as an AES key: the key bytes are unchanged, so
        // files sealed by the old code still open.
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(raw.encoded, "AES")
    }

    /** Seal [payload] into the passphrase envelope. */
    internal fun envelopeEncrypt(
        payload: String,
        passphrase: String,
        encode: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) }
    ): JSONObject {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "notscape-vault")
            .put("v", 3)
            .put("iter", ITERATIONS)
            .put("salt", encode(salt))
            .put("iv", encode(iv))
            .put("data", encode(data))
    }

    /** Open the passphrase envelope; throws on a wrong passphrase. */
    internal fun envelopeDecrypt(
        root: JSONObject,
        passphrase: String,
        decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }
    ): String {
        val salt = decode(root.getString("salt"))
        val iv = decode(root.getString("iv"))
        val data = decode(root.getString("data"))
        // The file carries its KDF parameters (v2+); v1 files predate
        // the field and used the legacy iteration count.
        val iterations = root.optInt("iter", LEGACY_ITERATIONS)
        // See EncryptedBackup: a crafted file must not
        // dictate an unbounded PBKDF2 count.
        EncryptedBackup.checkIterations(iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    /** The v3 payload: decrypted passwords, addresses and cards.
     *  Corrupt address/card rows are skipped, never aborting the
     *  rest (a password decrypt failure still throws, as before —
     *  the caller reports it as an authentication problem). */
    internal fun buildPayload(
        store: VaultCollections,
        decryptPassword: (VaultEntry) -> String,
        decryptAddress: (AddressEntry) -> AddressData?,
        decryptCard: (CardEntry) -> CardData?
    ): JSONObject {
        val passwords = JSONArray()
        store.vault.forEach { e ->
            passwords.put(
                JSONObject()
                    .put("site", e.site)
                    .put("user", e.username)
                    .put("pass", decryptPassword(e))
            )
        }
        val addresses = JSONArray()
        store.addresses.forEach { e ->
            decryptAddress(e)?.let { d ->
                addresses.put(
                    JSONObject().put("label", e.label).put("data", d.toJson())
                )
            }
        }
        val cards = JSONArray()
        store.cards.forEach { e ->
            decryptCard(e)?.let { d ->
                cards.put(
                    JSONObject().put("label", e.label)
                        .put("last4", e.last4).put("brand", e.brand)
                        .put("data", d.toJson())
                )
            }
        }
        return JSONObject()
            .put("passwords", passwords)
            .put("addresses", addresses)
            .put("cards", cards)
    }

    fun export(
        store: VaultCollections,
        passphrase: String,
        decryptPassword: (VaultEntry) -> String =
            { VaultCrypto.decrypt(it.passwordEnc, it.site, it.id) },
        decryptAddress: (AddressEntry) -> AddressData? =
            { e -> runCatching {
                AddressData.fromJsonString(
                    VaultCrypto.decryptForKind(e.payloadEnc, "address", e.id)
                )
            }.getOrNull() },
        decryptCard: (CardEntry) -> CardData? =
            { e -> runCatching {
                CardData.fromJsonString(
                    VaultCrypto.decryptForKind(e.payloadEnc, "card", e.id)
                )
            }.getOrNull() },
        encode: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) }
    ): String {
        val payload = buildPayload(store, decryptPassword, decryptAddress, decryptCard)
        return envelopeEncrypt(payload.toString(), passphrase, encode).toString(2)
    }

    private fun importPasswordArray(
        store: VaultCollections,
        arr: JSONArray,
        encryptPassword: (String, String, Long) -> String
    ): Int {
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val site = o.getString("site")
            val user = o.optString("user")
            if (store.vault.any { it.site == site && it.username == user }) continue
            // The entry id is minted BEFORE encrypting (audit S-m7):
            // the ciphertext is bound to it as GCM additional data.
            val newId = System.currentTimeMillis() * 1000 + (0..999).random()
            store.addVault(
                VaultEntry(
                    id = newId,
                    site = site,
                    username = user,
                    passwordEnc = encryptPassword(o.getString("pass"), site, newId)
                )
            )
            added++
        }
        return added
    }

    /** Import one decrypted payload: a bare array is a v2 (or v1)
     *  password file; an object is v3. Returns how many entries
     *  were added. Dedup mirrors the save paths (passwords by
     *  site + user, addresses by label + street + postal, cards
     *  by full number), so a re-import adds nothing. */
    internal fun importPayload(
        store: VaultCollections,
        payloadText: String,
        encryptPassword: (String, String, Long) -> String,
        encryptAddress: (AddressData, Long) -> String,
        encryptCard: (CardData, Long) -> String,
        decryptAddress: (AddressEntry) -> AddressData?,
        decryptCard: (CardEntry) -> CardData?
    ): Int {
        var added = 0
        if (payloadText.trimStart().startsWith("[")) {
            return importPasswordArray(store, JSONArray(payloadText), encryptPassword)
        }
        val payload = JSONObject(payloadText)
        payload.optJSONArray("passwords")?.let {
            added += importPasswordArray(store, it, encryptPassword)
        }
        payload.optJSONArray("addresses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val label = o.optString("label")
                val data = AddressData.fromJson(o.getJSONObject("data"))
                val dup = store.addresses.any { e ->
                    e.label.equals(label, ignoreCase = true) &&
                        runCatching { decryptAddress(e) }.getOrNull()?.let { d ->
                            d.street1.equals(data.street1, ignoreCase = true) &&
                                d.postal.equals(data.postal, ignoreCase = true)
                        } == true
                }
                if (dup) continue
                val newId = newFormFillId()
                store.addAddress(AddressEntry(newId, label, encryptAddress(data, newId)))
                added++
            }
        }
        payload.optJSONArray("cards")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val label = o.optString("label")
                val last4 = o.optString("last4")
                val brand = o.optString("brand")
                val data = CardData.fromJson(o.getJSONObject("data"))
                val digits = data.number.filter { it.isDigit() }
                val dup = store.cards.any { e ->
                    e.last4 == last4 && e.brand == brand &&
                        runCatching { decryptCard(e) }.getOrNull()?.let { d ->
                            d.number.filter { it.isDigit() } == digits
                        } == true
                }
                if (dup) continue
                val newId = newFormFillId()
                store.addCard(CardEntry(newId, label, last4, brand, encryptCard(data, newId)))
                added++
            }
        }
        return added
    }

    /** Returns how many entries were imported, or -1 if the file/passphrase is wrong. */
    fun import(
        store: VaultCollections,
        json: String,
        passphrase: String,
        encryptPassword: (String, String, Long) -> String =
            { p, s, id -> VaultCrypto.encrypt(p, s, id) },
        encryptAddress: (AddressData, Long) -> String =
            { d, id -> VaultCrypto.encryptForKind(d.toJsonString(), "address", id) },
        encryptCard: (CardData, Long) -> String =
            { d, id -> VaultCrypto.encryptForKind(d.toJsonString(), "card", id) },
        decryptAddress: (AddressEntry) -> AddressData? =
            { e -> runCatching {
                AddressData.fromJsonString(
                    VaultCrypto.decryptForKind(e.payloadEnc, "address", e.id)
                )
            }.getOrNull() },
        decryptCard: (CardEntry) -> CardData? =
            { e -> runCatching {
                CardData.fromJsonString(
                    VaultCrypto.decryptForKind(e.payloadEnc, "card", e.id)
                )
            }.getOrNull() },
        decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }
    ): Int {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "notscape-vault") return -1
            // v1 files predate the field (0 = absent); 2 is the
            // password-array format, 3 the object format. Any
            // other version is a file this build cannot read.
            val version = root.optInt("v", 0)
            if (version != 0 && version != 1 && version != 2 && version != 3) return -1
            val payloadText = envelopeDecrypt(root, passphrase, decode)
            importPayload(
                store, payloadText, encryptPassword, encryptAddress, encryptCard,
                decryptAddress, decryptCard
            )
        } catch (e: VaultCrypto.AuthRequired) {
            // Not a bad file: the vault key needs a fresh device
            // authentication. Let the caller re-lock and say so.
            throw e
        } catch (e: Exception) {
            -1
        }
    }
}
