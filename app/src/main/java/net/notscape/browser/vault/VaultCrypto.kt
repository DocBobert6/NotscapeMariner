package net.notscape.browser.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import net.notscape.browser.data.AppStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Vault encryption: AES-256/GCM, keys in the Android Keystore, plaintext
 * never touches disk.
 *
 * Two key generations exist. The original key (v1) needs no user
 * authentication. The current key (v2) is bound to user authentication:
 * it can only be used within a short window after the user unlocks with
 * biometrics or their device credential, and it dies permanently if a
 * new biometric is enrolled. Records written under v2 carry a "2:"
 * prefix so decryption always knows which key a record belongs to;
 * unprefixed records are v1.
 *
 * Audit S-m7: new records carry a "v3:" prefix and bind the ciphertext
 * to the entry it belongs to — the GCM additional authenticated data is
 * "<site>|<id>", so a record copied onto another entry (or another
 * vault) fails decryption instead of silently filling the wrong site's
 * password. Legacy "2:" and unprefixed records still decrypt exactly as
 * before; [migrateAadIfNeeded] moves them onto v3 under their same key.
 *
 * Right after a successful vault unlock, [migrateIfNeeded] creates v2
 * if needed and re-encrypts every v1 record under it, retiring the v1
 * key only after every record verifies under the new key. Any failure
 * aborts the migration with every record untouched.
 */
object VaultCrypto {

    /** A vault operation needs a fresh device authentication first. */
    class AuthRequired : Exception("Vault authentication required")

    private const val ALIAS_V1 = "notscape_vault_key"
    private const val ALIAS_V2 = "notscape_vault_key_v2"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val V2_PREFIX = "2:"
    private const val V3_PREFIX = "v3:"

    /**
     * True once a migration has re-encrypted records
     * in memory but the Room write was not confirmed
     * yet, so the v1 key must be kept: the next
     * unlock retries the confirmation before any
     * retirement. Without this, a same-process
     * re-unlock could retire v1 while the write was
     * still queued.
     */
    @Volatile
    private var pendingV1Retire = false

    /** Seconds after a successful device authentication during which
     *  the v2 key may be used. The vault's unlock prompt opens the
     *  window; anything attempted later asks for authentication again. */
    private const val AUTH_WINDOW_SECONDS = 300

    private fun keystore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun hasV1(): Boolean = keystore().containsAlias(ALIAS_V1)
    fun hasV2(): Boolean = keystore().containsAlias(ALIAS_V2)

