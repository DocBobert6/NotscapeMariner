package net.notscape.browser.vault

import android.webkit.JavascriptInterface
import net.notscape.browser.data.VaultEntry

/**
 * "Save this password?" capture (credentials round).
 *
 * How it works: after a page finishes loading, the browser arms the
 * page only when it shows a login form, only on HTTPS, and never on
 * a private tab. The script listens for real form submits and hands
 * the typed username/password to the bridge — nothing is saved, and
 * no prompt appears, until the user taps Save in the dialog. The
 * dialog shows the host taken from the WebView's committed URL on
 * the Kotlin side, never a value the page supplied, so a page script
 * calling the bridge directly cannot spoof a different site.
 *
 * Deliberate limits: forms injected after page load are not armed,
 * and logins that never submit a <form> (button + fetch) are not
 * seen — those still reach the vault by manual entry. Nothing ever
 * auto-fills on page load; the manual-tap fill design stands.
 */
object SaveLogin {

    const val BRIDGE = "notscapeSaveLogin"

    /** Arm every not-yet-armed form on the page. */
    const val ARM_JS = """(function(){
      function vis(el){
        if (!el || el.disabled) return false;
        var s = window.getComputedStyle(el);
        if (s.display === 'none' || s.visibility === 'hidden') return false;
        if (el.offsetParent === null && s.position !== 'fixed') return false;
        return el.getClientRects().length > 0;
      }
      var forms = document.querySelectorAll('form');
      for (var fi = 0; fi < forms.length; fi++) {
        var f = forms[fi];
        if (f.__nsSaveArmed) continue;
        f.__nsSaveArmed = true;
        f.addEventListener('submit', function(ev){
          var form = ev.target;
          var pw = null, user = null, uScore = -1000000;
          var inputs = form.querySelectorAll('input');
          for (var i = 0; i < inputs.length; i++) {
            var el = inputs[i];
            if (!vis(el)) continue;
            if ((el.type || '').toLowerCase() === 'password') { pw = el; break; }
          }
          if (!pw) return;
          for (var j = 0; j < inputs.length; j++) {
            var e2 = inputs[j];
            if (!vis(e2) || e2 === pw) continue;
            var t = (e2.type || 'text').toLowerCase();
            if (t !== 'text' && t !== 'email' && t !== 'tel' && t !== '') continue;
            var ac = (e2.getAttribute('autocomplete') || '').toLowerCase();
            var nm = ((e2.getAttribute('name') || '') + ' ' + (e2.id || '')).toLowerCase();
            var s2 = 0;
            if (ac === 'username') s2 += 50; else if (ac === 'email') s2 += 45;
            if (t === 'email') s2 += 35;
            if (/user|login|account/.test(nm)) s2 += 25;
            if (/search|news|subscribe/.test(nm)) s2 -= 60;
            if (s2 > uScore) { uScore = s2; user = e2; }
          }
          if (user && uScore < -10) user = null;
          try {
            window.notscapeSaveLogin.onLogin(
              user ? user.value : '', pw.value
            );
          } catch (e) {}
        });
      }
    })()"""

    data class Offer(
        val host: String,
        val username: String,
        val password: String,
        /** Non-null when an entry for this host + username already
         *  exists: Save updates it instead of adding a duplicate. */
        val existingId: Long? = null,
        /** The existing entry's stored site text, kept on update so
         *  an edit does not silently rewrite how the entry displays
         *  (the ciphertext is bound to site + id as AAD). */
        val existingSite: String? = null
    )

    /** Whether a capture may even be offered for this page state. */
    fun offerable(committedUrl: String?, isPrivate: Boolean, hasLoginForm: Boolean): Boolean {
        if (isPrivate || !hasLoginForm) return false
        if (committedUrl.isNullOrBlank()) return false
        return committedUrl.lowercase().startsWith("https://")
    }

    /** The entry already stored for this host + username, if any. */
    fun findExisting(entries: List<VaultEntry>, host: String, username: String): VaultEntry? =
        entries.firstOrNull { entry ->
            VaultFill.hostOf(entry.site) == host.lowercase() && entry.username == username
        }

    /** True when the stored entry already holds exactly this
     *  password — no prompt is needed at all. [decrypt] is injected
     *  so the check stays JVM-testable. */
    fun alreadySaved(
        entry: VaultEntry?,
        password: String,
        decrypt: (VaultEntry) -> String?
    ): Boolean {
        if (entry == null) return false
        return runCatching { decrypt(entry) }.getOrNull() == password
    }
}

/** JS bridge for the save prompt. The bridge is created per armed
 *  page with that page's host baked in, so a capture always files
 *  under the login page's host — even when the submit itself POSTs
 *  cross-host and the WebView has already navigated by the time the
 *  callback reaches the UI thread. The callback runs on a WebView
 *  thread — the caller must hop to the main thread and re-verify
 *  the tab's privacy and login-form state before acting. */
class SaveLoginBridge(
    private val armedHost: String,
    private val onLogin: (host: String, user: String, pass: String) -> Unit
) {
    @JavascriptInterface
    fun onLogin(user: String, pass: String) {
        onLogin(armedHost, user, pass)
    }
}
