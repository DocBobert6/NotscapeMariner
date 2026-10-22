package net.notscape.browser.vault

import android.net.Uri
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.VaultEntry
import org.json.JSONObject

/**
 * Filling vault passwords into login forms — only ever into this
 * browser's own WebViews, only after the user taps and authenticates,
 * and only for entries whose site matches the page's host.
 */
object VaultFill {

    /** JS probe run after a page loads: does it have a password field? */
    const val PROBE_JS =
        "(function(){return !!document.querySelector('input[type=\"password\"]')})()"

    /** Vault entries whose site matches [pageHost]. Entry sites are
     *  free text, so compare by extracted host when possible (exact or
     *  subdomain either way), else by plain containment. */
    fun matches(store: AppStore, pageHost: String): List<VaultEntry> {
        if (pageHost.isBlank()) return emptyList()
        val host = pageHost.lowercase().removePrefix("www.")
        return store.vault.filter { entry ->
            val entryHost = hostOf(entry.site)
            if (entryHost != null) {
                val eh = entryHost.lowercase().removePrefix("www.")
                eh == host || eh.endsWith(".$host") || host.endsWith(".$eh")
            } else {
                val s = entry.site.trim().lowercase()
                s.isNotBlank() && (host.contains(s) || s.contains(host))
            }
        }
    }

    private fun hostOf(site: String): String? {
        val s = site.trim()
        if (s.isBlank()) return null
        val withScheme = if (s.contains("://")) s else "https://$s"
        return Uri.parse(withScheme).host
    }

    /**
     * JS that fills the page's login form: the password field, plus the
     * best-guess username field (autocomplete/email/user-named, else
     * the closest text input before the password field). Values are set
     * through the native setter and followed by input/change events so
     * framework-driven forms notice them. Credentials are JSON-quoted.
     */
    fun fillScript(username: String, password: String): String {
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        return """
        (function(){
          function setVal(el, v){
            var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
            if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
          var pw = document.querySelector('input[type="password"]:not([disabled])');
          if (!pw) return '0';
          var user = document.querySelector('input[autocomplete="username"], input[type="email"], input[name*="user" i], input[id*="user" i], input[name*="email" i], input[id*="email" i]');
          if (!user) {
            var texts = document.querySelectorAll('input[type="text"], input[type="email"], input:not([type])');
            for (var i = 0; i < texts.length; i++) {
              var el = texts[i];
              if (el.disabled) continue;
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
