package net.notscape.browser.vault

import net.notscape.browser.data.AddressEntry
import net.notscape.browser.data.CardEntry
import org.json.JSONObject

/**
 * Address + card form fill (beta.68 #4): pure data and decisions.
 *
 * Everything here is Android-free so it is unit-testable on the JVM:
 * the payload JSON shapes, Luhn + brand detection, expiry parsing,
 * the conservative capture decisions, the masked display, and the
 * Insert / Update / AlreadySaved dedup (same shape as
 * SaveLogin.resolveSave). The crypto (VaultCrypto.encryptForKind)
 * and the store live outside this file.
 *
 * Standing rules carried over: CVC is never stored or captured
 * (D1); nothing here ever reads a cc-csc field, even to decide.
 */

/** Same id allocator as vault ids (VaultScreen.kt:238,764,
 *  VaultAutofillActivity.kt:179). */
fun newFormFillId(): Long = System.currentTimeMillis() * 1000 + (0..999).random()

/** One address profile. Stored as a single encrypted JSON blob
 *  (see [AddressEntry.payloadEnc]); only the label is plaintext. */
data class AddressData(
    val name: String = "",
    val org: String = "",
    val street1: String = "",
    val street2: String = "",
    val city: String = "",
    val region: String = "",
    val postal: String = "",
    val country: String = "",
    val phone: String = "",
    val email: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("org", org)
        .put("street1", street1).put("street2", street2)
        .put("city", city).put("region", region)
        .put("postal", postal).put("country", country)
        .put("phone", phone).put("email", email)

    fun toJsonString(): String = toJson().toString()

    /** Label for a freshly captured profile: the name, then the
     *  city, then a plain fallback. The user can rename it in
     *  the vault afterwards. */
    fun suggestedLabel(): String =
        name.ifBlank { city }.ifBlank { "Address" }

    companion object {
        fun fromJson(o: JSONObject): AddressData = AddressData(
            name = o.optString("name"), org = o.optString("org"),
            street1 = o.optString("street1"), street2 = o.optString("street2"),
            city = o.optString("city"), region = o.optString("region"),
            postal = o.optString("postal"), country = o.optString("country"),
            phone = o.optString("phone"), email = o.optString("email")
        )

        fun fromJsonString(s: String): AddressData = fromJson(JSONObject(s))
    }
}

/** One payment card. The full number lives only inside the
 *  encrypted blob; [CardEntry.last4] + [CardEntry.brand] are the
 *  plaintext chooser fields. CVC is never stored (D1). */
data class CardData(
    val name: String = "",
    val number: String = "",
    val expMonth: Int = 0,
    val expYear: Int = 0,
    /** Id of the linked billing address, or null when unlinked. */
    val billingAddressId: Long? = null
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("name", name).put("number", number)
            .put("expMonth", expMonth).put("expYear", expYear)
        if (billingAddressId != null) o.put("billingAddressId", billingAddressId)
        return o
    }

    fun toJsonString(): String = toJson().toString()

    /** Brand + masked number for chooser rows and fresh-capture
     *  labels ("Visa •••• 1111"). The full number never leaves
     *  the blob. */
    fun displayLabel(): String = "${brand()} •••• ${last4()}"

    /** Label for a freshly captured card. The user can rename it
     *  in the vault afterwards. */
    fun suggestedLabel(): String = displayLabel()

    fun last4(): String = number.filter { it.isDigit() }.takeLast(4)

    fun brand(): String = detectBrand(number)

    companion object {
        fun fromJson(o: JSONObject): CardData {
            val link = o.optLong("billingAddressId", -1)
            return CardData(
                name = o.optString("name"), number = o.optString("number"),
                expMonth = o.optInt("expMonth", 0), expYear = o.optInt("expYear", 0),
                billingAddressId = if (link < 0) null else link
            )
        }

        fun fromJsonString(s: String): CardData = fromJson(JSONObject(s))
    }
}

/** Luhn check on a card number. Spaces and dashes are tolerated;
 *  anything else, or a length outside 13–19 digits, fails. Luhn
 *  is a typo check only — a pass is not proof of a real card. */
fun luhnValid(number: String): Boolean {
    if (number.any { !it.isDigit() && it != ' ' && it != '-' }) return false
    val digits = number.filter { it.isDigit() }
    if (digits.length !in 13..19) return false
    var sum = 0
    var double = false
    for (i in digits.length - 1 downTo 0) {
        var d = digits[i] - '0'
        if (double) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
        double = !double
    }
    return sum % 10 == 0
}

/** Display brand from the BIN prefix. A display convenience
 *  only — never used for a decision; co-badged and new BIN
 *  ranges fall through to "Card". */
fun detectBrand(number: String): String {
    val d = number.filter { it.isDigit() }
    return when {
        d.startsWith("4") -> "Visa"
        d.startsWith("34") || d.startsWith("37") -> "Amex"
        d.startsWith("6011") || d.startsWith("65") -> "Discover"
        d.length >= 2 && d.substring(0, 2).toIntOrNull() in 51..55 -> "Mastercard"
        d.length >= 4 && d.substring(0, 4).toIntOrNull() in 2221..2720 -> "Mastercard"
        else -> "Card"
    }
}

/** Masked card display: bullets in groups of four, then the
 *  last four digits ("•••• •••• •••• 1234"). */
fun maskedCardNumber(number: String): String {
    val d = number.filter { it.isDigit() }
    if (d.length < 4) return "••••"
    if (d.length == 15 && (d.startsWith("34") || d.startsWith("37"))) {
        // Amex prints 4-6-5: the last group holds five digits.
        return "•••• •••••• ${d.takeLast(5)}"
    }
    val last4 = d.takeLast(4)
    val groups = "•".repeat(d.length - 4).chunked(4).joinToString(" ")
    return "$groups $last4"
}

