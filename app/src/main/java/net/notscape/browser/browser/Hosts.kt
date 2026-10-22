package net.notscape.browser.browser

/**
 * Host comparisons on registrable-domain (eTLD+1) boundaries, so
 * "same site" means the same registrable domain — never a raw suffix
 * or substring test that a look-alike host could slip past.
 *
 * The multi-label suffix list covers the common country-code
 * second-level public suffixes (co.uk, com.au, …); it is not the
 * full public suffix list. Unknown multi-label suffixes fall back
 * to the plain last-two-labels rule.
 */
object Hosts {

    private val MULTI_LABEL_SUFFIXES = setOf(
        // United Kingdom
        "ac.uk", "co.uk", "gov.uk", "me.uk", "net.uk", "nhs.uk", "org.uk", "police.uk", "sch.uk",
        // Australia / New Zealand
        "asn.au", "com.au", "edu.au", "gov.au", "id.au", "net.au", "org.au",
        "ac.nz", "co.nz", "geek.nz", "govt.nz", "net.nz", "org.nz", "school.nz",
        // Japan / Korea
        "ac.jp", "co.jp", "go.jp", "ne.jp", "or.jp",
        "ac.kr", "co.kr", "go.kr", "ne.kr", "or.kr",
        // India
        "ac.in", "co.in", "firm.in", "gen.in", "gov.in", "ind.in", "net.in", "org.in",
        // Brazil / other Latin America
        "com.br", "gov.br", "net.br", "org.br",
        "com.ar", "com.co", "com.mx", "gob.mx", "com.pe", "com.ve",
        // China / Taiwan / Hong Kong / Singapore
        "com.cn", "gov.cn", "net.cn", "org.cn",
        "com.tw", "gov.tw", "org.tw",
        "com.hk", "gov.hk", "org.hk",
        "com.sg", "gov.sg",
        // South Africa, Israel, Turkey, and others with common second levels
        "ac.za", "co.za", "gov.za", "org.za",
        "ac.il", "co.il", "gov.il", "org.il",
        "com.tr", "gov.tr", "org.tr",
        "com.pl", "com.ua", "com.vn", "com.ph", "com.my", "com.pk", "com.ng", "com.eg",
        "com.sa", "com.ae", "com.qa", "com.bd", "com.np", "com.lk", "com.kh", "com.mm"
    )

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    /** The registrable domain (eTLD+1) of [host]. */
    fun registrableDomain(host: String): String {
        val h = host.lowercase().trimEnd('.')
        if (IPV4.matches(h)) return h
        val labels = h.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in MULTI_LABEL_SUFFIXES) {
            labels.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    /**
     * True when [a] and [b] are the same site: the same host, or
     * hosts under the same registrable domain (e.g. login.example.com
     * and www.example.com). IP addresses only ever match themselves.
     */
    fun sameSite(a: String, b: String): Boolean {
        val ha = a.lowercase().trimEnd('.')
        val hb = b.lowercase().trimEnd('.')
        if (ha == hb) return true
        if (IPV4.matches(ha) || IPV4.matches(hb)) return false
        return registrableDomain(ha) == registrableDomain(hb)
    }
}
