package net.notscape.browser

import net.notscape.browser.data.AddressEntry
import net.notscape.browser.data.CardEntry
import net.notscape.browser.vault.AddressData
import net.notscape.browser.vault.AddressSaveAction
import net.notscape.browser.vault.CardData
import net.notscape.browser.vault.CardSaveAction
import net.notscape.browser.vault.FormFill
import net.notscape.browser.vault.detectBrand
import net.notscape.browser.vault.formatExpiry
import net.notscape.browser.vault.luhnValid
import net.notscape.browser.vault.maskedCardNumber
import net.notscape.browser.vault.parseExpiry
import net.notscape.browser.vault.pickAddressOffer
import net.notscape.browser.vault.pickCardOffer
import net.notscape.browser.vault.resolveAddressSave
import net.notscape.browser.vault.resolveCardSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta.68 #4: address/card form fill — pure decisions. */
class FormFillTest {

    @Test fun luhnAcceptsKnownTestNumbers() {
        assertTrue(luhnValid("4111111111111111")) // Visa
        assertTrue(luhnValid("5555555555554444")) // Mastercard
        assertTrue(luhnValid("378282246310005")) // Amex
        assertTrue(luhnValid("6011111111111117")) // Discover
        assertTrue(luhnValid("4111 1111 1111 1111")) // spaces tolerated
        assertTrue(luhnValid("4111-1111-1111-1111")) // dashes tolerated
    }

    @Test fun luhnRejectsBadNumbers() {
        assertFalse(luhnValid("4111111111111112")) // single-digit error
        assertFalse(luhnValid("411111111111")) // too short
        assertFalse(luhnValid("41111111111111111111")) // too long
        assertFalse(luhnValid("4111a11111111111")) // non-digit
        assertFalse(luhnValid("")) // empty
        assertFalse(luhnValid("378282246310006")) // Amex, one digit off
    }

    @Test fun brandDetectionFromBinPrefix() {
        assertEquals("Visa", detectBrand("4111111111111111"))
        assertEquals("Mastercard", detectBrand("5555555555554444"))
        assertEquals("Mastercard", detectBrand("5105105105105100"))
        assertEquals("Mastercard", detectBrand("2221000000000009"))
        assertEquals("Mastercard", detectBrand("2720999999999999"))
        assertEquals("Amex", detectBrand("378282246310005"))
        assertEquals("Amex", detectBrand("371449635398431"))
        assertEquals("Discover", detectBrand("6011111111111117"))
        assertEquals("Discover", detectBrand("6500000000000002"))
        assertEquals("Card", detectBrand("9999999999999999"))
        assertEquals("Card", detectBrand(""))
    }

    @Test fun expiryParsing() {
        assertEquals(9 to 2028, parseExpiry("09/28"))
        assertEquals(9 to 2028, parseExpiry("9/2028"))
        assertEquals(12 to 2030, parseExpiry("122030"))
        assertEquals(1 to 2029, parseExpiry("01-29"))
        assertEquals(11 to 2027, parseExpiry("11 / 27"))
        assertNull(parseExpiry("13/28")) // month out of range
        assertNull(parseExpiry("00/28"))
        assertNull(parseExpiry("09/")) // no year
        assertNull(parseExpiry("Sept 28"))
        assertNull(parseExpiry(""))
    }

    @Test fun maskedDisplayAndExpiryFormat() {
        assertEquals("•••• •••• •••• 1111", maskedCardNumber("4111111111111111"))
        assertEquals("•••• •••••• 10005", maskedCardNumber("378282246310005"))
        assertEquals("09/28", formatExpiry(9, 2028))
        assertEquals("01/30", formatExpiry(1, 2030))
    }

