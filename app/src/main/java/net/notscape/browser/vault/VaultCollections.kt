package net.notscape.browser.vault

import net.notscape.browser.data.AddressEntry
import net.notscape.browser.data.CardEntry
import net.notscape.browser.data.VaultEntry

/**
 * The vault collections VaultBackup reads and writes, minus the
 * Android Context AppStore needs — so the passphrase export
 * round-trip is unit-testable on the JVM with a fake. AppStore
 * implements this; its lists and add* methods already match.
 */
interface VaultCollections {
    val vault: List<VaultEntry>
    val addresses: List<AddressEntry>
    val cards: List<CardEntry>
    fun addVault(entry: VaultEntry)
    fun addAddress(entry: AddressEntry)
    fun addCard(entry: CardEntry)
}
