package net.notscape.browser.vault

/**
 * The system-autofill unlock window, kept in its own object on
 * purpose: the AutofillService class itself only exists on API 26+,
 * so no pre-26 code may reference it — but the vault screen (which
 * runs on every API level) must be able to record an unlock. Both
 * sides share this plain flag instead.
 *
 * Process-local only: a process death clears it, and the service
 * then runs its own unlock flow before serving anything. The window
 * matches the vault key's own 300-second authentication window.
 */
object AutofillUnlock {

    @Volatile
    var unlockedUntilMs: Long = 0L
        private set

    fun isUnlocked(): Boolean =
        System.currentTimeMillis() < unlockedUntilMs

    fun markUnlocked(windowMs: Long = 300_000L) {
        unlockedUntilMs = System.currentTimeMillis() + windowMs
    }
}
