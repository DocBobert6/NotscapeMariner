package net.notscape.browser

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.notscape.browser.data.AddressEntity
import net.notscape.browser.data.AppDatabase
import net.notscape.browser.data.CardEntity
import net.notscape.browser.data.MIGRATION_1_2
import net.notscape.browser.data.MIGRATION_2_3
import net.notscape.browser.data.MIGRATION_3_4
import net.notscape.browser.data.VaultEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented vault tests (Phase O5): the vault's persistence layer
 * running on a real device, where the AndroidKeyStore-backed Room
 * database and the SQLite migrations actually execute. Small on
 * purpose — two tests, no UI, no network.
 *
 * Note: VaultCrypto itself is NOT covered here — its v2 key requires
 * user authentication, which no emulator can provide. The vault's
 * on-device persistence (Room tables + migrations) is what this
 * suite pins.
 */
@RunWith(AndroidJUnit4::class)
class VaultInstrumentedTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun targetContext() =
        InstrumentationRegistry.getInstrumentation().targetContext

    /** The beta.68 form-fill tables round-trip through Room on device. */
    @Test
    fun vaultTablesRoundTrip() {
        val db = Room.inMemoryDatabaseBuilder(
            targetContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val dao = db.storeDao()
            dao.insertAddresses(
                listOf(AddressEntity(1, "Home", "v3:1:AAAA"))
            )
            dao.insertCards(
                listOf(CardEntity(2, "Main card", "1111", "Visa", "v3:1:BBBB"))
            )
            dao.insertVault(
                listOf(VaultEntity(3, "example.com", "robert", "2:CCCC"))
            )
            val addresses = dao.addresses()
            assertEquals(1, addresses.size)
            assertEquals("Home", addresses[0].label)
            assertEquals("v3:1:AAAA", addresses[0].payloadEnc)
            val cards = dao.cards()
            assertEquals(1, cards.size)
            assertEquals("1111", cards[0].last4)
            assertEquals("Visa", cards[0].brand)
            val vault = dao.vault()
            assertEquals(1, vault.size)
            assertEquals("robert", vault[0].username)
        } finally {
            db.close()
        }
    }

    /** A v1 database migrates to v4: the vault row survives and the
     *  form-fill tables exist (empty). The v1 schema comes from the
     *  exported 1.json in the test assets.
     *
     *  Note: this deliberately does NOT use
     *  runMigrationsAndValidate — that helper needs the exported
     *  4.json in the test assets, and the v4 schema is only exported
     *  by the Room compiler at build time (it is not committed).
     *  Opening the migrated file with Room.databaseBuilder instead
     *  runs the same real migrations (MIGRATION_1_2/2_3/3_4) and
     *  Room itself validates the resulting schema against the v4
     *  entities on open, throwing on any mismatch. */
    @Test
    fun migrate1To4KeepsVaultAndAddsFormFillTables() {
        val name = "vault-migration-test.db"
        targetContext().deleteDatabase(name)
        migrationHelper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO vault (id, site, username, passwordEnc) " +
                    "VALUES (61, 'example.com', 'robert', '2:AAAA')"
            )
            close()
        }
        val db = Room.databaseBuilder(
            targetContext(), AppDatabase::class.java, name
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(4, db.openHelper.writableDatabase.version)
            val dao = db.storeDao()
            val vault = dao.vault()
            assertEquals(1, vault.size)
            assertEquals("example.com", vault[0].site)
            assertEquals("robert", vault[0].username)
            assertEquals(0, dao.addresses().size)
            assertEquals(0, dao.cards().size)
        } finally {
            db.close()
            targetContext().deleteDatabase(name)
        }
    }
}
