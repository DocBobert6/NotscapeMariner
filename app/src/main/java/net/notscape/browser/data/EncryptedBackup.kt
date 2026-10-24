package net.notscape.browser.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Passphrase-encrypted full backup (0.0.9993): the vault
 * export's envelope (PBKDF2-HMAC-SHA256 at 600k + AES-256-GCM)
 * wrapped around the plain backup document, so a backup can
 * travel through a shared folder - the user's own sync -
 * without exposing its contents. Different app tag from the
 * vault export, so the two files can never be confused.
 */
object EncryptedBackup {

    private const val ITERATIONS = 600_000

    private fun deriveKey(
        passphrase: String, salt: ByteArray, iterations: Int
    ): javax.crypto.SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
    }

    fun encrypt(plain: String, passphrase: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(passphrase, salt, ITERATIONS),
            GCMParameterSpec(128, iv)
        )
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "notscape-backup")
            .put("v", 1)
            .put("iter", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            .toString(2)
    }

    fun isEncrypted(text: String): Boolean = runCatching {
        JSONObject(text).optString("app") == "notscape-backup"
    }.getOrDefault(false)

    /** The decrypted backup document, or null on a wrong
     *  passphrase or a malformed envelope. */
    fun decrypt(envelope: String, passphrase: String): String? =
        runCatching {
            val root = JSONObject(envelope)
            require(root.optString("app") == "notscape-backup")
            val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(root.getString("data"), Base64.NO_WRAP)
            val iterations = root.optInt("iter", ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, salt, iterations),
                GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
}