    @Test fun addressOfferNeedsTwoFieldsOrNamePlusLine() {
        val full = pickAddressOffer(
            mapOf(
                "name" to "Robert", "street1" to "1 Main St",
                "city" to "Dry Ridge", "postal" to "41035"
            )
        )
        assertEquals("Robert", full?.name)
        assertEquals("1 Main St", full?.street1)
        // Name + one line is enough.
        assertTrue(pickAddressOffer(mapOf("name" to "Robert", "city" to "Dry Ridge")) != null)
        // Two address fields without a name is enough.
        assertTrue(pickAddressOffer(mapOf("city" to "Dry Ridge", "postal" to "41035")) != null)
        // A lone postcode lookup never prompts.
        assertNull(pickAddressOffer(mapOf("postal" to "41035")))
        // A lone name never prompts.
        assertNull(pickAddressOffer(mapOf("name" to "Robert")))
        // Nothing at all never prompts.
        assertNull(pickAddressOffer(emptyMap()))
    }

    @Test fun cardOfferNeedsLuhnPlusExpiry() {
        val card = pickCardOffer(
            mapOf("name" to "Robert", "number" to "4111111111111111", "expiry" to "09/28")
        )
        assertEquals("4111111111111111", card?.number)
        assertEquals(9, card?.expMonth)
        assertEquals(2028, card?.expYear)
        // Separate month/year fields work too.
        assertTrue(
            pickCardOffer(
                mapOf("number" to "5555555555554444", "expMonth" to "12", "expYear" to "2030")
            ) != null
        )
        // A number failing Luhn never prompts.
        assertNull(pickCardOffer(mapOf("number" to "4111111111111112", "expiry" to "09/28")))
        // No expiry never prompts.
        assertNull(pickCardOffer(mapOf("number" to "4111111111111111")))
        // No number never prompts.
        assertNull(pickCardOffer(mapOf("expiry" to "09/28")))
        // A CVC value is never read, even for the decision: a
        // form carrying one still offers on number + expiry alone.
        val withCvc = pickCardOffer(
            mapOf("number" to "4111111111111111", "expiry" to "09/28", "cvc" to "123")
        )
        assertEquals("4111111111111111", withCvc?.number)
    }

    @Test fun addressPayloadJsonRoundTrips() {
        val data = AddressData(
            name = "Robert", org = "Acme", street1 = "1 Main St", street2 = "Apt 2",
            city = "Dry Ridge", region = "KY", postal = "41035",
            country = "USA", phone = "555-1", email = "r@example.com"
        )
        val back = AddressData.fromJsonString(data.toJsonString())
        assertEquals(data, back)
        // Missing keys default to empty, never crash.
        assertEquals(AddressData(name = "Robert"), AddressData.fromJsonString("{\"name\":\"Robert\"}"))
    }

    @Test fun cardPayloadJsonRoundTrips() {
        val data = CardData(
            name = "Robert", number = "4111111111111111",
            expMonth = 9, expYear = 2028, billingAddressId = 42
        )
        val back = CardData.fromJsonString(data.toJsonString())
        assertEquals(data, back)
        assertNull(CardData.fromJsonString("{\"number\":\"4111\"}").billingAddressId)
    }

    private val storedCard = CardEntry(1, "Main card", "1111", "Visa", "enc-1")
    private val storedCardData = CardData(
        name = "Robert", number = "4111111111111111", expMonth = 9, expYear = 2028
    )

    @Test fun resolveCardSave() {
        val decrypt: (CardEntry) -> CardData? = { if (it.id == 1L) storedCardData else null }
        // Same number, same name + expiry: nothing to do.
        assertEquals(
            CardSaveAction.AlreadySaved,
            resolveCardSave(listOf(storedCard), storedCardData, decrypt)
        )
        // Same number, new expiry: update in place.
        assertEquals(
            CardSaveAction.Update(storedCard),
            resolveCardSave(
                listOf(storedCard),
                storedCardData.copy(expMonth = 10, expYear = 2029), decrypt
            )
        )
        // A different number inserts.
        assertEquals(
            CardSaveAction.Insert,
            resolveCardSave(
                listOf(storedCard),
                CardData(name = "Robert", number = "5555555555554444", expMonth = 1, expYear = 2030),
                decrypt
            )
        )
        // Empty vault inserts.
        assertEquals(
            CardSaveAction.Insert,
            resolveCardSave(emptyList(), storedCardData, decrypt)
        )
    }

    private val storedAddress = AddressEntry(2, "Home", "enc-2")
    private val storedAddressData = AddressData(
        name = "Robert", street1 = "1 Main St", city = "Dry Ridge", postal = "41035"
    )

