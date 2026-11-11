package net.notscape.browser.vault

/**
 * Autofill field picking, kept pure and JVM-testable on purpose:
 * the service class itself extends a framework class that only
 * exists on API 26+, so unit tests must never load it. The service
 * converts AssistStructure nodes into [FieldInfo] and calls
 * [pickFields]; tests exercise the picker with fake lists.
 */
object AutofillFields {

    /** One parsed form field. */
    data class FieldInfo(
        val hints: List<String>,
        val htmlType: String?,
        val autocomplete: String?
    )

    /** Slots an address dataset can fill. */
    val ADDRESS_SLOTS = setOf(
        "name", "street1", "street2", "city", "region",
        "postal", "country", "phone", "email"
    )

    /** Slots a card dataset can fill. */
    val CARD_SLOTS = setOf("name", "number", "exp", "expMonth", "expYear")

    /** Slot -> field index for address/card fill, using the same
     *  slot names as [FormFill]. CVC fields are recognised only to
     *  be EXCLUDED — a credit-card security-code hint never becomes
     *  a fill target (D1: the CVC is never stored or filled).
     *  First field wins per slot; a field is claimed by at most
     *  one slot. */
    fun pickFormFillFields(infos: List<FieldInfo>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        val used = mutableSetOf<Int>()
        infos.forEachIndexed { i, info ->
            if (i in used) return@forEachIndexed
            val slot = slotFor(info) ?: return@forEachIndexed
            if (slot !in out) {
                out[slot] = i
                used += i
            }
        }
        return out
    }

    /** Which kinds of datasets [slots] can fill: "address" when a
     *  real address field is present, "card" when a card number is
     *  present. A bare name field offers neither — there is nothing
     *  distinctive to fill. */
    fun formFillKinds(slots: Map<String, Int>): Set<String> {
        val kinds = mutableSetOf<String>()
        if (slots.keys.any { it in ADDRESS_SLOTS && it != "name" }) kinds += "address"
        if ("number" in slots) kinds += "card"
        return kinds
    }

    private fun slotFor(info: FieldInfo): String? {
        val hintSet = info.hints.map { it.lowercase() }.toSet()
        val ac = info.autocomplete?.lowercase()?.trim()
        val type = info.htmlType?.lowercase()?.trim()
        return when {
            "creditcardnumber" in hintSet || ac == "cc-number" -> "number"
            "creditcardexpirationdate" in hintSet || ac == "cc-exp" -> "exp"
            "creditcardexpirationmonth" in hintSet || ac == "cc-exp-month" -> "expMonth"
            "creditcardexpirationyear" in hintSet || ac == "cc-exp-year" -> "expYear"
            // CVC: recognised, never a target.
            "creditcardsecuritycode" in hintSet || ac == "cc-csc" -> null
            "postaladdress" in hintSet ||
                ac == "street-address" || ac == "address-line1" -> "street1"
            ac == "address-line2" -> "street2"
            ac == "address-level2" -> "city"
            ac == "address-level1" -> "region"
            "postalcode" in hintSet || ac == "postal-code" -> "postal"
            ac == "country" || ac == "country-name" -> "country"
            "personname" in hintSet || ac == "name" || ac == "cc-name" -> "name"
            "phone" in hintSet || ac == "tel" || type == "tel" -> "phone"
            "emailaddress" in hintSet || ac == "email" || type == "email" -> "email"
            else -> null
        }
    }

    /** (username index, password index) into a field list. */
    fun pickFields(infos: List<FieldInfo>): Pair<Int?, Int?> {
        var passwordIdx: Int? = null
        var usernameIdx: Int? = null
        infos.forEachIndexed { i, info ->
            val hintSet = info.hints.map { it.lowercase() }.toSet()
            val isPassword = "password" in hintSet ||
                info.htmlType?.lowercase() == "password"
            if (isPassword && passwordIdx == null) passwordIdx = i
        }
        infos.forEachIndexed { i, info ->
            if (i == passwordIdx) return@forEachIndexed
            val hintSet = info.hints.map { it.lowercase() }.toSet()
            val ac = info.autocomplete?.lowercase()
            val isUsername = "username" in hintSet || "email" in hintSet ||
                ac == "username" || ac == "email" ||
                info.htmlType?.lowercase() == "email"
            if (isUsername && usernameIdx == null) usernameIdx = i
        }
        return usernameIdx to passwordIdx
    }
}
