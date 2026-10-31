package net.notscape.browser

import net.notscape.browser.vault.VaultCsvImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chrome/Firefox CSV import parsing — secrets never leave the parser. */
class VaultCsvImportTest {

    @Test
    fun chromeHeaders() {
        val csv = "name,url,username,password\n" +
            "Example,https://example.com,alice,s3cret\n"
        val r = VaultCsvImport.parse(csv)
        assertEquals(1, r.rows.size)
        assertEquals("https://example.com", r.rows[0].site)
        assertEquals("alice", r.rows[0].username)
        assertEquals("s3cret", r.rows[0].password)
        assertEquals(0, r.skipped)
    }

    @Test
    fun firefoxHeadersAndHeaderVariants() {
        val csv = "url,username,password,httpRealm\n" +
            "https://a.example,bob,pw1,\n"
        val r = VaultCsvImport.parse(csv)
        assertEquals(1, r.rows.size)
        assertEquals("https://a.example", r.rows[0].site)
        val alt = "website,user,pass\n" +
            "example.org,carol,pw2\n"
        val r2 = VaultCsvImport.parse(alt)
        assertEquals(1, r2.rows.size)
        assertEquals("example.org", r2.rows[0].site)
        assertEquals("carol", r2.rows[0].username)
    }

    @Test
    fun quotedCommasQuotesAndNewlines() {
        val csv = "url,username,password\n" +
            "\"https://x.example\",\"say \"\"hi\"\"\",\"pa,ss\nword\"\n"
        val r = VaultCsvImport.parse(csv)
        assertEquals(1, r.rows.size)
        assertEquals("say \"hi\"", r.rows[0].username)
        assertEquals("pa,ss\nword", r.rows[0].password)
        assertEquals(0, r.skipped)
    }

    @Test
    fun blankPasswordBlankSiteAndBadColumnCountSkipped() {
        val csv = "url,username,password\n" +
            "https://ok.example,dan,good\n" +
            "https://no-pw.example,erin,\n" +
            ",frank,good2\n" +
            "https://short.example,only-two\n"
        val r = VaultCsvImport.parse(csv)
        assertEquals(1, r.rows.size)
        assertEquals("https://ok.example", r.rows[0].site)
        assertEquals(3, r.skipped)
    }

    @Test
    fun unrecognisedHeaderSkipsDataRows() {
        val r = VaultCsvImport.parse("foo,bar\n1,2\n3,4\n")
        assertTrue(r.rows.isEmpty())
        assertEquals(2, r.skipped)
    }

    @Test
    fun bomAndCrlfTolerated() {
        val csv = "\uFEFFurl,username,password\r\n" +
            "https://b.example,gail,pw\r\n"
        val r = VaultCsvImport.parse(csv)
        assertEquals(1, r.rows.size)
        assertEquals("https://b.example", r.rows[0].site)
    }

    @Test
    fun emptyInputIsEmpty() {
        val r = VaultCsvImport.parse("")
        assertTrue(r.rows.isEmpty())
        assertEquals(0, r.skipped)
    }
}
