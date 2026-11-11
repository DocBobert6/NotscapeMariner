package net.notscape.browser.vault

/** Shared strong-password generator (beta.62 #42): SecureRandom,
 *  lowercase always in the pool, other classes opt-in, at least
 *  one character from each enabled class. Default length 16,
 *  minimum 8 (audit S-p5). */
object PasswordGenerator {

    fun generate(
        length: Int = 16,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true
    ): String {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val classes = buildList {
            add(lower)
            if (upper) add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (digits) add("0123456789")
            if (symbols) add("!@#\$%^&*()-_=+")
        }
        val n = length.coerceIn(8, 64).coerceAtLeast(classes.size)
        val pool = classes.joinToString("")
        val rng = java.security.SecureRandom()
        val chars = CharArray(n) { pool[rng.nextInt(pool.length)] }
        // Distinct slots, so no class's guaranteed character
        // can overwrite another's (beta.66: a fresh random
        // index per class let two classes collide, silently
        // dropping a class — the test failed on it).
        val slots = (0 until n).shuffled(rng)
        classes.forEachIndexed { i, cls ->
            chars[slots[i]] = cls[rng.nextInt(cls.length)]
        }
        return chars.concatToString()
    }
}
