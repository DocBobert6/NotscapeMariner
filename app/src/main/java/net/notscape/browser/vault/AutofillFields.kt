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