    @Test fun resolveAddressSave() {
        val decrypt: (AddressEntry) -> AddressData? =
            { if (it.id == 2L) storedAddressData else null }
        assertEquals(
            AddressSaveAction.AlreadySaved,
            resolveAddressSave(listOf(storedAddress), storedAddressData, "Home", decrypt)
        )
        // Same label + street + postal, different city: update.
        assertEquals(
            AddressSaveAction.Update(storedAddress),
            resolveAddressSave(
                listOf(storedAddress),
                storedAddressData.copy(city = "Crittenden"), "Home", decrypt
            )
        )
        // A different label inserts, even with the same street.
        assertEquals(
            AddressSaveAction.Insert,
            resolveAddressSave(listOf(storedAddress), storedAddressData, "Work", decrypt)
        )
        // Empty vault inserts.
        assertEquals(
            AddressSaveAction.Insert,
            resolveAddressSave(emptyList(), storedAddressData, "Home", decrypt)
        )
    }

    @Test fun fillAllowedNeedsHttps() {
        assertTrue(FormFill.fillAllowed("https://shop.example/checkout"))
        assertFalse(FormFill.fillAllowed("http://shop.example/checkout"))
        assertFalse(FormFill.fillAllowed(null))
        assertFalse(FormFill.fillAllowed(""))
        assertFalse(FormFill.fillAllowed("about:blank"))
    }

    @Test fun valuesMatchNormalises() {
        // A page may reformat the number with spaces.
        assertTrue(FormFill.valuesMatch("number", "4111111111111111", "4111 1111 1111 1111"))
        assertFalse(FormFill.valuesMatch("number", "4111111111111111", "4111111111111112"))
        // Expiry compares digits-only too.
        assertTrue(FormFill.valuesMatch("exp", "09/28", "09/28"))
        assertTrue(FormFill.valuesMatch("expMonth", "09", "9"))
        // Postal/city compare case-insensitively.
        assertTrue(FormFill.valuesMatch("postal", "sw1a 1aa", "SW1A 1AA"))
        assertTrue(FormFill.valuesMatch("city", "Dry Ridge", "dry ridge"))
        assertFalse(FormFill.valuesMatch("city", "Dry Ridge", "Crittenden"))
        // Everything else compares trimmed.
        assertTrue(FormFill.valuesMatch("name", "Robert", "Robert "))
        assertFalse(FormFill.valuesMatch("name", "Robert", "Bob"))
    }

    @Test fun addressAndCardValues() {
        val address = FormFill.addressValues(storedAddressData)
        assertEquals("Robert", address["name"])
        assertEquals("1 Main St", address["street1"])
        assertEquals("Dry Ridge", address["city"])
        assertFalse("org" in address) // blank fields are skipped
        val card = FormFill.cardValues(storedCardData)
        assertEquals("4111111111111111", card["number"])
        assertEquals("09/28", card["exp"])
        assertEquals("09", card["expMonth"])
        assertEquals("2028", card["expYear"])
    }

    @Test fun suggestedLabels() {
        assertEquals("Robert", storedAddressData.suggestedLabel())
        assertEquals("Address", AddressData().suggestedLabel())
        assertEquals("Visa •••• 1111", storedCardData.suggestedLabel())
    }

    @Test fun parseLocateAndReadback() {
        // evaluateJavascript returns the script result JSON-quoted.
        val located = FormFill.parseLocate(
            "\"{\\\"slots\\\": {\\\"number\\\": \\\"INPUT\\\", \\\"country\\\": \\\"SELECT\\\"}}\""
        )
        assertEquals("INPUT", located["number"])
        assertEquals("SELECT", located["country"])
        assertTrue(FormFill.parseLocate(null).isEmpty())
        assertTrue(FormFill.parseLocate("\"not json\"").isEmpty())
        val back = FormFill.parseReadback(
            "\"{\\\"number\\\": \\\"4111 1111 1111 1111\\\", \\\"country\\\": \\\"US\\\"}\""
        )
        assertEquals("4111 1111 1111 1111", back["number"])
        assertEquals("US", back["country"])
    }
}
