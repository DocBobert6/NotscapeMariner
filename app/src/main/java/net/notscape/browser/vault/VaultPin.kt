package net.notscape.browser.vault

import android.util.Base64
import net.notscape.browser.data.AppStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App-PIN gate for the password vault (audit S-M5).
 *
 * On a device with no biometric or device credential enrolled, the vault
 * used to open on a bare tap — the Keystore key still encrypted entries at
 * rest, but anyone holding the unlocked phone walked straight in. Those
 * devices now ask for an app PIN instead, set on first use.
 *
 * Honest limit, stated on the lock screen: the PIN gates the vault UI, it
 * does not re-encrypt entries. At-rest encryption stays on the
 * non-auth-bound v1 Keystore key — an auth-bound key cannot work without a
 * device credential — and a forgotten PIN locks the vault out the same way
 * a lost device key does. The verifier is a salted PBKDF2-HMAC-SHA256 hash
 * at the same 600k iterations the vault export uses, stored as a setting;
 * the PIN itself is never stored.
 */
object VaultPin {

    /** Setting key carrying the PIN verifier ("v1:<iter>:<salt>:<hash>"). */
    const val SETTING_KEY = "vaultPinVerifier"

    private const val ITERATIONS = 600_000
    private const val MIN_LENGTH = 4
    private const val MAX_LENGTH = 32

    fun isSet(store: AppStore): Boolean =
        store.setting(SETTING_KEY, "").isNotBlank()

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH

    fun set(store: AppStore, pin: String) {
        require(isValidPin(pin)) { "PIN must be $MIN_LENGTH-$MAX_LENGTH characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, ITERATIONS)
        store.putSetting(
            SETTING_KEY,
            "v1:$ITERATIONS:" +
                Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(hash, Base64.NO_WRAP)
        )
    }

    fun check(store: AppStore, pin: String): Boolean {
        val parts = store.setting(SETTING_KEY, "").split(":")
        if (parts.size != 4 || parts[0] != "v1") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        // A tampered verifier must not dictate an unbounded PBKDF2 count.
        if (iterations <= 0 || iterations > 1_000_000) return false
        return try {
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            MessageDigest.isEqual(expected, derive(pin, salt, iterations))
        } catch (e: Exception) {
            false
        }
    }

    fun clear(store: AppStore) {
        store.removeSetting(SETTING_KEY)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }
}
