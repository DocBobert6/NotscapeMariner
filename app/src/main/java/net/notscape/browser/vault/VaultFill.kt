package net.notscape.browser.vault

import android.net.Uri
import net.notscape.browser.browser.Hosts
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.VaultEntry
import org.json.JSONObject

/**
 * Filling vault passwords into login forms — only ever into this
 * browser's own WebViews, only after the user taps and authenticates,
 * and only onto the page the WebView has actually committed.
 *
 * The rules, enforced against the committed URL (wv.url) at the
 * moment of injection — never against the tab's intended URL:
 * the page must be HTTPS, and its host must be the same site as the
 * entry's (exact host, or the same registrable domain / eTLD+1).
 * Only visible form fields are probed or filled, so a hidden decoy
 * field can never receive a credential.
 */
object VaultFill {

    private const val VIS_FN = """
          function vis(el){
            if (!el || el.disabled) return false;
            var s = window.getComputedStyle(el);
            if (s.display === 'none' || s.visibility === 'hidden') return false;
            if (el.offsetParent === null && s.position !== 'fixed') return false;
            return el.getClientRects().length > 0;
          }
    """

    /** JS probe run after a page loads: does it show a login form
     *  (a visible password field)? */
    const val PROBE_JS =
        "(function(){\n" + VIS_FN + """
          var pws = document.querySelectorAll('input[type="password"]');
          for (var i = 0; i < pws.length; i++) { if (vis(pws[i])) return true; }
          return false;
        })()
        """

    /** Vault entries whose site is the same site as [pageHost]:
     *  the entry host equals the page host, or both sit under the
     *  same registrable domain. Entries whose site text yields no
     *  host never match — they remain available in the vault itself,
     *  but are never offered for filling. */
    fun matches(store: AppStore, pageHost: String): List<VaultEntry> {
        if (pageHost.isBlank()) return emptyList()
        val host = pageHost.lowercase()
        return store.vault.filter { entry ->
            val entryHost = hostOf(entry.site) ?: return@filter false
            Hosts.sameSite(entryHost, host)
        }
    }

    /**
     * The final gate, checked against the WebView's committed URL at
     * the moment of injection. Between the match, the biometric
     * prompt, and the chooser, the page may have navigated anywhere —
     * so nothing is decrypted or injected unless the page that is
     * actually showing is HTTPS and the same site as the entry.
     */
    fun fillAllowed(entry: VaultEntry, committedUrl: String?): Boolean {
        if (committedUrl.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(committedUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val pageHost = uri.host ?: return false
        val entryHost = hostOf(entry.site) ?: return false
        return Hosts.sameSite(entryHost, pageHost)
    }

    private fun hostOf(site: String): String? {
        val s = site.trim()
        if (s.isBlank()) return null
        val withScheme = if (s.contains("://")) s else "https://$s"
        return runCatching { Uri.parse(withScheme).host }.getOrNull()?.lowercase()
    }

    /**
     * JS that fills the page's login form: the first visible password
     * field, plus the best-guess visible username field
     * (autocomplete/email/user-named, else the closest visible text
     * input before the password field). Hidden fields are never
     * touched. Values are set through the native setter and followed
     * by input/change events so framework-driven forms notice them.
     * Credentials are JSON-quoted.
     */
    fun fillScript(username: String, password: String): String {
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        return """
        (function(){
        $VIS_FN
          function setVal(el, v){
            var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
            if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
          var pw = null;
          var pws = document.querySelectorAll('input[type="password"]');
          for (var i = 0; i < pws.length; i++) { if (vis(pws[i])) { pw = pws[i]; break; } }
          if (!pw) return '0';
          var user = null;
          var uc = document.querySelectorAll('input[autocomplete="username"], input[type="email"], input[name*="user" i], input[id*="user" i], input[name*="email" i], input[id*="email" i]');
          for (var j = 0; j < uc.length; j++) { if (uc[j] !== pw && vis(uc[j])) { user = uc[j]; break; } }
          if (!user) {
            var texts = document.querySelectorAll('input[type="text"], input[type="email"], input:not([type])');
            for (var k = 0; k < texts.length; k++) {
              var el = texts[k];
              if (!vis(el)) continue;
              if (el.compareDocumentPosition(pw) & Node.DOCUMENT_POSITION_FOLLOWING) user = el;
            }
          }
          var un = $u;
          if (user && un) setVal(user, un);
          setVal(pw, $p);
          return '1';
        })()
        """.trimIndent()
    }
}
