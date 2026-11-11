package net.notscape.browser.vault

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.os.Bundle
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import net.notscape.browser.data.AppStore

/**
 * System autofill (credentials round): the vault serves other apps.
 *
 * Rules, matching the in-app philosophy:
 * - Never serve entries while locked. When the vault was unlocked
 *   recently (see [AutofillUnlock]), the service returns one dataset
 *   per entry and the user picks manually from the dropdown — there
 *   is no automatic matching for native apps, because no asset-link
 *   association exists. Otherwise it returns a single "unlock" dataset
 *   whose authentication intent opens [VaultAutofillActivity]; the
 *   user unlocks and picks there, and the filled response comes back
 *   through the authentication result.
 * - Passwords are decrypted only for the entry the user picks (direct
 *   path) or after unlock in the activity (auth path), never in bulk
 *   for display.
 * - The service runs only on API 26+ (the framework never binds it
 *   earlier); the shared unlock flag lives in [AutofillUnlock] so no
 *   pre-26 code references this class.
 */
class NotscapeAutofillService : AutofillService() {

    private data class ParsedField(
        val id: AutofillId,
        val info: AutofillFields.FieldInfo,
        val textValue: String?
    )

    private fun parseStructure(structure: AssistStructure): List<ParsedField> {
        val out = mutableListOf<ParsedField>()
        fun walk(node: AssistStructure.ViewNode) {
            val id = node.autofillId
            if (id != null) {
                val hints = node.autofillHints?.toList().orEmpty()
                var htmlType: String? = null
                var autocomplete: String? = null
                val html = node.htmlInfo
                if (html != null) {
                    for (attr in html.attributes ?: emptyList()) {
                        when (attr.first.lowercase()) {
                            "type" -> htmlType = attr.second
                            "autocomplete" -> autocomplete = attr.second
                        }
                    }
                }
                val text = runCatching {
                    val v = node.autofillValue
                    if (v != null && v.isText) v.textValue?.toString() else null
                }.getOrNull()
                out.add(ParsedField(id, AutofillFields.FieldInfo(hints, htmlType, autocomplete), text))
            }
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i))
            }
        }
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode)
        }
        return out
    }

    private fun presentation(label: String): RemoteViews =
        RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
        }

    private fun authIntent(
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        mode: String,
        username: String? = null,
        password: String? = null,
        packageName: String? = null,
        formFillSlots: List<String>? = null,
        formFillIds: List<AutofillId>? = null,
        saveForm: Bundle? = null
    ): PendingIntent {
        val intent = Intent(this, VaultAutofillActivity::class.java).apply {
            putExtra(VaultAutofillActivity.EXTRA_MODE, mode)
            if (usernameId != null) putExtra(VaultAutofillActivity.EXTRA_USERNAME_ID, usernameId)
            if (passwordId != null) putExtra(VaultAutofillActivity.EXTRA_PASSWORD_ID, passwordId)
            if (username != null) putExtra(VaultAutofillActivity.EXTRA_USERNAME, username)
            if (password != null) putExtra(VaultAutofillActivity.EXTRA_PASSWORD, password)
            if (packageName != null) putExtra(VaultAutofillActivity.EXTRA_PACKAGE, packageName)
            if (formFillSlots != null) putStringArrayListExtra(
                VaultAutofillActivity.EXTRA_FORM_FILL_SLOTS, ArrayList(formFillSlots)
            )
            if (formFillIds != null) putParcelableArrayListExtra(
                VaultAutofillActivity.EXTRA_FORM_FILL_IDS, ArrayList(formFillIds)
            )
            if (saveForm != null) putExtra(VaultAutofillActivity.EXTRA_SAVE_FORM, saveForm)
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun authDataset(
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        formFillSlots: List<String>? = null,
        formFillIds: List<AutofillId>? = null
    ): FillResponse {
        val dataset = Dataset.Builder(presentation("Unlock Notscape vault"))
            .setAuthentication(
                authIntent(
                    usernameId, passwordId, "fill",
                    formFillSlots = formFillSlots, formFillIds = formFillIds
                ).intentSender
            )
            .build()
        return FillResponse.Builder().addDataset(dataset).build()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        if (cancellationSignal.isCanceled) return
        runCatching {
            val store = AppStore.get(this)
            store.awaitReady(5_000)
            val contexts: List<FillContext> = request.fillContexts
            val structure = contexts.lastOrNull()?.structure
                ?: return@runCatching callback.onSuccess(null)
            val fields = parseStructure(structure)
            val (usernameIdx, passwordIdx) = AutofillFields.pickFields(fields.map { it.info })
            if (usernameIdx == null && passwordIdx == null) {
                // Beta.68 #4: no login fields — an address/card
                // form. Same locked/manual-pick rules as passwords:
                // locked serves one unlock dataset; unlocked serves
                // one dataset per profile/card, and the user picks.
                val slots = AutofillFields.pickFormFillFields(fields.map { it.info })
                val kinds = AutofillFields.formFillKinds(slots)
                if (kinds.isEmpty()) {
                    return@runCatching callback.onSuccess(null)
                }
                val slotIds = slots.mapValues { (_, idx) -> fields[idx].id }
                val formFillSlotList = slots.keys.toList()
                val formFillIdList = slots.values.map { fields[it].id }
                if (!AutofillUnlock.isUnlocked()) {
                    return@runCatching callback.onSuccess(
                        authDataset(null, null, formFillSlotList, formFillIdList)
                    )
                }
                val response = FillResponse.Builder()
                if ("address" in kinds) {
                    for (entry in store.addresses) {
                        val data = runCatching {
                            AddressData.fromJsonString(
                                VaultCrypto.decryptForKind(entry.payloadEnc, "address", entry.id)
                            )
                        }.getOrElse {
                            if (it is VaultCrypto.AuthRequired) {
                                return@runCatching callback.onSuccess(
                                    authDataset(null, null, formFillSlotList, formFillIdList)
                                )
                            } else {
                                continue
                            }
                        }
                        val values = FormFill.addressValues(data)
                            .filterKeys { it in slots }
                        if (values.isEmpty()) continue
                        val builder = Dataset.Builder(presentation(entry.label))
                        for ((slot, v) in values) {
                            val id = slotIds[slot] ?: continue
                            builder.setValue(id, AutofillValue.forText(v))
                        }
                        response.addDataset(builder.build())
                    }
                }
                if ("card" in kinds) {
                    for (entry in store.cards) {
                        val data = runCatching {
                            CardData.fromJsonString(
                                VaultCrypto.decryptForKind(entry.payloadEnc, "card", entry.id)
                            )
                        }.getOrElse {
                            if (it is VaultCrypto.AuthRequired) {
                                return@runCatching callback.onSuccess(
                                    authDataset(null, null, formFillSlotList, formFillIdList)
                                )
                            } else {
                                continue
                            }
                        }
                        val values = FormFill.cardValues(data)
                            .filterKeys { it in slots }
                        if (values.isEmpty()) continue
                        val builder = Dataset.Builder(presentation(data.displayLabel()))
                        for ((slot, v) in values) {
                            val id = slotIds[slot] ?: continue
                            builder.setValue(id, AutofillValue.forText(v))
                        }
                        response.addDataset(builder.build())
                    }
                }
                return@runCatching callback.onSuccess(response.build())
            }
            val usernameId = usernameIdx?.let { fields[it].id }
            val passwordId = passwordIdx?.let { fields[it].id }
            if (!AutofillUnlock.isUnlocked()) {
                return@runCatching callback.onSuccess(authDataset(usernameId, passwordId))
            }
            // Direct path: one dataset per entry, user picks manually.
            // An authentication window that closed mid-build falls
            // back to the auth dataset rather than serving partial data.
            val response = FillResponse.Builder()
            for (entry in store.vault) {
                // A dataset with no values is invalid: skip entries
                // that would contribute nothing to these fields.
                if (passwordId == null && entry.username.isBlank()) continue
                val plain = runCatching {
                    VaultCrypto.decrypt(entry.passwordEnc, entry.site, entry.id)
                }.getOrElse {
                    if (it is VaultCrypto.AuthRequired) {
                        return@runCatching callback.onSuccess(
                            authDataset(usernameId, passwordId)
                        )
                    } else {
                        continue
                    }
                }
                val label = if (entry.username.isNotBlank()) {
                    "${entry.site} — ${entry.username}"
                } else {
                    entry.site
                }
                val builder = Dataset.Builder(presentation(label))
                if (usernameId != null && entry.username.isNotEmpty()) {
                    builder.setValue(usernameId, AutofillValue.forText(entry.username))
                }
                if (passwordId != null) {
                    builder.setValue(passwordId, AutofillValue.forText(plain))
                }
                response.addDataset(builder.build())
            }
            callback.onSuccess(response.build())
        }.onFailure {
            runCatching { callback.onFailure("Notscape autofill failed") }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        runCatching {
            val contexts = request.fillContexts
            val structure = contexts.lastOrNull()?.structure
                ?: return@runCatching callback.onSuccess()
            val fields = parseStructure(structure)
            val (usernameIdx, passwordIdx) = AutofillFields.pickFields(fields.map { it.info })
            val username = usernameIdx?.let { fields[it].textValue }
            val password = passwordIdx?.let { fields[it].textValue }
            val pkg = structure.activityComponent?.packageName
            if (!password.isNullOrBlank()) {
                return@runCatching callback.onSuccess(
                    authIntent(null, null, "save", username, password, pkg).intentSender
                )
            }
            // Beta.68 #4: no password to save — a card/address
            // form. Offer to save the typed values through the
            // same unlock activity; the dedup there is the push-2
            // one (card by number, address by label + street +
            // postal). A form carrying both a password and card
            // fields saves the password only, as above.
            val slots = AutofillFields.pickFormFillFields(fields.map { it.info })
            if (AutofillFields.formFillKinds(slots).isEmpty()) {
                return@runCatching callback.onSuccess()
            }
            val values = Bundle()
            for ((slot, idx) in slots) {
                val v = fields[idx].textValue
                if (!v.isNullOrBlank()) values.putString(slot, v)
            }
            if (values.isEmpty) {
                return@runCatching callback.onSuccess()
            }
            callback.onSuccess(
                authIntent(null, null, "save", saveForm = values, packageName = pkg).intentSender
            )
        }.onFailure {
            runCatching { callback.onFailure("Notscape autofill save failed") }
        }
    }

    // The framework calls this when the user disables the service;
    // nothing to clean up (no cached credentials anywhere here).
    override fun onConnected() = Unit
    override fun onDisconnected() = Unit
}
