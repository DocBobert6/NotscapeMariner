package net.notscape.browser.vault

import android.webkit.JavascriptInterface
import org.json.JSONObject
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
 * Deliberate limits: forms injected after page load are not
 * armed. Logins that never submit a <form> (button + fetch)
 * are seen by the fetch/XHR watch (beta.62 #23) only when the
 * request clearly carries one username + one password for
 * this site. Nothing ever auto-fills on page load; the
 * manual-tap fill design stands.
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

    /** Focus watcher (beta.62 #22): when a login field gains
     *  focus, tell the app so it can offer the vault entry for
     *  this page. Nothing fills without the user's tap — the
     *  bridge only reports the focus, and the Kotlin side
     *  re-checks the committed URL and the vault matches. A
     *  password field always counts; a text field counts only
     *  when its form shows a visible password field.
     *
     *  Beta.68 #4: the same watcher also classifies address/card
     *  fields (via FormFill's shared classifier) — but only when
     *  the field's form shows NO visible password field, because
     *  a form with a password field belongs to the login path. */
    val FOCUS_WATCH_JS: String = "(function(){\n" + FormFill.CLASSIFY_FN + """
      if (window.__nsFieldWatchInstalled) return;
      window.__nsFieldWatchInstalled = true;
      function vis(el){
        if (!el || el.disabled) return false;
        var s = window.getComputedStyle(el);
        if (s.display === 'none' || s.visibility === 'hidden') return false;
        if (el.offsetParent === null && s.position !== 'fixed') return false;
        return el.getClientRects().length > 0;
      }
      function isLoginField(el){
        if (!el || el.disabled) return false;
        var t = (el.type || '').toLowerCase();
        if (t === 'password') return vis(el);
        if (t !== 'text' && t !== 'email' && t !== 'tel' && t !== '') return false;
        if (!vis(el)) return false;
        var f = el.form;
        if (!f) return false;
        var pws = f.querySelectorAll('input[type="password"]');
        for (var i = 0; i < pws.length; i++) { if (vis(pws[i])) return true; }
        return false;
      }
      function formHasVisiblePassword(form){
        if (!form) return false;
        var pws = form.querySelectorAll('input[type="password"]');
        for (var i = 0; i < pws.length; i++) { if (vis(pws[i])) return true; }
        return false;
      }
      function formFillKind(el){
        if (!el || el.disabled) return '';
        if (formHasVisiblePassword(el.form)) return '';
        var slot = nsClassifyField(el);
        if (!slot) return '';
        if (slot === 'cardName' || slot === 'number' || slot === 'exp' ||
            slot === 'expMonth' || slot === 'expYear') return 'card';
        return 'address';
      }
      document.addEventListener('focusin', function(ev){
        try {
          var el = ev.target;
          if (!el || el.disabled) return;
          if ((el.type || '').toLowerCase() === 'password' && vis(el)) {
            window.__nsGenTarget = el;
            var ac = (el.getAttribute('autocomplete') || '').toLowerCase();
            var pwCount = 0;
            var scope = el.form || document;
            var all = scope.querySelectorAll('input[type="password"]');
            for (var i = 0; i < all.length; i++) { if (vis(all[i])) pwCount++; }
            if (ac === 'new-password' || pwCount >= 2) {
              window.notscapeSaveLogin.onFieldFocus('generate');
              return;
            }
          }
          if (isLoginField(el)) {
            window.notscapeSaveLogin.onFieldFocus('login');
            return;
          }
          var ffKind = formFillKind(el);
          if (ffKind) window.notscapeSaveLogin.onFieldFocus(ffKind);
        } catch (e) {}
      });
      document.addEventListener('focusout', function(ev){
        try { window.notscapeSaveLogin.onFieldBlur(); } catch (e) {}
      });
    })()"""

    /** Fetch/XHR login watch (beta.62 #23): logins submitted
     *  without a <form> submit (button + fetch) are now seen
     *  too. The wrapper only OBSERVES: it extracts the body's
     *  string key/value pairs and hands them to the bridge; the
     *  conservative decision — same-origin POST, exactly one
     *  username-like key and one password-like key, both
     *  non-empty — lives in [pickFetchLogin] on the Kotlin side
     *  where it is unit-testable. Any error in the wrapper falls
     *  through to the original request untouched, and bodies
     *  that are not JSON, url-encoded, FormData or
     *  URLSearchParams are never inspected. */
    const val FETCH_WATCH_JS = """(function(){
      if (window.__nsFetchWatchInstalled) return;
      window.__nsFetchWatchInstalled = true;
      function pairsFrom(body){
        var out = [];
        try {
          if (typeof body === 'string') {
            try {
              var j = JSON.parse(body);
              if (j && typeof j === 'object' && !Array.isArray(j)) {
                for (var k in j) {
                  if (typeof j[k] === 'string') out.push([k, j[k]]);
                }
                return out;
              }
            } catch (e) {}
            var sp = new URLSearchParams(body);
            sp.forEach(function(v, k){ out.push([k, v]); });
            return out;
          }
          if (body instanceof URLSearchParams) {
            body.forEach(function(v, k){ out.push([k, v]); });
            return out;
          }
          if (typeof FormData !== 'undefined' && body instanceof FormData) {
            body.forEach(function(v, k){
              if (typeof v === 'string') out.push([k, v]);
            });
            return out;
          }
        } catch (e) {}
        return out;
      }
      function looksLogin(pairs){
        var hasU = false, hasP = false;
        for (var i = 0; i < pairs.length; i++) {
          var k = pairs[i][0].toLowerCase();
          if (/^(user(name)?|login|e-?mail|account|handle)$/.test(k)) hasU = true;
          if (k.indexOf('password') >= 0 || /^(pass|passwd)$/.test(k)) hasP = true;
        }
        return hasU && hasP;
      }
      function report(method, url, body){
        try {
          var pairs = pairsFrom(body);
          if (!pairs.length || !looksLogin(pairs)) return;
          var abs = new URL(url, location.href).href;
          window.notscapeSaveLogin.onFetchPairs(
            method, abs, JSON.stringify(pairs)
          );
        } catch (e) {}
      }
      try {
        var origFetch = window.fetch;
        if (typeof origFetch === 'function') {
          window.fetch = function(input, init){
            try {
              var u = (input && typeof input === 'object' && input.url) ? input.url : input;
              var m = (init && init.method) ||
                ((input && typeof input === 'object' && input.method) || 'GET');
              var b = (init && 'body' in init) ? init.body :
                ((input && typeof input === 'object') ? input.body : undefined);
              report(m, u, b);
            } catch (e) {}
            return origFetch.apply(this, arguments);
          };
        }
      } catch (e) {}
      try {
        var origOpen = XMLHttpRequest.prototype.open;
        var origSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(m, u){
          try { this.__nsMethod = m; this.__nsUrl = u; } catch (e) {}
          return origOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.send = function(b){
          try { report(this.__nsMethod || 'GET', this.__nsUrl || location.href, b); } catch (e) {}
          return origSend.apply(this, arguments);
        };
      } catch (e) {}
    })()"""

    /** Fill script for a generated password (beta.62 #42):
     *  sets the remembered new-password field and any other
     *  visible password field in the same form (the confirm
     *  field), through the native setter with input/change
     *  events so framework forms notice. The password is
     *  JSON-quoted into the script; nothing is logged. Returns
     *  how many fields were filled, as a string. */
    fun generateFillScript(password: String): String {
        val p = JSONObject.quote(password)
        return "(function(){\n" + """
          var pw = $p;
          function setVal(el){
            try { el.focus(); } catch(e) {}
            var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
            try { el.dispatchEvent(new KeyboardEvent('keydown', {bubbles:true})); } catch(e) {}
            if (desc && desc.set) { desc.set.call(el, pw); } else { el.value = pw; }
            try { el.dispatchEvent(new InputEvent('beforeinput', {bubbles:true, inputType:'insertText', data:pw})); } catch(e) {}
            try { el.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:pw})); }
            catch(e) { el.dispatchEvent(new Event('input', {bubbles:true})); }
            try { el.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true})); } catch(e) {}
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
          var target = window.__nsGenTarget;
          var done = 0;
          if (target && target.isConnected) {
            setVal(target);
            done++;
            var f = target.form;
            if (f) {
              var pws = f.querySelectorAll('input[type="password"]');
              for (var i = 0; i < pws.length; i++) {
                if (pws[i] !== target && pws[i].offsetParent !== null) {
                  setVal(pws[i]);
                  done++;
                }
              }
            }
          }
          return '' + done;
        })()"""
    }

    /** Read the username field of the form holding the
     *  remembered new-password field, for the save offer that
     *  follows a generation. Returns the value (or '' when
     *  there is none); parse with [VaultFill.parseLocate]. */
    const val GEN_USERNAME_READ_JS = """(function(){
      var t = window.__nsGenTarget;
      if (!t || !t.isConnected) return '';
      var f = t.form;
      if (!f) return '';
      var inputs = f.querySelectorAll('input');
      for (var i = 0; i < inputs.length; i++) {
        var el = inputs[i];
        if (el === t) continue;
        var ty = (el.type || '').toLowerCase();
        if (ty !== 'text' && ty !== 'email' && ty !== 'tel' && ty !== '') continue;
        var ac = (el.getAttribute('autocomplete') || '').toLowerCase();
        if (ac === 'username' || ac === 'email') return el.value || '';
        var nm = ((el.getAttribute('name') || '') + ' ' + (el.id || '')).toLowerCase();
        if (/user|login|email/.test(nm)) return el.value || '';
      }
      return '';
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

    /** What a save of (site, username, password) should do with the
     *  vault as it stands (beta.62 #43): the autofill-service save
     *  path used to insert a fresh entry every time, duplicating the
     *  login on every save. Now it updates the existing entry for
     *  the same site + username, and does nothing at all when the
     *  stored password already matches. */
    sealed interface SaveAction {
        data object Insert : SaveAction
        data class Update(val existing: VaultEntry) : SaveAction
        data object AlreadySaved : SaveAction
    }

    fun resolveSave(
        entries: List<VaultEntry>,
        site: String,
        username: String,
        password: String,
        decrypt: (VaultEntry) -> String?
    ): SaveAction {
        val existing = findExisting(entries, site, username) ?: return SaveAction.Insert
        return if (alreadySaved(existing, password, decrypt)) SaveAction.AlreadySaved
        else SaveAction.Update(existing)
    }

    private val USER_KEY_REGEX =
        Regex("^(user(name)?|login|e-?mail|account|handle)$", RegexOption.IGNORE_CASE)
    private val PASS_KEY_REGEX =
        Regex("^pass(word)?$", RegexOption.IGNORE_CASE)

    /** Origin (scheme + host + port) of a URL, or null when it
     *  has none. Pure string work, no Android types. */
    fun originOf(url: String): String? {
        val lower = url.trim().lowercase()
        val schemeEnd = lower.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = lower.substring(0, schemeEnd)
        if (scheme != "http" && scheme != "https") return null
        var rest = lower.substring(schemeEnd + 3)
        val end = rest.indexOfAny(charArrayOf('/', '?', '#'))
        if (end >= 0) rest = rest.substring(0, end)
        val at = rest.lastIndexOf('@')
        if (at >= 0) rest = rest.substring(at + 1)
        if (rest.isBlank()) return null
        return "$scheme://$rest"
    }

    /** Conservative fetch/XHR login decision (beta.62 #23):
     *  only a same-origin POST whose body carries exactly one
     *  username-like key and exactly one password-like key,
     *  both non-empty, is a capture. Anything else — a GET, a
     *  cross-origin call, two usernames, no password — is
     *  ambiguity, and ambiguity is never captured. */
    fun pickFetchLogin(
        pairs: List<Pair<String, String>>,
        method: String,
        requestUrl: String,
        pageUrl: String
    ): Pair<String, String>? {
        if (!method.equals("POST", ignoreCase = true)) return null
        if (pairs.size > 50) return null
        val reqOrigin = originOf(requestUrl) ?: return null
        val pageOrigin = originOf(pageUrl) ?: return null
        if (reqOrigin != pageOrigin) return null
        val users = pairs.filter {
            USER_KEY_REGEX.matches(it.first) && it.second.isNotBlank()
        }
        val passes = pairs.filter {
            (PASS_KEY_REGEX.matches(it.first) ||
                it.first.lowercase().contains("password")) &&
                it.second.isNotBlank()
        }
        if (users.size != 1 || passes.size != 1) return null
        val user = users.first().second
        val pass = passes.first().second
        if (user.length > 512 || pass.length > 512) return null
        return user to pass
    }

    /** Per-site "never save" list (beta.62 #44), stored as one
     *  comma-separated setting ("vaultNeverSave") of lowercase
     *  hosts. Pure helpers so the list math is unit-testable. */
    const val NEVER_SAVE_KEY = "vaultNeverSave"

    fun parseNeverSave(raw: String): Set<String> =
        raw.split(",").map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }.toSet()

    fun withNeverSave(raw: String, host: String): String =
        (parseNeverSave(raw) + host.trim().lowercase())
            .sorted().joinToString(",")

    fun withoutNeverSave(raw: String, host: String): String =
        (parseNeverSave(raw) - host.trim().lowercase())
            .sorted().joinToString(",")

    fun isNeverSave(raw: String, host: String): Boolean =
        host.trim().lowercase() in parseNeverSave(raw)
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
    private val onLogin: (host: String, user: String, pass: String) -> Unit,
    private val onFieldFocus: ((kind: String) -> Unit)? = null,
    private val onFieldBlur: (() -> Unit)? = null,
    private val onFetchPairs: ((method: String, url: String, pairsJson: String) -> Unit)? = null,
    private val onFormSubmit: ((pairsJson: String) -> Unit)? = null
) {
    @JavascriptInterface
    fun onLogin(user: String, pass: String) {
        onLogin(armedHost, user, pass)
    }

    /** Beta.62 #22: a login field gained focus (kind 'login').
     *  Reports only — the Kotlin side decides whether to offer. */
    @JavascriptInterface
    fun onFieldFocus(kind: String) {
        onFieldFocus?.invoke(kind)
    }

    @JavascriptInterface
    fun onFieldBlur() {
        onFieldBlur?.invoke()
    }

    /** Beta.62 #23: a fetch/XHR call carried candidate login
     *  pairs (already pre-filtered in JS to look like a login).
     *  The Kotlin side applies the conservative decision. */
    @JavascriptInterface
    fun onFetchPairs(method: String, url: String, pairsJson: String) {
        onFetchPairs?.invoke(method, url, pairsJson)
    }

    /** Beta.68 #4: a form without a password field was submitted
     *  carrying recognised address/card values (already
     *  classified in JS). The Kotlin side applies the
     *  conservative capture decision and prompts. */
    @JavascriptInterface
    fun onFormSubmit(pairsJson: String) {
        onFormSubmit?.invoke(pairsJson)
    }
}
