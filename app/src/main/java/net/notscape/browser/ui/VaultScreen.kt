package net.notscape.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import net.notscape.browser.R
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.VaultEntry
import net.notscape.browser.vault.VaultBackup
import net.notscape.browser.vault.VaultCrypto
import net.notscape.browser.vault.VaultCsvImport
import net.notscape.browser.vault.AutofillUnlock
import net.notscape.browser.vault.VaultPin

/**
 * Copy a secret to the clipboard (audit S-M6): the clip is marked
 * sensitive (Android 13+ hides it in clipboard previews/history) and
 * auto-clears 45s later, so a copied password doesn't linger. Used by
 * both the entry copy button and the generator's Copy button — the
 * generator used to set a plain, never-cleared clip.
 */
private fun copySecretToClipboard(context: android.content.Context, label: String, secret: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, secret)
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    cm.setPrimaryClip(clip)
    android.os.Handler(android.os.Looper.getMainLooper())
        .postDelayed({
            runCatching {
                val cur = cm.primaryClip
                val stillOurs = cur != null && cur.itemCount > 0 &&
                    cur.getItemAt(0).text?.toString() == secret
                if (stillOurs) {
                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }, 45_000)
}

/** Password generator for the vault add dialog: SecureRandom, lowercase
 *  always in the pool, other classes opt-in. Guarantees at least one
 *  character from each enabled class. */
private fun generatePassword(
    length: Int,
    upper: Boolean,
    digits: Boolean,
    symbols: Boolean
): String {
    val lower = "abcdefghijklmnopqrstuvwxyz"
    val classes = buildList {
        add(lower)
        if (upper) add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (digits) add("0123456789")
        if (symbols) add("!@#\$%^&*()-_=+")
    }
    val pool = classes.joinToString("")
    val rng = java.security.SecureRandom()
    val chars = CharArray(length.coerceAtLeast(classes.size)) {
        pool[rng.nextInt(pool.length)]
    }
    classes.forEach { cls ->
        chars[rng.nextInt(chars.size)] = cls[rng.nextInt(cls.length)]
    }
    return chars.concatToString()
}

@Composable
fun VaultScreen(store: AppStore, onBack: () -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }
    /** App-PIN flow (audit S-M5): shown when no biometric or device
     *  credential is enrolled, instead of unlocking on a bare tap. */
    var showPin by remember { mutableStateOf(false) }
    var pinEntry by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var pinBusy by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    /** Entry being edited in place (credentials round) + its
     *  decrypted password, held only while the dialog is open. */
    var editTarget by remember { mutableStateOf<VaultEntry?>(null) }
    var editPassword by remember { mutableStateOf("") }
    var revealedPw by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var exportPass by remember { mutableStateOf<String?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var vaultNote by remember { mutableStateOf("") }
    /** Backup export/import derive keys with 600k PBKDF2 rounds —
     *  far too heavy for the main thread — so they run on a worker
     *  thread while this is set. */
    var vaultBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    /** True while our own file picker is in front (backup export /
     *  import): the auto-relock must not fire for that round-trip. */
    var pickerOpen by remember { mutableStateOf(false) }

    /** The hardened vault key only works shortly after authentication.
     *  When an operation finds the window closed, fall back to the lock
     *  screen so a fresh unlock re-opens it. */
    fun relock() {
        unlocked = false
        revealedPw = emptyMap()
        authError = "For your security, unlock again to keep using the vault."
    }

    // Security bundle (Phase D): the vault locks itself again when
    // the user leaves this screen or the app goes to the background,
    // and while the vault is on screen the window is FLAG_SECURE —
    // no screenshots, no screen recording, no recents preview of
    // passwords. Our own file pickers are exempted from the
    // background rule (their round-trip is part of using the vault).
    val vaultLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(vaultLifecycleOwner) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && unlocked && !pickerOpen) {
                relock()
            }
        }
        vaultLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vaultLifecycleOwner.lifecycle.removeObserver(observer)
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            relock()
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        pickerOpen = false
        val pass = exportPass
        exportPass = null
        if (uri != null && !pass.isNullOrBlank()) {
            vaultBusy = true
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(VaultBackup.export(store, pass).toByteArray())
                    }
                    vaultNote = "Vault exported. Keep the file and passphrase safe."
                }.onFailure {
                    if (it is VaultCrypto.AuthRequired) relock() else vaultNote = "Export failed."
                }
                vaultBusy = false
            }
        }
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pickerOpen = false
        if (uri != null) {
            runCatching {
                pendingImportJson = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
            }
        }
    }
    /** Chrome/Firefox CSV import (credentials round): parsed on a
     *  worker thread, encrypted with the vault key, duplicates
     *  (same site + username) skipped. Only counts reach the note
     *  — row contents are never logged or displayed. */
    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pickerOpen = false
        if (uri != null && !vaultBusy) {
            vaultBusy = true
            scope.launch(Dispatchers.IO) {
                var authFailed = false
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                }.getOrNull()
                if (text == null) {
                    vaultNote = "Couldn't read that file."
                } else {
                    val parsed = VaultCsvImport.parse(text)
                    var added = 0
                    var skipped = parsed.skipped
                    for (row in parsed.rows) {
                        if (store.vault.any {
                            it.site == row.site && it.username == row.username
                        }) {
                            skipped++
                            continue
                        }
                        val newId =
                            System.currentTimeMillis() * 1000 + (0..999).random()
                        val enc = runCatching {
                            VaultCrypto.encrypt(row.password, row.site, newId)
                        }
                        if (enc.isSuccess) {
                            store.addVault(
                                VaultEntry(
                                    id = newId,
                                    site = row.site,
                                    username = row.username,
                                    passwordEnc = enc.getOrThrow()
                                )
                            )
                            added++
                        } else if (enc.exceptionOrNull() is VaultCrypto.AuthRequired) {
                            authFailed = true
                            break
                        } else {
                            skipped++
                        }
                    }
                    if (authFailed) {
                        relock()
                    } else {
                        vaultNote = "Imported $added password(s), skipped $skipped row(s)."
                    }
                }
                vaultBusy = false
            }
        }
    }

    fun unlock() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            pinEntry = ""
            pinConfirm = ""
            pinError = ""
            showPin = true
            return
        }
        val can = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric or device lock enrolled: the vault no longer
            // opens on a bare tap — it asks for an app PIN instead
            // (audit S-M5), set on first use.
            pinEntry = ""
            pinConfirm = ""
            pinError = ""
            showPin = true
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    // The system autofill service may serve entries
                    // for a short window after a vault unlock
                    // (credentials round); otherwise it must run its
                    // own unlock flow before serving anything.
                    AutofillUnlock.markUnlocked()
                    // The authentication window is open right now — the
                    // one safe moment to move older vault records onto
                    // the authentication-bound key.
                    scope.launch(Dispatchers.IO) {
                        runCatching { VaultCrypto.migrateIfNeeded(store) }
                        runCatching { VaultCrypto.migrateAadIfNeeded(store) }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
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

    SubScreen("Password vault", onBack) {
        if (!unlocked) {
            Column(modifier = Modifier.padding(16.dp)) {
                PanelCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Your vault is encrypted on this device with a key in the Android Keystore.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "If this device's key is ever lost, the entries cannot be recovered - that is by design. The encrypted export in the vault menu keeps a portable copy you control.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val hasDeviceCredential = remember {
                            BiometricManager.from(context).canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            ) == BiometricManager.BIOMETRIC_SUCCESS
                        }
                        if (!hasDeviceCredential) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This device has no screen lock or biometric enrolled, so the vault uses an app PIN instead. The PIN gates the vault screen; entries stay encrypted with the device key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { unlock() }) { Text("Unlock") }
                        if (authError.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(authError)
                        }
                    }
                }
            }
            return@SubScreen
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search vault") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add password")
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                KitPill(
                    label = "Export vault",
                    onClick = { if (!vaultBusy) exportPass = "" }
                )
                Spacer(Modifier.width(8.dp))
                KitPill(
                    label = "Import vault",
                    onClick = {
                        if (!vaultBusy) {
                            pickerOpen = true
                            importFileLauncher.launch(arrayOf("application/json"))
                        }
                    }
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                KitPill(
                    label = "Import CSV",
                    onClick = {
                        if (!vaultBusy) {
                            pickerOpen = true
                            importCsvLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain"
                                )
                            )
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                KitPill(
                    label = "Autofill in other apps",
                    onClick = {
                        // System autofill cannot be enabled from here
                        // (credentials round): this opens the system
                        // screen where the user turns it on.
                        if (android.os.Build.VERSION.SDK_INT >=
                            android.os.Build.VERSION_CODES.O
                        ) {
                            runCatching {
                                val intent =
                                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                                        .apply {
                                            data = Uri.parse(
                                                "package:${context.packageName}"
                                            )
                                        }
                                context.startActivity(intent)
                            }.onFailure {
                                vaultNote = "Open system Settings and choose Notscape as the autofill service."
                            }
                        } else {
                            vaultNote = "System autofill needs Android 8 or later."
                        }
                    }
                )
            }
            if (vaultBusy) {
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Working…")
                }
            }
            if (vaultNote.isNotBlank()) {
                Text(vaultNote, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        val entries = store.vault.filter {
            search.isBlank() || it.site.contains(search, ignoreCase = true) ||
                it.username.contains(search, ignoreCase = true)
        }
        if (entries.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("No passwords saved yet.", modifier = Modifier.padding(16.dp))
            }
        }
        if (entries.isNotEmpty()) {
            LazyColumn {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        SettingsCard {
                            entries.forEachIndexed { i, entry ->
                                if (i > 0) InsetDivider()
                                val shownPw = revealedPw[entry.id]
                                val isShown = shownPw != null
                                val password = shownPw ?: "••••••••"
                                KitGroupedRow(
                                leading = {
                                    Icon(
                                        Icons.Filled.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                    )
                                },
                                title = {
                        Text(
                            entry.site, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                                supporting = {
                        Column {
                            Text(
                                entry.username, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                password, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                                trailing = {
                        Row {
                            IconButton(onClick = {
                                if (isShown) {
                                    revealedPw = revealedPw - entry.id
                                } else {
                                    runCatching { VaultCrypto.decrypt(entry.passwordEnc, entry.site, entry.id) }
                                        .onSuccess { revealedPw = revealedPw + (entry.id to it) }
                                        .onFailure {
                                            if (it is VaultCrypto.AuthRequired) relock()
                                            else vaultNote = "Couldn't decrypt that entry."
                                        }
                                }
                            }) {
                                Icon(
                                    if (isShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Show/hide"
                                )
                            }
                            IconButton(onClick = {
                                runCatching {
                                    val plain = VaultCrypto.decrypt(entry.passwordEnc, entry.site, entry.id)
                                    copySecretToClipboard(context, entry.site, plain)
                                }.onFailure {
                                    if (it is VaultCrypto.AuthRequired) relock()
                                    // Audit F11: a failed copy used to fail
                                    // silently (or only relock on auth loss).
                                    else vaultNote = context.getString(R.string.vault_copy_failed)
                                }
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password")
                            }
                            // Audit S-m7: opt this entry into subdomain
                            // filling. Exact-host filling always applies;
                            // this switch is the only thing that extends
                            // it to the same registrable domain.
                            Switch(
                                checked = store.setting(
                                    "vaultSubdomain:${entry.id}", "false"
                                ) == "true",
                                onCheckedChange = { on ->
                                    store.putSetting(
                                        "vaultSubdomain:${entry.id}",
                                        on.toString()
                                    )
                                },
                                modifier = Modifier.semantics {
                                    contentDescription =
                                        "Also fill on subdomains of this site"
                                }
                            )
                            IconButton(onClick = {
                                runCatching {
                                    VaultCrypto.decrypt(
                                        entry.passwordEnc, entry.site, entry.id
                                    )
                                }.onSuccess { plain ->
                                    editPassword = plain
                                    editTarget = entry
                                }.onFailure {
                                    if (it is VaultCrypto.AuthRequired) relock()
                                    else vaultNote = "Couldn't decrypt that entry."
                                }
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { store.removeVault(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showAdd) {
        var site by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var genLen by remember { mutableStateOf(16) }
        var allowSubdomains by remember { mutableStateOf(false) }
        var genUpper by remember { mutableStateOf(true) }
        var genDigits by remember { mutableStateOf(true) }
        var genSymbols by remember { mutableStateOf(false) }
        FrostDialog(
            onDismissRequest = { showAdd = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Add password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = site, onValueChange = { site = it },
                        label = { Text("Site") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("Username") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Generator: fills the password field only — nothing
                    // is stored until Save is tapped. Lowercase is always
                    // in the pool; the other classes are opt-in toggles.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            "Generate:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { if (genLen > 8) genLen-- }) {
                            Text("\u2212")
                        }
                        Text(
                            "$genLen",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { if (genLen < 64) genLen++ }) {
                            Text("+")
                        }
                        TextButton(onClick = {
                            pass = generatePassword(
                                genLen, genUpper, genDigits, genSymbols
                            )
                        }) { Text("Generate") }
                        TextButton(
                            onClick = {
                                copySecretToClipboard(context, "password", pass)
                                Toast.makeText(
                                    context, "Password copied",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = pass.isNotEmpty()
                        ) { Text("Copy") }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { genUpper = !genUpper }) {
                            Text(if (genUpper) "A\u2013Z \u2713" else "A\u2013Z")
                        }
                        TextButton(onClick = { genDigits = !genDigits }) {
                            Text(if (genDigits) "0\u20139 \u2713" else "0\u20139")
                        }
                        TextButton(onClick = { genSymbols = !genSymbols }) {
                            Text(if (genSymbols) "#?! \u2713" else "#?!")
                        }
                    }
                    // Audit S-m7: opt this entry into subdomain
                    // filling (exact-host filling always applies).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Also fill on subdomains of this site",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = allowSubdomains,
                            onCheckedChange = { allowSubdomains = it },
                            modifier = Modifier.semantics {
                                contentDescription =
                                    "Also fill on subdomains of this site"
                            }
                        )
                    }
                    // Entry-time hygiene (Phase D): the invisible-
                    // character class of fill failures starts here —
                    // warn, never silently alter. The password is
                    // saved exactly as typed either way.
                    val passIssue = when {
                        pass.isNotEmpty() && pass != pass.trim() ->
                            "Starts or ends with a space — most sites reject that. Saved exactly as typed."
                        pass.any { it == '\u2018' || it == '\u2019' || it == '\u201C' || it == '\u201D' } ->
                            "Contains a curly quote — usually a typing accident. Saved exactly as typed."
                        else -> null
                    }
                    if (passIssue != null) {
                        Text(
                            passIssue,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (site.isNotBlank() && pass.isNotBlank()) {
                        // The entry id is minted BEFORE encrypting
                        // (audit S-m7): the ciphertext is bound to
                        // it as GCM additional data.
                        val newId = System.currentTimeMillis() * 1000 + (0..999).random()
                        runCatching {
                            VaultCrypto.encrypt(pass, site.trim(), newId)
                        }
                            .onSuccess { enc ->
                                store.addVault(
                                    VaultEntry(
                                        id = newId,
                                        site = site.trim(),
                                        username = user.trim(),
                                        passwordEnc = enc
                                    )
                                )
                                if (allowSubdomains) {
                                    store.putSetting(
                                        "vaultSubdomain:$newId", "true"
                                    )
                                }
                                showAdd = false
                            }
                            .onFailure {
                                showAdd = false
                                if (it is VaultCrypto.AuthRequired) relock()
                                else vaultNote = "Couldn't save that password."
                            }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }

    // Edit-in-place (credentials round): re-encrypts under the same
    // id, so the ciphertext stays bound to site + id as AAD — a site
    // change re-encrypts rather than reusing the old ciphertext.
    editTarget?.let { target ->
        var site by remember { mutableStateOf(target.site) }
        var user by remember { mutableStateOf(target.username) }
        var pass by remember { mutableStateOf(editPassword) }
        var allowSubdomains by remember {
            mutableStateOf(
                store.setting("vaultSubdomain:${target.id}", "false") == "true"
            )
        }
        FrostDialog(
            onDismissRequest = { editTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Edit password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = site, onValueChange = { site = it },
                        label = { Text("Site") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("Username") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Also fill on subdomains of this site",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = allowSubdomains,
                            onCheckedChange = { allowSubdomains = it },
                            modifier = Modifier.semantics {
                                contentDescription =
                                    "Also fill on subdomains of this site"
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (site.isNotBlank() && pass.isNotBlank()) {
                        runCatching {
                            VaultCrypto.encrypt(pass, site.trim(), target.id)
                        }.onSuccess { enc ->
                            store.updateVault(
                                target.copy(
                                    site = site.trim(),
                                    username = user.trim(),
                                    passwordEnc = enc
                                )
                            )
                            store.putSetting(
                                "vaultSubdomain:${target.id}",
                                allowSubdomains.toString()
                            )
                            revealedPw = revealedPw - target.id
                            editTarget = null
                        }.onFailure {
                            editTarget = null
                            if (it is VaultCrypto.AuthRequired) relock()
                            else vaultNote = "Couldn't save that password."
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (exportPass != null) {
        var pass by remember { mutableStateOf("") }
        FrostDialog(
            onDismissRequest = { exportPass = null },
            title = { Text("Export vault") },
            text = {
                Column {
                    Text("The export file is encrypted with a passphrase you choose (at least 10 characters). You will need the same passphrase to import it — it cannot be recovered.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportPass = pass
                        pickerOpen = true
                        exportFileLauncher.launch("notscape-vault-export.json")
                    },
                    enabled = pass.length >= 10
                ) { Text("Choose file") }
            },
            dismissButton = {
                TextButton(onClick = { exportPass = null }) { Text("Cancel") }
            }
        )
    }

    pendingImportJson?.let { json ->
        var pass by remember { mutableStateOf("") }
        FrostDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text("Import vault") },
            text = {
                Column {
                    Text("Enter the passphrase used when this vault file was exported. Entries already in your vault are skipped.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportJson = null
                        vaultBusy = true
                        scope.launch(Dispatchers.IO) {
                            var authFailed = false
                            val n = runCatching { VaultBackup.import(store, json, pass) }
                                .getOrElse {
                                    if (it is VaultCrypto.AuthRequired) authFailed = true
                                    -1
                                }
                            if (authFailed) {
                                relock()
                            } else {
                                vaultNote = if (n >= 0) "Imported $n password(s)."
                                else "Import failed — wrong passphrase or not a vault export."
                            }
                            vaultBusy = false
                        }
                    },
                    enabled = pass.isNotBlank() && !vaultBusy
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) { Text("Cancel") }
            }
        )
    }

    // App-PIN dialog (audit S-M5). PBKDF2 at 600k iterations runs on a
    // worker thread, never the main thread.
    if (showPin) {
        val pinIsSet = VaultPin.isSet(store)
        FrostDialog(
            onDismissRequest = { if (!pinBusy) showPin = false },
            title = { Text(if (pinIsSet) "Enter vault PIN" else "Set a vault PIN") },
            text = {
                Column {
                    Text(
                        if (pinIsSet) "Enter your vault PIN to unlock."
                        else "This device has no screen lock or biometric enrolled. Choose a PIN (4-32 characters) to protect the vault.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinEntry,
                        onValueChange = { pinEntry = it },
                        label = { Text(if (pinIsSet) "PIN" else "New PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!pinIsSet) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pinConfirm,
                            onValueChange = { pinConfirm = it },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinBusy) return@Button
                        if (pinIsSet) {
                            pinBusy = true
                            val attempt = pinEntry
                            scope.launch(Dispatchers.IO) {
                                val ok = VaultPin.check(store, attempt)
                                if (ok) runCatching {
                                    VaultCrypto.migrateAadIfNeeded(store)
                                }
                                scope.launch {
                                    pinBusy = false
                                    if (ok) {
                                        showPin = false
                                        pinEntry = ""
                                        pinError = ""
                                        unlocked = true
                                        AutofillUnlock.markUnlocked()
                                    } else {
                                        pinError = "Wrong PIN. Try again."
                                    }
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
                                    runCatching {
                                        VaultCrypto.migrateAadIfNeeded(store)
                                    }
                                    scope.launch {
                                        pinBusy = false
                                        showPin = false
                                        pinEntry = ""
                                        pinConfirm = ""
                                        pinError = ""
                                        unlocked = true
                                    }
                                }
                            }
                        }
                    },
                    enabled = !pinBusy
                ) { Text(if (pinIsSet) "Unlock" else "Set PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPin = false }) { Text("Cancel") }
            }
        )
    }
}
