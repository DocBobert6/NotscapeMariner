package net.notscape.browser

import net.notscape.browser.vault.AutofillFields
import net.notscape.browser.vault.AutofillFields.FieldInfo
import net.notscape.browser.vault.AutofillFields.pickFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Autofill field picking — the Android structure walk is thin. */
class AutofillPickTest {

    private fun hint(vararg hints: String) =
        FieldInfo(hints.toList(), null, null)

    @Test
    fun hintsWin() {
        val (u, p) = pickFields(
            listOf(
                hint("username"),
                hint("password")
            )
        )
        assertEquals(0, u)
        assertEquals(1, p)
    }

    @Test
    fun htmlTypeAndAutocomplete() {
        val (u, p) = pickFields(
            listOf(
                FieldInfo(emptyList(), "email", null),
                FieldInfo(emptyList(), "password", null)
            )
        )
        assertEquals(0, u)
        assertEquals(1, p)
        val (u2, p2) = pickFields(
            listOf(
                FieldInfo(emptyList(), null, "username"),
                FieldInfo(emptyList(), null, null)
            )
        )
        assertEquals(0, u2)
        assertNull(p2)
    }

    @Test
    fun passwordFieldIsNeverPickedAsUsername() {
        val (u, p) = pickFields(listOf(hint("password")))
        assertNull(u)
        assertEquals(0, p)
    }

    @Test
    fun noFieldsMeansNoPick() {
        val (u, p) = pickFields(emptyList())
        assertNull(u)
        assertNull(p)
    }
}
