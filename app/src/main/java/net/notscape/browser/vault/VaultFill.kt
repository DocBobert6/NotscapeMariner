package net.notscape.browser.vault

import net.notscape.browser.browser.Hosts
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.VaultEntry
import org.json.JSONObject
import org.json.JSONTokener

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
 *
 * Delivery (Phase D rebuild): the primary path types the real
 * characters as key events through the view (see BrowserScreen),
 * which pages receive as TRUSTED input; the script below locates
 * and focuses the fields first, walking open shadow roots and
 * same-origin frames. The JS setter script remains as a fallback.
 * Closed shadow roots and cross-origin frames are hard browser-
 * security limits and are never worked around.
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

    /** Field collection + scoring, shared by the locate and setter
     *  scripts: descends into open shadow roots and same-origin
     *  iframes, then picks the password field and the username field
     *  by score (autocomplete attributes first, then type, then
     *  name/id patterns, with search/newsletter boxes penalised). */
    private const val COLLECT_JS = """
          function collectInputs(){
            var out = [];
            function fromRoot(root){
              var els = root.querySelectorAll('input');
              for (var i = 0; i < els.length; i++) out.push(els[i]);
              var all = root.querySelectorAll('*');
              for (var j = 0; j < all.length; j++) {
                if (all[j].shadowRoot) fromRoot(all[j].shadowRoot);
              }
            }
            function fromDoc(doc){
              fromRoot(doc);
              var fr = doc.querySelectorAll('iframe,frame');
              for (var i = 0; i < fr.length; i++) {
                try { var d = fr[i].contentDocument; if (d) fromDoc(d); } catch(e) {}
              }
            }
            fromDoc(document);
            return out;
          }
          function pickFields(){
            var inputs = collectInputs();
            var pw = null, pwScore = -1000000;
            for (var i = 0; i < inputs.length; i++) {
              var el = inputs[i];
              if (!vis(el)) continue;
              if ((el.type || '').toLowerCase() === 'password') {
                var sc = 10;
                var ac = (el.getAttribute('autocomplete') || '').toLowerCase();
                if (ac === 'current-password') sc += 30;
                if (ac === 'new-password') sc -= 20;
                if (el.form) sc += 5;
                if (sc > pwScore) { pwScore = sc; pw = el; }
              }
            }
            var user = null, uScore = -1000000;
            for (var j = 0; j < inputs.length; j++) {
              var e2 = inputs[j];
              if (!vis(e2) || e2 === pw) continue;
              var t = (e2.type || 'text').toLowerCase();
              if (t !== 'text' && t !== 'email' && t !== 'tel' && t !== '') continue;
              var ac2 = (e2.getAttribute('autocomplete') || '').toLowerCase();
              var nm = ((e2.getAttribute('name') || '') + ' ' + (e2.id || '') + ' ' +
                        (e2.getAttribute('aria-label') || '')).toLowerCase();
              var s2 = 0;
              if (ac2 === 'username') s2 += 50; else if (ac2 === 'email') s2 += 45;
              if (t === 'email') s2 += 35;
              if (/user|login|account|handle/.test(nm)) s2 += 25;
              if (/e-?mail/.test(nm)) s2 += 25;
              if (/search|news|subscribe|coupon|promo|chat/.test(nm)) s2 -= 60;
              if (pw && e2.form && e2.form === pw.form) s2 += 15;
              if (s2 > uScore) { uScore = s2; user = e2; }
            }
            if (user && uScore < -10) user = null;
            return { u: user, p: pw };
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

    /** Locate the login fields, remember them on window.__nsFill,
     *  focus the username field (or the password field when there
     *  is no username field), and report what was found:
     *  "u", "p", "up", or "" — the caller then types the real
     *  characters as key events into the focused field. */
    fun locateScript(): String =
        "(function(){\n" + VIS_FN + COLLECT_JS + """
          var f = pickFields();
          window.__nsFill = f;
          var target = f.u || f.p;
          if (target) { try { target.focus(); } catch(e) {} }
          return (f.u ? 'u' : '') + (f.p ? 'p' : '');
        })()"""

    /** Focus the password field remembered by [locateScript]. */
    fun focusPasswordScript(): String =
        "(function(){ var f = window.__nsFill; " +
            "if (f && f.p) { try { f.p.focus(); } catch(e) {} return '1'; } return '0'; })()"

    /** Read back what the remembered fields currently hold, as JSON. */
    fun readbackScript(): String =
        "(function(){ var f = window.__nsFill || {}; " +
            "return JSON.stringify({u: f.u ? f.u.value : null, p: f.p ? f.p.value : null}); })()"

    /** The value [locateScript] reported, from evaluateJavascript's
     *  raw (JSON-quoted) callback string. */
    fun parseLocate(raw: String?): String {
        val v = runCatching { JSONTokener(raw ?: "").nextValue() as? String }.getOrNull()
        return v ?: ""
    }

    /** (username, password) currently in the remembered fields;
     *  either may be null when the field doesn't exist. */
    fun parseReadback(raw: String?): Pair<String?, String?> {
        val text = runCatching { JSONTokener(raw ?: "").nextValue() as? String }.getOrNull()
            ?: return null to null
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return null to null
        val u = if (obj.isNull("u")) null else obj.optString("u")
        val p = if (obj.isNull("p")) null else obj.optString("p")
        return u to p
    }

    /** Vault entries whose site is the same site as [pageHost]:
     *  the entry host equals the page host, or both sit under the
     *  same registrable domain. Entries whose site text yields no
     *  host never match — they remain available in the vault itself,
     *  but are never offered for filling. */
    fun matches(store: AppStore, pageHost: String): List<VaultEntry> =
        matchesEntries(store.vault, pageHost)

    /** Pure form of [matches], for tests. */
    fun matchesEntries(entries: List<VaultEntry>, pageHost: String): List<VaultEntry> {
        if (pageHost.isBlank()) return emptyList()
        val host = pageHost.lowercase()
        return entries.filter { entry ->
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
        val schemeEnd = committedUrl.indexOf("://")
        if (schemeEnd <= 0) return false
        if (!committedUrl.substring(0, schemeEnd).equals("https", ignoreCase = true)) return false
        val pageHost = hostOf(committedUrl) ?: return false
        val entryHost = hostOf(entry.site) ?: return false
        return Hosts.sameSite(entryHost, pageHost)
    }

    /** The host an entry belongs to (for pending-fill bookkeeping). */
    fun entryHost(entry: VaultEntry): String? = hostOf(entry.site)

    /** Host part of a site string or URL, parsed without Android
     *  types so the gate logic is JVM-testable: strips scheme, path,
     *  query, fragment, userinfo and port, and lowercases. */
    fun hostOf(site: String): String? {
        var s = site.trim().lowercase()
        if (s.isBlank()) return null
        val schemeIdx = s.indexOf("://")
        if (schemeIdx >= 0) s = s.substring(schemeIdx + 3)
        val end = s.indexOfAny(charArrayOf('/', '?', '#'))
        if (end >= 0) s = s.substring(0, end)
        val at = s.lastIndexOf('@')
        if (at >= 0) s = s.substring(at + 1)
        if (s.startsWith("[")) {
            val close = s.indexOf(']')
            return if (close > 1) s.substring(1, close) else null
        }
        val colon = s.indexOf(':')
        if (colon >= 0) s = s.substring(0, colon)
        return s.ifBlank { null }
    }

    /**
     * Setter-path fallback: fill the located fields through the
     * native value setter with a full event sequence (keydown,
     * beforeinput/input as InputEvent, keyup, change) so framework-
     * driven forms notice. Returns '1' when a field was filled.
     * Credentials are JSON-quoted.
     */
    fun fillScript(username: String, password: String): String {
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        return "(function(){\n" + VIS_FN + COLLECT_JS + """
          var f = pickFields();
          window.__nsFill = f;
          if (!f.p && !f.u) return '0';
          function setVal(el, v){
            try { el.focus(); } catch(e) {}
            var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
            try { el.dispatchEvent(new KeyboardEvent('keydown', {bubbles:true})); } catch(e) {}
            if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }
            try { el.dispatchEvent(new InputEvent('beforeinput', {bubbles:true, inputType:'insertText', data:v})); } catch(e) {}
            try { el.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:v})); }
            catch(e) { el.dispatchEvent(new Event('input', {bubbles:true})); }
            try { el.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true})); } catch(e) {}
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
          var un = $u;
          if (f.u && un) setVal(f.u, un);
          if (f.p) setVal(f.p, $p);
          return '1';
        })()"""
    }
}
