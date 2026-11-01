package net.notscape.browser.vault

/**
 * Chrome/Firefox-style password CSV import. Pure logic, JVM-testable,
 * and deliberately silent: it never logs, and the parsed rows are
 * handed straight to the vault's encryption — no secret ever passes
 * through a log line here or in the caller.
 *
 * Header mapping is tolerant of the common variants:
 *   site:     url, website, site, name (Chrome exports "name" for the
 *             site label and "url" for the address — url wins when
 *             both are present)
 *   username: username, user, login, email
 *   password: password, pass
 * Rows are skipped (and counted) when the column count does not
 * match the header, when the site or password is blank, or when the
 * header itself names no usable site/password column. Quoted fields
 * may contain commas, escaped quotes, and newlines.
 */
object VaultCsvImport {

    data class Row(val site: String, val username: String, val password: String)

    data class Result(val rows: List<Row>, val skipped: Int)

    /** Split one CSV document into records of raw fields. */
    fun parseRecords(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        var fieldStarted = false
        fun endField() {
            fields.add(cur.toString())
            cur.clear()
            fieldStarted = false
        }
        fun endRecord() {
            endField()
            records.add(fields)
            fields = mutableListOf()
        }
        while (i < clean.length) {
            val c = clean[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < clean.length && clean[i + 1] == '"') {
                            cur.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        cur.append(c)
                    }
                }
                c == '"' && !fieldStarted && cur.isEmpty() -> {
                    inQuotes = true
                    fieldStarted = true
                }
                c == ',' -> endField()
                c == '\r' -> {
                    if (i + 1 < clean.length && clean[i + 1] == '\n') i++
                    endRecord()
                }
                c == '\n' -> endRecord()
                else -> {
                    cur.append(c)
                    fieldStarted = true
                }
            }
            i++
        }
        if (inQuotes || cur.isNotEmpty() || fieldStarted || fields.isNotEmpty()) {
            endRecord()
        }
        return records.filter { rec -> rec.any { it.isNotBlank() } }
    }

    fun parse(text: String): Result {
        val records = parseRecords(text)
        if (records.isEmpty()) return Result(emptyList(), 0)
        val header = records.first().map { it.trim().lowercase() }
        val siteIdx = listOf("url", "website", "site", "name")
            .firstNotNullOfOrNull { header.indexOf(it).takeIf { idx -> idx >= 0 } }
        val userIdx = listOf("username", "user", "login", "email")
            .firstNotNullOfOrNull { header.indexOf(it).takeIf { idx -> idx >= 0 } }
        val passIdx = listOf("password", "pass")
            .firstNotNullOfOrNull { header.indexOf(it).takeIf { idx -> idx >= 0 } }
        if (siteIdx == null || passIdx == null) {
            return Result(emptyList(), records.size - 1)
        }
        val rows = mutableListOf<Row>()
        var skipped = 0
        for (rec in records.drop(1)) {
            if (rec.size != header.size) {
                skipped++
                continue
            }
            val site = rec[siteIdx].trim()
            val user = if (userIdx != null) rec[userIdx].trim() else ""
            val pass = rec[passIdx].trim()
            if (site.isBlank() || pass.isBlank()) {
                skipped++
                continue
            }
            rows.add(Row(site, user, pass))
        }
        return Result(rows, skipped)
    }
}