/** Expiry as shown in chooser rows ("09/28"). */
fun formatExpiry(month: Int, year: Int): String =
    "%02d/%02d".format(month, year % 100)

/** Parse an expiry as typed on a form: "MM/YY", "MM/YYYY",
 *  "MMYY", "MM-YY". Two-digit years assume 20xx. Null when
 *  the month is out of range or the shape is unrecognised. */
fun parseExpiry(raw: String): Pair<Int, Int>? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val m = Regex("^(\\d{1,2})\\s*[/\\-]\\s*(\\d{2}|\\d{4})$").matchEntire(t)
        ?: Regex("^(\\d{2})(\\d{2}|\\d{4})$").matchEntire(t)
        ?: return null
    val month = m.groupValues[1].toIntOrNull() ?: return null
    if (month !in 1..12) return null
    val y = m.groupValues[2].toIntOrNull() ?: return null
    val year = if (y < 100) 2000 + y else y
    if (year !in 2000..2099) return null
    return month to year
}

/** Conservative address-capture decision: at least two recognised
 *  address fields non-empty, or a full name + one address line.
 *  A lone postcode lookup never prompts. Keys are the field names
 *  in [AddressData] ("name", "street1", "city", ...). */
fun pickAddressOffer(values: Map<String, String>): AddressData? {
    val v = values.mapValues { it.value.trim() }
    val addressFields = listOf("street1", "street2", "city", "region", "postal", "country")
    val filled = addressFields.count { !v[it].isNullOrBlank() }
    val hasName = !v["name"].isNullOrBlank()
    val hasLine = listOf("street1", "street2", "city", "postal")
        .any { !v[it].isNullOrBlank() }
    if (filled < 2 && !(hasName && hasLine)) return null
    return AddressData(
        name = v["name"].orEmpty(), org = v["org"].orEmpty(),
        street1 = v["street1"].orEmpty(), street2 = v["street2"].orEmpty(),
        city = v["city"].orEmpty(), region = v["region"].orEmpty(),
        postal = v["postal"].orEmpty(), country = v["country"].orEmpty(),
        phone = v["phone"].orEmpty(), email = v["email"].orEmpty()
    )
}

/** Conservative card-capture decision: the number must pass Luhn
 *  and an expiry must parse. Keys: "number", "expiry" (or
 *  "expMonth"/"expYear"), "name". The CVC value is never read,
 *  even for the decision — no "cvc" key is consulted here. */
fun pickCardOffer(values: Map<String, String>): CardData? {
    val number = values["number"].orEmpty()
    if (!luhnValid(number)) return null
    val expiry = values["expiry"]?.let { parseExpiry(it) }
        ?: run {
            val m = values["expMonth"]?.toIntOrNull()
            val y = values["expYear"]?.toIntOrNull()
            if (m != null && y != null && m in 1..12) {
                m to if (y < 100) 2000 + y else y
            } else null
        } ?: return null
    return CardData(
        name = values["name"].orEmpty(),
        number = number.filter { it.isDigit() },
        expMonth = expiry.first, expYear = expiry.second,
        billingAddressId = values["billingAddressId"]?.toLongOrNull()
    )
}

sealed interface CardSaveAction {
    data object Insert : CardSaveAction
    data class Update(val existing: CardEntry) : CardSaveAction
    data object AlreadySaved : CardSaveAction
}

/** What saving [offered] should do with the cards as they stand
 *  (same shape as SaveLogin.resolveSave): the same normalised
 *  number updates the existing card, and does nothing at all
 *  when name + expiry already match. [decrypt] is injected so
 *  the check stays JVM-testable. */
fun resolveCardSave(
    existing: List<CardEntry>,
    offered: CardData,
    decrypt: (CardEntry) -> CardData?
): CardSaveAction {
    val digits = offered.number.filter { it.isDigit() }
    val match = existing.firstNotNullOfOrNull { e ->
        val d = runCatching { decrypt(e) }.getOrNull() ?: return@firstNotNullOfOrNull null
        if (d.number.filter { it.isDigit() } == digits) e to d else null
    } ?: return CardSaveAction.Insert
    val (entry, stored) = match
    return if (stored.name == offered.name &&
        stored.expMonth == offered.expMonth &&
        stored.expYear == offered.expYear
    ) CardSaveAction.AlreadySaved
    else CardSaveAction.Update(entry)
}

sealed interface AddressSaveAction {
    data object Insert : AddressSaveAction
    data class Update(val existing: AddressEntry) : AddressSaveAction
    data object AlreadySaved : AddressSaveAction
}

/** What saving [offered] under [label] should do (same shape as
 *  [resolveCardSave]): the same label + street + postal updates
 *  the existing profile, and does nothing at all when every
 *  field already matches. */
fun resolveAddressSave(
    existing: List<AddressEntry>,
    offered: AddressData,
    label: String,
    decrypt: (AddressEntry) -> AddressData?
): AddressSaveAction {
    val match = existing.firstNotNullOfOrNull { e ->
        val d = runCatching { decrypt(e) }.getOrNull() ?: return@firstNotNullOfOrNull null
        if (e.label.equals(label, ignoreCase = true) &&
            d.street1.equals(offered.street1, ignoreCase = true) &&
            d.postal.equals(offered.postal, ignoreCase = true)
        ) e to d else null
    } ?: return AddressSaveAction.Insert
    val (entry, stored) = match
    return if (stored == offered) AddressSaveAction.AlreadySaved
    else AddressSaveAction.Update(entry)
}
