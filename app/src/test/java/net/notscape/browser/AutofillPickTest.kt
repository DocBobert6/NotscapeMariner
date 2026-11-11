package net.notscape.browser

import net.notscape.browser.vault.AutofillFields
import net.notscape.browser.vault.AutofillFields.FieldInfo
import net.notscape.browser.vault.AutofillFields.formFillKinds
import net.notscape.browser.vault.AutofillFields.pickFields
import net.notscape.browser.vault.AutofillFields.pickFormFillFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // Beta.68 #4: address/card hint picking.

    @Test
    fun cardHintsPicked() {
        val slots = pickFormFillFields(
            listOf(
                hint("creditCardNumber"),
                hint("creditCardExpirationMonth"),
                hint("creditCardExpirationYear"),
                hint("creditCardSecurityCode")
            )
        )
        assertEquals(0, slots["number"])
        assertEquals(1, slots["expMonth"])
        assertEquals(2, slots["expYear"])
        // The CVC is recognised only to be excluded.
        assertFalse("cvc" in slots)
        assertEquals(setOf("card"), formFillKinds(slots))
    }

    @Test
    fun addressHintsPicked() {
        val slots = pickFormFillFields(
            listOf(
                hint("personName"),
                hint("postalAddress"),
                hint("postalCode")
            )
        )
        assertEquals(0, slots["name"])
        assertEquals(1, slots["street1"])
        assertEquals(2, slots["postal"])
        assertEquals(setOf("address"), formFillKinds(slots))
    }

    @Test
    fun autocompleteAttributesPicked() {
        val slots = pickFormFillFields(
            listOf(
                FieldInfo(emptyList(), null, "cc-number"),
                FieldInfo(emptyList(), null, "cc-exp"),
                FieldInfo(emptyList(), null, "cc-exp-month"),
                FieldInfo(emptyList(), null, "cc-exp-year"),
                FieldInfo(emptyList(), null, "postal-code"),
                FieldInfo(emptyList(), null, "country")
            )
        )
        assertEquals(0, slots["number"])
        assertEquals(1, slots["exp"])
        assertEquals(2, slots["expMonth"])
        assertEquals(3, slots["expYear"])
        assertEquals(4, slots["postal"])
        assertEquals(5, slots["country"])
        assertEquals(setOf("address", "card"), formFillKinds(slots))
    }

    @Test
    fun cvcAlonePicksNothing() {
        val slots = pickFormFillFields(listOf(hint("creditCardSecurityCode")))
        assertTrue(slots.isEmpty())
        assertTrue(formFillKinds(slots).isEmpty())
    }

    @Test
    fun bareNameOffersNoDataset() {
        val slots = pickFormFillFields(listOf(hint("personName")))
        assertEquals(0, slots["name"])
        assertTrue(formFillKinds(slots).isEmpty())
    }

    @Test
    fun firstFieldWinsPerSlot() {
        val slots = pickFormFillFields(
            listOf(hint("creditCardNumber"), hint("creditCardNumber"))
        )
        assertEquals(0, slots["number"])
        assertEquals(1, slots.size)
    }
}
