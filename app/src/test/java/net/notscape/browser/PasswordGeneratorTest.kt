package net.notscape.browser

import net.notscape.browser.vault.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared generator (beta.62 #42): length + class guarantees. */
class PasswordGeneratorTest {

    @Test
    fun defaultLengthIsSixteen() {
        assertEquals(16, PasswordGenerator.generate().length)
    }

    @Test
    fun allClassesPresentByDefault() {
        val pw = PasswordGenerator.generate()
        assertTrue(pw.any { it.isLowerCase() })
        assertTrue(pw.any { it.isUpperCase() })
        assertTrue(pw.any { it.isDigit() })
        assertTrue(pw.any { "!@#$%^&*()-_=+".contains(it) })
    }

    @Test
    fun lengthClampsToMinimumEight() {
        assertEquals(8, PasswordGenerator.generate(length = 3).length)
        assertEquals(24, PasswordGenerator.generate(length = 24).length)
    }

    @Test
    fun generationsDiffer() {
        assertNotEquals(PasswordGenerator.generate(), PasswordGenerator.generate())
    }

    @Test
    fun disabledClassesStayOut() {
        val pw = PasswordGenerator.generate(upper = false, digits = false, symbols = false)
        assertTrue(pw.all { it.isLowerCase() })
    }
}
