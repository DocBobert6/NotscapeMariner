package net.notscape.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.VaultEntry
import net.notscape.browser.vault.VaultBackup
import net.notscape.browser.vault.VaultCrypto

@Composable
fun VaultScreen(store: AppStore, onBack: () -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var revealedPw by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var exportPass by remember { mutableStateOf<String?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var vaultNote by remember { mutableStateOf("") }
    /** Backup export/import derive keys with 600k PBKDF2 rounds —
     *  far too heavy for the main thread — so they run on a worker
     *  thread while this is set. */
    var vaultBusy by remember { mutableStateOf(false) }

    /** The hardened vault key only works shortly after authentication.
     *  When an operation finds the window closed, fall back to the lock
     *  screen so a fresh unlock re-opens it. */
    fun relock() {
        unlocked = false
        revealedPw = emptyMap()
        authError = "For your security, unlock again to keep using the vault."
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pass = exportPass
        exportPass = null
        if (uri != null && !pass.isNullOrBlank()) {
            vaultBusy = true
            Thread {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(VaultBackup.export(store, pass).toByteArray())
                    }
                    vaultNote = "Vault exported. Keep the file and passphrase safe."
                }.onFailure {
                    if (it is VaultCrypto.AuthRequired) relock() else vaultNote = "Export failed."
                }
                vaultBusy = false
            }.start()
        }
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                pendingImportJson = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
            }
        }
    }

    fun unlock() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            unlocked = true
            return
        }
        val can = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric or device lock enrolled: the vault stays encrypted at
            // rest with a Keystore key; allow entry after an explicit tap.
            unlocked = true
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    // The authentication window is open right now — the
                    // one safe moment to move older vault records onto
                    // the authentication-bound key.
                    runCatching { VaultCrypto.migrateIfNeeded(store) }
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
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.height(12.dp))
                Text("Your vault is encrypted on this device with a key in the Android Keystore.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { unlock() }) { Text("Unlock") }
                if (authError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(authError)
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
                OutlinedButton(
                    onClick = { exportPass = "" },
                    enabled = !vaultBusy
                ) { Text("Export vault") }
                Spacer(Modifier.height(0.dp))
                OutlinedButton(
                    onClick = { importFileLauncher.launch(arrayOf("application/json")) },
                    enabled = !vaultBusy,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Import vault") }
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
            Text("No passwords saved yet.", modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(entries, key = { it.id }) { entry ->
                val shownPw = revealedPw[entry.id]
                val isShown = shownPw != null
                val password = shownPw ?: "••••••••"
                ListItem(
                    headlineContent = { Text(entry.site, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(password, maxLines = 1)
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                if (isShown) {
                                    revealedPw = revealedPw - entry.id
                                } else {
                                    runCatching { VaultCrypto.decrypt(entry.passwordEnc) }
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
                                    val plain = VaultCrypto.decrypt(entry.passwordEnc)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    // Mark the clip sensitive (Android 13+ hides
                                    // it in clipboard previews/history) and
                                    // auto-clear it shortly after, so a copied
                                    // password doesn't linger.
                                    val clip = ClipData.newPlainText(entry.site, plain)
                                    clip.description.extras = android.os.PersistableBundle().apply {
                                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                                    }
                                    cm.setPrimaryClip(clip)
                                    android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed({
                                            runCatching {
                                                val cur = cm.primaryClip
                                                val stillOurs = cur != null && cur.itemCount > 0 &&
                                                    cur.getItemAt(0).text?.toString() == plain
                                                if (stillOurs) {
                                                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                                                }
                                            }
                                        }, 45_000)
                                }.onFailure {
                                    if (it is VaultCrypto.AuthRequired) relock()
                                }
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password")
                            }
                            IconButton(onClick = { store.removeVault(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAdd) {
        var site by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (site.isNotBlank() && pass.isNotBlank()) {
                        runCatching { VaultCrypto.encrypt(pass) }
                            .onSuccess { enc ->
                                store.addVault(
                                    VaultEntry(
                                        id = System.currentTimeMillis() * 1000 + (0..999).random(),
                                        site = site.trim(),
                                        username = user.trim(),
                                        passwordEnc = enc
                                    )
                                )
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

    if (exportPass != null) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
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
        AlertDialog(
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
                        Thread {
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
                        }.start()
                    },
                    enabled = pass.isNotBlank() && !vaultBusy
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) { Text("Cancel") }
            }
        )
    }
}
