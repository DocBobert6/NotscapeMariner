package net.notscape.browser.vault

import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.notscape.browser.data.AddressEntry
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.CardEntry
import net.notscape.browser.data.VaultEntry
import net.notscape.browser.ui.NotscapeTheme

/**
 * The system-autofill authentication activity (credentials round).
 *
 * The service never serves entries while locked: it hands the user
 * here instead. This activity unlocks exactly like the vault screen
 * (biometric/device credential, or the app PIN when nothing is
 * enrolled), then either lets the user pick an entry to fill into
 * the requesting app, or confirms saving a login captured from
 * another app. The window is FLAG_SECURE, like the vault screen.
 *
 * Fill results return through the authentication-result extra, the
 * framework's documented channel — the activity never writes
 * credentials anywhere else.
 */
class VaultAutofillActivity : FragmentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_USERNAME_ID = "usernameId"
        const val EXTRA_PASSWORD_ID = "passwordId"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_FORM_FILL_SLOTS = "formFillSlots"
        const val EXTRA_FORM_FILL_IDS = "formFillIds"
        const val EXTRA_SAVE_FORM = "saveForm"
    }

    private fun autofillIdListExtra(key: String): List<AutofillId> {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(key, AutofillId::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<AutofillId>(key).orEmpty()
        }
    }

    // No two-arg getBundleExtra exists (a Bundle extra is always
    // a Bundle) — the one-arg call serves every API level.
    private fun saveFormExtra(): Bundle? {
        @Suppress("DEPRECATION")
        return intent.getBundleExtra(EXTRA_SAVE_FORM)
    }

    private fun autofillIdExtra(key: String): AutofillId? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key) as? AutofillId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val store = AppStore.get(this)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "fill"
        val usernameId = autofillIdExtra(EXTRA_USERNAME_ID)
        val passwordId = autofillIdExtra(EXTRA_PASSWORD_ID)
        val saveUsername = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        val savePassword = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val savePackage = intent.getStringExtra(EXTRA_PACKAGE) ?: "another app"
        val formFillSlots = intent.getStringArrayListExtra(EXTRA_FORM_FILL_SLOTS).orEmpty()
        val formFillSlotIds = formFillSlots.zip(autofillIdListExtra(EXTRA_FORM_FILL_IDS)).toMap()
        val saveForm = saveFormExtra()?.let { bundle ->
            bundle.keySet().associateWith { bundle.getString(it).orEmpty() }
        }.orEmpty()
        setContent {
            NotscapeTheme(store) {
                AutofillAuthScreen(
                    store = store,
                    mode = mode,
                    saveUsername = saveUsername,
                    savePackage = savePackage,
                    formFillSlots = formFillSlots,
                    saveForm = saveForm,
                    onUnlock = { AutofillUnlock.markUnlocked() },
                    onFillEntry = { entry -> finishWithEntry(entry, usernameId, passwordId) },
                    onFillFormFill = { label, values ->
                        finishWithFormFill(label, values, formFillSlotIds)
                    },
                    onSaveConfirmed = {
                        if (saveForm.isNotEmpty()) {
                            saveCapturedForm(store, savePackage, saveForm)
                        } else {
                            saveCaptured(store, savePackage, saveUsername, savePassword)
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun finishWithEntry(
        entry: VaultEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ) {
        if (usernameId == null && passwordId == null) {
            finish()
            return
        }
        val plain = runCatching {
            VaultCrypto.decrypt(entry.passwordEnc, entry.site, entry.id)
        }.getOrNull()
        if (plain == null) {
            Toast.makeText(this, "Couldn't decrypt that entry.", Toast.LENGTH_SHORT).show()
            return
        }
        val presentation =
            RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, entry.site)
            }
        val builder = DatasetBuilderCompat(presentation)
        if (usernameId != null && entry.username.isNotEmpty()) {
            builder.setValue(usernameId, AutofillValue.forText(entry.username))
        }
        if (passwordId != null) {
            builder.setValue(passwordId, AutofillValue.forText(plain))
        }
        val response = builder.buildResponse()
        if (response == null) {
            finish()
            return
        }
        setResult(
            RESULT_OK,
            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        )
        finish()
    }

    /** Beta.68 #4: fill an address/card dataset picked in the
     *  activity after unlock. Only the picked entry is decrypted
     *  (the caller did that); the values are already filtered to
     *  the requesting app's slots. */
    private fun finishWithFormFill(
        label: String,
        values: Map<String, String>,
        slotIds: Map<String, AutofillId>
    ) {
        if (values.isEmpty() || slotIds.isEmpty()) {
            finish()
            return
        }
        val presentation =
            RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, label)
            }
        val builder = DatasetBuilderCompat(presentation)
        for ((slot, v) in values) {
            val id = slotIds[slot] ?: continue
            builder.setValue(id, AutofillValue.forText(v))
        }
        val response = builder.buildResponse()
        if (response == null) {
            finish()
            return
        }
        setResult(
            RESULT_OK,
            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        )
        finish()
    }

    /** Beta.68 #4: save a card/address captured from another app.
     *  Address first, then card — the same order as the in-browser
     *  chained prompt. Each kind goes through its push-2 dedup
     *  (card by number, address by label + street + postal); the
     *  requesting package is only the display context, because
     *  cards and addresses are not site-bound. A card needs a
     *  Luhn-passing number and a parseable expiry, the same bar as
     *  the in-browser prompt — without them the card is skipped
     *  and the address (if any) still saves. */
    private fun saveCapturedForm(
        store: AppStore,
        pkg: String,
        values: Map<String, String>
    ) {
        val addressOk = runCatching { saveFormAddress(store, values) }.isSuccess
        val cardOk = runCatching { saveFormCard(store, values) }.isSuccess
        if (!addressOk || !cardOk) {
            Toast.makeText(this, "Couldn't save that address or card.", Toast.LENGTH_SHORT).show()
            return
        }
        setResult(RESULT_OK)
        finish()
    }

    private fun saveFormAddress(store: AppStore, values: Map<String, String>) {
        val data = AddressData(
            name = values["name"].orEmpty(),
            org = values["org"].orEmpty(),
            street1 = values["street1"].orEmpty(),
            street2 = values["street2"].orEmpty(),
            city = values["city"].orEmpty(),
            region = values["region"].orEmpty(),
            postal = values["postal"].orEmpty(),
            country = values["country"].orEmpty(),
            phone = values["phone"].orEmpty(),
            email = values["email"].orEmpty()
        )
        if (data.street1.isBlank() && data.postal.isBlank() && data.name.isBlank()) return
        val label = data.suggestedLabel()
        when (val action = resolveAddressSave(
            store.addresses, data, label
        ) { e ->
            AddressData.fromJsonString(
                VaultCrypto.decryptForKind(e.payloadEnc, "address", e.id)
            )
        }) {
            AddressSaveAction.AlreadySaved -> Unit
            is AddressSaveAction.Update -> {
                val enc = VaultCrypto.encryptForKind(
                    data.toJsonString(), "address", action.existing.id
                )
                store.updateAddress(action.existing.copy(label = label, payloadEnc = enc))
            }
            AddressSaveAction.Insert -> {
                val newId = System.currentTimeMillis() * 1000 + (0..999).random()
                val enc = VaultCrypto.encryptForKind(data.toJsonString(), "address", newId)
                store.addAddress(AddressEntry(newId, label, enc))
            }
        }
    }

    private fun saveFormCard(store: AppStore, values: Map<String, String>) {
        val number = values["number"].orEmpty().filter { it.isDigit() }
        if (number.isBlank() || !luhnValid(number)) return
        val (month, year) = values["exp"]?.let { parseExpiry(it) }
            ?: run {
                val m = values["expMonth"]?.filter { it.isDigit() }?.toIntOrNull()
                val y = values["expYear"]?.filter { it.isDigit() }?.toIntOrNull()
                if (m != null && y != null) m to if (y < 100) 2000 + y else y else null
            } ?: return
        if (month !in 1..12) return
        val data = CardData(
            name = values["name"].orEmpty(),
            number = number, expMonth = month, expYear = year
        )
        when (val action = resolveCardSave(
            store.cards, data
        ) { e ->
            CardData.fromJsonString(
                VaultCrypto.decryptForKind(e.payloadEnc, "card", e.id)
            )
        }) {
            CardSaveAction.AlreadySaved -> Unit
            is CardSaveAction.Update -> {
                val enc = VaultCrypto.encryptForKind(
                    data.toJsonString(), "card", action.existing.id
                )
                store.updateCard(
                    action.existing.copy(
                        payloadEnc = enc,
                        last4 = data.last4(), brand = data.brand()
                    )
                )
            }
            CardSaveAction.Insert -> {
                val newId = System.currentTimeMillis() * 1000 + (0..999).random()
                val enc = VaultCrypto.encryptForKind(data.toJsonString(), "card", newId)
                store.addCard(
                    CardEntry(newId, data.suggestedLabel(), data.last4(), data.brand(), enc)
                )
            }
        }
    }

    private fun saveCaptured(
        store: AppStore,
        pkg: String,
        username: String,
        password: String
    ) {
        // Beta.62 #43: never duplicate — update the existing entry
        // for this package + username, and skip the write entirely
        // when the stored password already matches. The update
        // re-encrypts under the EXISTING entry's site + id, because
        // the ciphertext is bound to them as GCM additional data.
        when (val action = SaveLogin.resolveSave(
            store.vault, pkg, username, password
        ) { e -> VaultCrypto.decrypt(e.passwordEnc, e.site, e.id) }) {
            SaveLogin.SaveAction.AlreadySaved -> {
                setResult(RESULT_OK)
                finish()
            }
            is SaveLogin.SaveAction.Update -> {
                val existing = action.existing
                runCatching {
                    VaultCrypto.encrypt(password, existing.site, existing.id)
                }
                    .onSuccess { enc ->
                        store.updateVault(existing.copy(passwordEnc = enc))
                        setResult(RESULT_OK)
                        finish()
                    }
                    .onFailure {
                        Toast.makeText(this, "Couldn't save that password.", Toast.LENGTH_SHORT).show()
                    }
            }
            SaveLogin.SaveAction.Insert -> {
                val newId = System.currentTimeMillis() * 1000 + (0..999).random()
                runCatching { VaultCrypto.encrypt(password, pkg, newId) }
                    .onSuccess { enc ->
                        store.addVault(VaultEntry(newId, pkg, username, enc))
                        setResult(RESULT_OK)
                        finish()
                    }
                    .onFailure {
                        Toast.makeText(this, "Couldn't save that password.", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    /** Tiny wrapper so the response build stays readable above. */
    private class DatasetBuilderCompat(presentation: RemoteViews) {
        private val builder =
            android.service.autofill.Dataset.Builder(presentation)

        fun setValue(id: AutofillId, value: AutofillValue) {
            builder.setValue(id, value)
        }

        fun buildResponse(): android.service.autofill.FillResponse? {
            return runCatching {
                android.service.autofill.FillResponse.Builder()
                    .addDataset(builder.build())
                    .build()
            }.getOrNull()
        }
    }
}

@Composable
private fun AutofillAuthScreen(
    store: AppStore,
    mode: String,
    saveUsername: String,
    savePackage: String,
    saveForm: Map<String, String> = emptyMap(),
    formFillSlots: List<String> = emptyList(),
    onUnlock: () -> Unit,
    onFillEntry: (VaultEntry) -> Unit,
    onFillFormFill: (String, Map<String, String>) -> Unit = { _, _ -> },
    onSaveConfirmed: () -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var addresses by remember { mutableStateOf<List<AddressEntry>>(emptyList()) }
    var cards by remember { mutableStateOf<List<CardEntry>>(emptyList()) }
    var saveError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun unlock() {
        if (activity == null) {
            showPin = true
            return
        }
        val can = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            showPin = true
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    unlocked = true
                    onUnlock()
                }

                override fun onAuthenticationError(
                    errorCode: Int, errString: CharSequence
                ) {
                    authError = errString.toString()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock password vault")
            .setSubtitle("Passwords stay on this device")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(unlocked) {
        if (unlocked) {
            withContext(Dispatchers.IO) { store.awaitReady(10_000) }
            entries = store.vault.toList()
            addresses = store.addresses.toList()
            cards = store.cards.toList()
        }
    }

    // Unlock once, on entry — the same gate as the vault screen.
    LaunchedEffect(Unit) { unlock() }

    Column(modifier = Modifier.padding(16.dp)) {
        if (!unlocked && showPin) {
            AutofillPinForm(
                store = store,
                onUnlocked = {
                    showPin = false
                    unlocked = true
                    onUnlock()
                },
                onCancel = onCancel
            )
        } else if (!unlocked) {
            Text(
                "Unlock the vault to continue.",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Passwords are never served to another app while the vault is locked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (authError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(authError)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = { unlock() }) { Text("Unlock") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        } else if (mode == "save" && saveForm.isNotEmpty()) {
            Text(
                "Save this address or card?",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text("App: $savePackage")
            val saveAddressLabel = run {
                val d = AddressData(
                    name = saveForm["name"].orEmpty(),
                    street1 = saveForm["street1"].orEmpty(),
                    city = saveForm["city"].orEmpty(),
                    postal = saveForm["postal"].orEmpty()
                )
                if (d.street1.isBlank() && d.postal.isBlank() && d.name.isBlank()) null
                else d.suggestedLabel()
            }
            val saveCardLabel = run {
                val digits = saveForm["number"].orEmpty().filter { it.isDigit() }
                if (digits.isBlank()) null
                else "${detectBrand(digits)} •••• ${digits.takeLast(4)}"
            }
            if (saveAddressLabel != null) Text("Address: $saveAddressLabel")
            if (saveCardLabel != null) Text("Card: $saveCardLabel")
            if (saveError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(saveError, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = onSaveConfirmed) { Text("Save") }
                TextButton(onClick = onCancel) { Text("Not now") }
            }
        } else if (mode == "save") {
            Text(
                "Save this password?",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text("App: $savePackage")
            if (saveUsername.isNotBlank()) Text("Username: $saveUsername")
            Text("Password: ••••••••")
            if (saveError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(saveError, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = onSaveConfirmed) { Text("Save") }
                TextButton(onClick = onCancel) { Text("Not now") }
            }
        } else if (formFillSlots.isNotEmpty()) {
            Text(
                "Choose an address or card to fill",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Search vault") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val shownAddresses = addresses.filter {
                search.isBlank() || it.label.contains(search, ignoreCase = true)
            }
            val shownCards = cards.filter {
                search.isBlank() || it.label.contains(search, ignoreCase = true) ||
                    it.brand.contains(search, ignoreCase = true) ||
                    it.last4.contains(search)
            }
            if (shownAddresses.isEmpty() && shownCards.isEmpty()) {
                Text("No saved addresses or cards yet.")
            } else {
                LazyColumn {
                    if (shownAddresses.isNotEmpty()) {
                        item {
                            Text(
                                "Addresses",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(shownAddresses, key = { it.id }) { entry ->
                            TextButton(
                                onClick = {
                                    val values = runCatching {
                                        val data = AddressData.fromJsonString(
                                            VaultCrypto.decryptForKind(
                                                entry.payloadEnc, "address", entry.id
                                            )
                                        )
                                        FormFill.addressValues(data)
                                            .filterKeys { it in formFillSlots }
                                    }.getOrNull()
                                    if (values.isNullOrEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Couldn't decrypt that entry.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        onFillFormFill(entry.label, values)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(entry.label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (shownCards.isNotEmpty()) {
                        item {
                            Text(
                                "Cards",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(shownCards, key = { it.id }) { entry ->
                            TextButton(
                                onClick = {
                                    val values = runCatching {
                                        val data = CardData.fromJsonString(
                                            VaultCrypto.decryptForKind(
                                                entry.payloadEnc, "card", entry.id
                                            )
                                        )
                                        FormFill.cardValues(data)
                                            .filterKeys { it in formFillSlots }
                                    }.getOrNull()
                                    if (values.isNullOrEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Couldn't decrypt that entry.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        onFillFormFill(
                                            "${entry.brand} •••• ${entry.last4}", values
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${entry.brand} •••• ${entry.last4}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        } else {
            Text(
                "Choose a login to fill",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Search vault") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val shown = entries.filter {
                search.isBlank() || it.site.contains(search, ignoreCase = true) ||
                    it.username.contains(search, ignoreCase = true)
            }
            if (shown.isEmpty()) {
                Text("No saved passwords yet.")
            } else {
                LazyColumn {
                    items(shown, key = { it.id }) { entry ->
                        TextButton(
                            onClick = { onFillEntry(entry) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(entry.site)
                                if (entry.username.isNotBlank()) {
                                    Text(
                                        entry.username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun AutofillPinForm(
    store: AppStore,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    var pinEntry by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var pinBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pinIsSet = VaultPin.isSet(store)
    Column {
        Text(
            if (pinIsSet) "Enter vault PIN" else "Set a vault PIN",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pinEntry, onValueChange = { pinEntry = it },
            label = { Text(if (pinIsSet) "PIN" else "New PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (!pinIsSet) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pinConfirm, onValueChange = { pinConfirm = it },
                label = { Text("Confirm PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (pinError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(pinError, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = {
                    if (pinBusy) return@Button
                    if (pinIsSet) {
                        pinBusy = true
                        val attempt = pinEntry
                        scope.launch(Dispatchers.IO) {
                            val ok = VaultPin.check(store, attempt)
                            scope.launch {
                                pinBusy = false
                                if (ok) onUnlocked()
                                else pinError = "Wrong PIN. Try again."
                            }
                        }
                    } else {
                        if (!VaultPin.isValidPin(pinEntry)) {
                            pinError = "PIN must be 4-32 characters."
                        } else if (pinEntry != pinConfirm) {
                            pinError = "PINs do not match."
                        } else {
                            pinBusy = true
                            val chosen = pinEntry
                            scope.launch(Dispatchers.IO) {
                                VaultPin.set(store, chosen)
                                scope.launch {
                                    pinBusy = false
                                    onUnlocked()
                                }
                            }
                        }
                    }
                },
                enabled = !pinBusy
            ) { Text(if (pinIsSet) "Unlock" else "Set PIN") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
