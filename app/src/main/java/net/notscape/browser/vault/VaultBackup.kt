package net.notscape.browser.vault

import android.util.Base64
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.EncryptedBackup
import net.notscape.browser.data.VaultEntry
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Passphrase-protected vault export. The vault's normal encryption key lives
 * in this device's Keystore and can never leave it, so moving passwords to
 * another device needs a separate file encrypted with a passphrase the user
 * chooses (PBKDF2 + AES-256-GCM).
 */
object VaultBackup {

    /** Current exports: PBKDF2-HMAC-SHA256 at the OWASP-recommended count. */
    private const val ITERATIONS = 600_000
    /** Iteration count used by v1 exports (kept so they still import). */
    private const val LEGACY_ITERATIONS = 120_000

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): javax.crypto.SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
    }

    fun export(store: AppStore, passphrase: String): String {
        val plain = JSONArray()
        store.vault.forEach { e ->
            plain.put(
                JSONObject()
                    .put("site", e.site)
                    .put("user", e.username)
                    .put("pass", VaultCrypto.decrypt(e.passwordEnc))
            )
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toString().toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "notscape-vault")
            .put("v", 2)
            .put("iter", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            .toString(2)
    }

    /** Returns how many entries were imported, or -1 if the file/passphrase is wrong. */
    fun import(store: AppStore, json: String, passphrase: String): Int {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "notscape-vault") return -1
            val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(root.getString("data"), Base64.NO_WRAP)
            // The file carries its KDF parameters (v2+); v1 files predate
            // the field and used the legacy iteration count.
            val iterations = root.optInt("iter", LEGACY_ITERATIONS)
            // See EncryptedBackup: a crafted file must not
            // dictate an unbounded PBKDF2 count.
            EncryptedBackup.checkIterations(iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(128, iv))
            val arr = JSONArray(String(cipher.doFinal(data), Charsets.UTF_8))
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val site = o.getString("site")
                val user = o.optString("user")
                if (store.vault.any { it.site == site && it.username == user }) continue
                store.addVault(
                    VaultEntry(
                        id = System.currentTimeMillis() * 1000 + (0..999).random(),
                        site = site,
                        username = user,
                        passwordEnc = VaultCrypto.encrypt(o.getString("pass"))
                    )
                )
                added++
            }
            added
        } catch (e: VaultCrypto.AuthRequired) {
            // Not a bad file: the vault key needs a fresh device
            // authentication. Let the caller re-lock and say so.
            throw e
        } catch (e: Exception) {
            -1
        }
    }
}