    private fun v1Key(create: Boolean): SecretKey? {
        val ks = keystore()
        (ks.getKey(ALIAS_V1, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS_V1,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun v2Key(create: Boolean): SecretKey? {
        val ks = keystore()
        (ks.getKey(ALIAS_V2, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS_V2,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return gen.generateKey()
    }

    private fun encryptWith(key: SecretKey, plain: String, aad: String? = null): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (e: UserNotAuthenticatedException) {
            throw AuthRequired()
        }
        if (aad != null) cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(ct, Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decryptWith(key: SecretKey, stored: String, aad: String? = null): String {
        val parts = stored.split(":", limit = 2)
        require(parts.size == 2) { "Bad vault record" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        } catch (e: UserNotAuthenticatedException) {
            throw AuthRequired()
        }
        if (aad != null) cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ct).toString(Charsets.UTF_8)
    }

    /** GCM AAD binding a record to its entry (audit S-m7). */
    private fun aadFor(site: String, id: Long): String = "$site|$id"

    /**
     * Encrypt under the current key, bound to [site] and [id].
     * The record carries a "v3:<key version>:" prefix so decryption
     * knows both the key and that AAD verification applies.
     */
    fun encrypt(plain: String, site: String, id: Long): String {
        val aad = aadFor(site, id)
        return if (hasV2()) {
            V3_PREFIX + "2:" + encryptWith(v2Key(create = false)!!, plain, aad)
        } else {
            V3_PREFIX + "1:" + encryptWith(v1Key(create = true)!!, plain, aad)
        }
    }

    /**
     * Decrypt a v3 record, verifying its AAD binding; legacy records
     * (v2-prefixed or unprefixed) decrypt exactly as before and ignore
     * [site]/[id]. A v3 record decrypted with a different site or id
     * fails GCM authentication.
     */
    fun decrypt(stored: String, site: String, id: Long): String {
        if (!stored.startsWith(V3_PREFIX)) return decrypt(stored)
        val afterV3 = stored.substring(V3_PREFIX.length)
        val keyVersion = afterV3.substringBefore(":")
        val body = afterV3.substringAfter(":")
        val key = if (keyVersion == "2") {
            v2Key(create = false) ?: throw IllegalStateException("Vault key missing")
        } else {
            v1Key(create = false) ?: throw IllegalStateException("Vault key missing")
        }
        return decryptWith(key, body, aadFor(site, id))
    }

    fun encrypt(plain: String): String =
        if (hasV2()) {
            V2_PREFIX + encryptWith(v2Key(create = false)!!, plain)
        } else {
            encryptWith(v1Key(create = true)!!, plain)
        }

    fun decrypt(stored: String): String =
        if (stored.startsWith(V2_PREFIX)) {
            val key = v2Key(create = false)
                ?: throw IllegalStateException("Vault key missing")
            decryptWith(key, stored.substring(V2_PREFIX.length))
        } else {
            val key = v1Key(create = false)
                ?: throw IllegalStateException("Vault key missing")
            decryptWith(key, stored)
        }

    /**
     * Move the vault onto the authentication-bound key. Call only in
     * the moment right after a successful device authentication (the
     * new key cannot be used outside its window). Returns true when a
     * migration actually happened. On any failure the half-made new key
     * is destroyed and every record is left exactly as it was.
     */
    fun migrateIfNeeded(store: AppStore): Boolean {
        val legacy = store.vault.filter { !it.passwordEnc.startsWith(V2_PREFIX) }
        if (!hasV1()) {
            // Fresh install: just make sure the current key exists so
            // new records use it from the start.
            if (!hasV2()) runCatching { v2Key(create = true) }
            return false
        }
        if (legacy.isEmpty()) {
            // The old key protects nothing any more: retire it once
            // the current key exists - and, when a previous
            // migration's write was never confirmed, only
            // once that write is confirmed now. At process
            // start the in-memory records come from Room,
            // so an empty legacy set already means Room
            // holds v2; the pending flag covers the
            // same-process re-unlock case.
            if (!hasV2()) runCatching { v2Key(create = true) }
            if (hasV2() && (!pendingV1Retire || store.awaitPendingWrites())) {
                runCatching { keystore().deleteEntry(ALIAS_V1) }
                pendingV1Retire = false
            }
            return true
        }
        val oldKey = v1Key(create = false) ?: return false
        val v2Existed = hasV2()
        val newKey = (if (v2Existed) v2Key(create = false)
            else runCatching { v2Key(create = true) }.getOrNull()) ?: return false
        val moved = HashMap<Long, String>()
        try {
            for (entry in legacy) {
                val plain = decryptWith(oldKey, entry.passwordEnc)
                val enc = V2_PREFIX + encryptWith(newKey, plain)
                check(decrypt(enc) == plain) { "Verification failed" }
                moved[entry.id] = enc
            }
        } catch (e: Exception) {
            // Abort: destroy the new key only if this run created it,
            // and leave every record exactly as it was.
            if (!v2Existed) runCatching { keystore().deleteEntry(ALIAS_V2) }
            return false
        }
        store.replaceVaultPasswords(moved)
        // Retire v1 only once the re-encrypted records
        // are confirmed in Room. On timeout the key is
        // kept and the next unlock retries (see
        // pendingV1Retire): deleting early could strand
        // v1 ciphertext with no key if the process died
        // in between.
        if (store.awaitPendingWrites()) {
            runCatching { keystore().deleteEntry(ALIAS_V1) }
            pendingV1Retire = false
        } else {
            pendingV1Retire = true
        }
        return true
    }

    /**
     * Move legacy (non-v3) records onto v3 AAD binding, under their
     * SAME key version — v2 rows need the authentication window, so
     * when it is closed this returns false and the next unlock
     * retries. Corrupt rows are skipped, never aborting the rest.
     * Returns true when nothing remains to migrate (or nothing was
     * pending). Call after [migrateIfNeeded] on a fresh unlock, and
     * on the app-PIN unlock path (credential-less devices hold only
     * v1 rows, so it works there too).
     */
    fun migrateAadIfNeeded(store: AppStore): Boolean {
        val pending = store.vault.filter { !it.passwordEnc.startsWith(V3_PREFIX) }
        if (pending.isEmpty()) return true
        val moved = HashMap<Long, String>()
        try {
            for (entry in pending) {
                val plain = try {
                    decrypt(entry.passwordEnc)
                } catch (e: AuthRequired) {
                    throw e
                } catch (e: Exception) {
                    continue
                }
                val isV2 = entry.passwordEnc.startsWith(V2_PREFIX)
                val enc = if (isV2) {
                    val key = v2Key(create = false) ?: return false
                    V3_PREFIX + "2:" + encryptWith(key, plain, aadFor(entry.site, entry.id))
                } else {
                    val key = v1Key(create = false) ?: continue
                    V3_PREFIX + "1:" + encryptWith(key, plain, aadFor(entry.site, entry.id))
                }
                check(decrypt(enc, entry.site, entry.id) == plain) { "Verification failed" }
                moved[entry.id] = enc
            }
        } catch (e: AuthRequired) {
            return false
        }
        if (moved.isNotEmpty()) store.replaceVaultPasswords(moved)
        return true
    }
}
