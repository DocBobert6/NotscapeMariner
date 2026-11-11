package net.notscape.browser.vault

import net.notscape.browser.data.AddressEntry
import net.notscape.browser.data.CardEntry
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Filling vault addresses and cards into checkout forms — only
 * ever into this browser's own WebViews, only after the user taps
 * and authenticates, and only onto the page the WebView has
 * actually committed.
 *
 * The rules, enforced against the committed URL (wv.url) at the
 * moment of injection — never against the tab's intended URL:
 * the page must be HTTPS. Unlike passwords, cards and addresses
 * are NOT site-bound (one card is used on many sites), so there
 * is no per-site matching — the gate is HTTPS + normal tab +
 * committed URL re-checked after the biometric prompt.
 * Only visible form fields are probed or filled, so a hidden
 * decoy field can never receive a value.
 *
 * Delivery mirrors VaultFill: the primary path types the real
 * characters as key events through the view (see BrowserScreen),
 * which pages receive as TRUSTED input; the script below locates
 * and focuses the fields first, walking open shadow roots and
 * same-origin frames. The JS setter script remains as a fallback.
 * Selects (country/state, expiry month/year) cannot take key
 * events, so they are always set through the setter path.
 * Closed shadow roots and cross-origin frames are hard browser-
 * security limits and are never worked around.
 *
 * The CVC is never read, never filled, never stored — the
 * classifier below returns '' for every CVC-shaped field, so no
 * script in this file ever sees one.
 */
object FormFill {

    /** Per-site "never save" list for form fill (beta.68 #4),
     *  separate from the password list so the two never interact.
     *  Same pure helpers as SaveLogin (parse/with/without/is). */
    const val NEVER_SAVE_KEY = "formFillNeverSave"

    /** Save-prompt toggles (D4: both ON by default, matching the
     *  login save prompt, which has no off switch beyond per-site
     *  Never). */
    const val OFFER_ADDRESSES_KEY = "formFillOfferAddresses"
    const val OFFER_CARDS_KEY = "formFillOfferCards"

    private const val VIS_FN = """
          function vis(el){
            if (!el || el.disabled) return false;
            var s = window.getComputedStyle(el);
            if (s.display === 'none' || s.visibility === 'hidden') return false;
            if (el.offsetParent === null && s.position !== 'fixed') return false;
            return el.getClientRects().length > 0;
          }
    """

    /** Shared field classifier (beta.68 #4): the slot a visible
     *  form field fills, or '' when it is not a form-fill field.
     *  Autocomplete attributes are authoritative; name/id patterns
     *  are the fallback, in the same scoring style as the login
     *  picker. A form that contains a visible password field
     *  belongs to the login path — callers check that first and
     *  never call this on such a form. CVC-shaped fields return ''
     *  always: the value is never read, even to decide. */
    const val CLASSIFY_FN = """
          function nsClassifyField(el){
            if (!el || el.disabled) return '';
            var tag = (el.tagName || '').toUpperCase();
            var isSelect = tag === 'SELECT';
            var isArea = tag === 'TEXTAREA';
            if (tag !== 'INPUT' && !isSelect && !isArea) return '';
            var t = (el.type || '').toLowerCase();
            if (!isSelect && !isArea) {
              if (t === 'password' || t === 'hidden' || t === 'submit' ||
                  t === 'button' || t === 'checkbox' || t === 'radio' ||
                  t === 'file' || t === 'date' || t === 'color' ||
                  t === 'range') return '';
            }
            var ac = (el.getAttribute('autocomplete') || '').toLowerCase().trim();
            if (ac === 'cc-csc') return '';
            var nm = ((el.getAttribute('name') || '') + ' ' + (el.id || '')).toLowerCase();
            switch (ac) {
              case 'cc-number': return 'number';
              case 'cc-exp': return 'exp';
              case 'cc-exp-month': return 'expMonth';
              case 'cc-exp-year': return 'expYear';
              case 'cc-name': return 'cardName';
              case 'name': return 'name';
              case 'organization': return 'org';
              case 'street-address': case 'address-line1': return 'street1';
              case 'address-line2': case 'address-line3': return 'street2';
              case 'address-level1': return 'region';
              case 'address-level2': return 'city';
              case 'postal-code': return 'postal';
              case 'country': case 'country-name': return 'country';
              case 'tel': case 'tel-national': return 'phone';
              case 'email': return 'email';
            }
            if (/cc[-_ ]?csc|cvv|cvc|security[-_ ]?code/.test(nm)) return '';
            if (/cc[-_ ]?number|card[-_ ]?number|cardnumber/.test(nm)) return 'number';
            if (/cc[-_ ]?name|card[-_ ]?(holder|name)|name[-_ ]?on[-_ ]?card/.test(nm)) return 'cardName';
            if (/exp/.test(nm)) {
              if (/month/.test(nm)) return 'expMonth';
              if (/year/.test(nm)) return 'expYear';
              return 'exp';
            }
            if (/zip|postal/.test(nm)) return 'postal';
            if (/street|address[-_ ]?line[-_ ]?1|addr[-_ ]?1/.test(nm)) return 'street1';
            if (/address[-_ ]?line[-_ ]?2|addr[-_ ]?2|apt|suite|unit/.test(nm)) return 'street2';
            if (/address/.test(nm)) return 'street1';
            if (/city|town/.test(nm)) return 'city';
            if (/state|province|region/.test(nm)) return 'region';
            if (/country/.test(nm)) return 'country';
            if (/phone|tel|mobile/.test(nm)) return 'phone';
            if (/organization|company/.test(nm)) return 'org';
            if (/e-?mail/.test(nm)) return 'email';
            if (/user(name)?|login|account/.test(nm)) return '';
            if (/full[-_ ]?name|^name$|name/.test(nm)) return 'name';
            if (t === 'email') return 'email';
            if (t === 'tel') return 'phone';
            if (t === 'month') return 'exp';
            return '';
          }
          function nsIsAuto(el){
            var ac = (el.getAttribute('autocomplete') || '').toLowerCase().trim();
            return ac === 'cc-number' || ac === 'cc-exp' || ac === 'cc-exp-month' ||
              ac === 'cc-exp-year' || ac === 'cc-name' || ac === 'name' ||
              ac === 'organization' || ac === 'street-address' ||
              ac === 'address-line1' || ac === 'address-line2' ||
              ac === 'address-line3' || ac === 'address-level1' ||
              ac === 'address-level2' || ac === 'postal-code' ||
              ac === 'country' || ac === 'country-name' ||
              ac === 'tel' || ac === 'tel-national' || ac === 'email';
          }
    """

    /** Field collection, shared by the locate and setter scripts:
     *  descends into open shadow roots and same-origin iframes,
     *  collecting inputs, selects and textareas (checkout forms
     *  use selects for country/state and expiry). */
    private const val COLLECT_JS = """
          function nsCollectFormInputs(){
            var out = [];
            function fromRoot(root){
              var els = root.querySelectorAll('input,select,textarea');
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
          function nsPickFormFields(kind){
            var slots = kind === 'card'
              ? ['cardName','number','exp','expMonth','expYear']
              : ['name','org','street1','street2','city','region','postal','country','phone','email'];
            var picked = {};
            var inputs = nsCollectFormInputs();
            for (var i = 0; i < inputs.length; i++) {
              var el = inputs[i];
              if (!vis(el)) continue;
              var slot = nsClassifyField(el);
              if (!slot || slots.indexOf(slot) < 0) continue;
              var auto = nsIsAuto(el);
              var cur = picked[slot];
              if (!cur || (auto && !cur.auto)) picked[slot] = {el: el, auto: auto};
            }
            return picked;
          }
          function nsSetVal(el, v){
            try { el.focus(); } catch(e) {}
            var tag = (el.tagName || '').toUpperCase();
            if (tag === 'SELECT') {
              var opts = el.options;
              var want = ('' + v).toLowerCase();
              var num = parseInt(v, 10);
              for (var i = 0; i < opts.length; i++) {
                var ov = (opts[i].value || '').toLowerCase();
                var ot = (opts[i].text || '').toLowerCase();
                if (ov === want || ot === want) { el.value = opts[i].value; break; }
                if (!isNaN(num) && parseInt(opts[i].value, 10) === num) { el.value = opts[i].value; break; }
                if (want.length === 4 && ov === want.substring(2)) { el.value = opts[i].value; break; }
              }
              el.dispatchEvent(new Event('input', {bubbles:true}));
              el.dispatchEvent(new Event('change', {bubbles:true}));
              return;
            }
            var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
            try { el.dispatchEvent(new KeyboardEvent('keydown', {bubbles:true})); } catch(e) {}
            if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }
            try { el.dispatchEvent(new InputEvent('beforeinput', {bubbles:true, inputType:'insertText', data:v})); } catch(e) {}
            try { el.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:v})); }
            catch(e) { el.dispatchEvent(new Event('input', {bubbles:true})); }
            try { el.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true})); } catch(e) {}
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
    """

    /** Submit listener (beta.68 #4): arms every not-yet-armed
     *  form on the page. On submit it collects the recognised
     *  address/card values and hands them to the bridge — nothing
     *  is saved, and no prompt appears, until the user taps Save
     *  in the dialog. Forms with a visible password field are
     *  skipped: the login path owns those. The CVC field's value
     *  is never read — the classifier returns '' for it, so it
     *  never reaches the collected map. */
    val FORM_ARM_JS: String =
        "(function(){\n" + VIS_FN + CLASSIFY_FN + """
          if (window.__nsFormArmInstalled) return;
          window.__nsFormArmInstalled = true;
          function formHasPassword(form){
            var pws = form.querySelectorAll('input[type="password"]');
            for (var i = 0; i < pws.length; i++) { if (vis(pws[i])) return true; }
            return false;
          }
          function armForm(f){
            if (f.__nsFormArmed) return;
            f.__nsFormArmed = true;
            f.addEventListener('submit', function(ev){
              var form = ev.target;
              if (formHasPassword(form)) return;
              var out = {};
              var fields = form.querySelectorAll('input,select,textarea');
              for (var i = 0; i < fields.length; i++) {
                var el = fields[i];
                if (!vis(el)) continue;
                var slot = nsClassifyField(el);
                if (!slot) continue;
                var v = (el.value || '').trim();
                if (!v) continue;
                if (!(slot in out)) out[slot] = v;
              }
              try { window.notscapeSaveLogin.onFormSubmit(JSON.stringify(out)); } catch (e) {}
            });
          }
          var forms = document.querySelectorAll('form');
          for (var fi = 0; fi < forms.length; fi++) armForm(forms[fi]);
        })()"""

    /** Locate the fields for [kind] ("address" | "card"),
     *  remember them on window.__nsFormFill, focus the first
     *  field, and report what was found as JSON:
     *  {"slots": {"number": "INPUT", "country": "SELECT"}}.
     *  The tag tells the caller whether to type (INPUT/TEXTAREA)
     *  or set through the setter path (SELECT). */
    fun locateScript(kind: String): String =
        "(function(){\n" + VIS_FN + CLASSIFY_FN + COLLECT_JS + """
          var kind = ${JSONObject.quote(kind)};
          var picked = nsPickFormFields(kind);
          window.__nsFormFill = {kind: kind, fields: picked};
          var slots = {};
          var first = null;
          var order = kind === 'card'
            ? ['cardName','number','exp','expMonth','expYear']
            : ['name','org','street1','street2','city','region','postal','country','phone','email'];
          for (var i = 0; i < order.length; i++) {
            var s = order[i];
            if (picked[s]) {
              slots[s] = (picked[s].el.tagName || '').toUpperCase();
              if (!first) first = picked[s].el;
            }
          }
          if (first) { try { first.focus(); } catch(e) {} }
          return JSON.stringify({slots: slots});
        })()"""

    /** Focus the remembered field for [slot]. Returns '1' when
     *  the field exists and was focused, '0' otherwise. */
    fun focusFieldScript(slot: String): String =
        "(function(){ var f = window.__nsFormFill; " +
            "if (f && f.fields && f.fields[${JSONObject.quote(slot)}]) " +
            "{ try { f.fields[${JSONObject.quote(slot)}].el.focus(); } catch(e) {} return '1'; } " +
            "return '0'; })()"

    /** Read back what the remembered fields currently hold, as
     *  JSON: {"number": "4111 ...", "country": "US"}. */
    fun readbackScript(): String =
        "(function(){ var f = window.__nsFormFill || {}; var fields = f.fields || {}; " +
            "var out = {}; for (var k in fields) { out[k] = fields[k].el.value; } " +
            "return JSON.stringify(out); })()"

    /** Setter-path fallback: fill [values] (slot -> value) into
     *  the page's fields for [kind], re-picking them first so
     *  the call does not depend on a prior locate. Returns how
     *  many fields were filled, as a string. Values are
     *  JSON-quoted into the script; nothing is logged. */
    fun fillScript(kind: String, values: Map<String, String>): String {
        val vals = JSONObject().apply {
            put("kind", kind)
            val obj = JSONObject()
            values.forEach { (k, v) -> obj.put(k, v) }
            put("values", obj)
        }.toString()
        return "(function(){\n" + VIS_FN + CLASSIFY_FN + COLLECT_JS + """
          var vals = $vals;
          var picked = nsPickFormFields(vals.kind);
          window.__nsFormFill = {kind: vals.kind, fields: picked};
          var done = 0;
          var vs = vals.values;
          for (var k in vs) {
            if (picked[k]) { nsSetVal(picked[k].el, vs[k]); done++; }
          }
          return '' + done;
        })()"""
    }

    /** Set one remembered field through the setter path (used
     *  for selects, which cannot take key events). Returns '1'
     *  when the field was found, '0' otherwise. */
    fun setOneScript(slot: String, value: String): String {
        val s = JSONObject.quote(slot)
        val v = JSONObject.quote(value)
        return "(function(){\n" + VIS_FN + CLASSIFY_FN + COLLECT_JS + """
          var f = window.__nsFormFill;
          if (f && f.fields && f.fields[$s]) {
            nsSetVal(f.fields[$s].el, $v);
            return '1';
          }
          return '0';
        })()"""
    }

    /** The located slots from [locateScript]'s raw (JSON-quoted)
     *  callback string: slot -> element tag ("INPUT", "SELECT",
     *  "TEXTAREA"), or empty when nothing was found. */
    fun parseLocate(raw: String?): Map<String, String> {
        val text = runCatching { JSONTokener(raw ?: "").nextValue() as? String }.getOrNull()
            ?: return emptyMap()
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val slots = obj.optJSONObject("slots") ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val keys = slots.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = slots.optString(k, "")
        }
        return out
    }

    /** The remembered fields' current values, from
     *  [readbackScript]'s raw callback string. */
    fun parseReadback(raw: String?): Map<String, String> {
        val text = runCatching { JSONTokener(raw ?: "").nextValue() as? String }.getOrNull()
            ?: return emptyMap()
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.optString(k, "")
        }
        return out
    }

    /**
     * The final gate, checked against the WebView's committed URL
     * at the moment of injection. Between the match, the biometric
     * prompt, and the chooser, the page may have navigated anywhere
     * — so nothing is decrypted or injected unless the page that is
     * actually showing is HTTPS. No site binding is invented for
     * cards/addresses: one card is used on many sites.
     */
    fun fillAllowed(committedUrl: String?): Boolean {
        if (committedUrl.isNullOrBlank()) return false
        val schemeEnd = committedUrl.indexOf("://")
        if (schemeEnd <= 0) return false
        return committedUrl.substring(0, schemeEnd).equals("https", ignoreCase = true)
    }

    /** Slot -> value pairs for an address profile, skipping
     *  blank fields. The combined-expiry slot never appears here. */
    fun addressValues(data: AddressData): Map<String, String> =
        mapOf(
            "name" to data.name, "org" to data.org,
            "street1" to data.street1, "street2" to data.street2,
            "city" to data.city, "region" to data.region,
            "postal" to data.postal, "country" to data.country,
            "phone" to data.phone, "email" to data.email
        ).filterValues { it.isNotBlank() }

    /** Slot -> value pairs for a card, skipping blank fields.
     *  The combined "exp" slot carries "MM/YY" for forms with a
     *  single expiry field; the separate month/year slots carry
     *  "MM" and "YYYY" for forms with two fields. */
    fun cardValues(data: CardData): Map<String, String> {
        val out = mutableMapOf<String, String>()
        if (data.name.isNotBlank()) out["cardName"] = data.name
        if (data.number.isNotBlank()) out["number"] = data.number
        out["exp"] = formatExpiry(data.expMonth, data.expYear)
        out["expMonth"] = "%02d".format(data.expMonth)
        out["expYear"] = data.expYear.toString()
        return out
    }

    /** Whether the page's value for [slot] matches what the
     *  vault holds: numbers and expiry compare digits-only (a
     *  page may reformat "4111111111111111" with spaces),
     *  postal/country/region/city compare case-insensitively,
     *  everything else compares trimmed. Pure, unit-testable. */
    fun valuesMatch(slot: String, expected: String, actual: String): Boolean {
        return when (slot) {
            "number", "phone", "exp" -> {
                val e = expected.filter { it.isDigit() }
                val a = actual.filter { it.isDigit() }
                e.isNotEmpty() && e == a
            }
            // A month field may hold "9" where the vault holds
            // "09"; a year field "28" where the vault holds
            // "2028". Compare as numbers, not as strings.
            "expMonth" -> {
                val e = expected.filter { it.isDigit() }.toIntOrNull()
                val a = actual.filter { it.isDigit() }.toIntOrNull()
                e != null && e == a
            }
            "expYear" -> {
                val e = expected.filter { it.isDigit() }.toIntOrNull()
                val a = actual.filter { it.isDigit() }.toIntOrNull()
                e != null && a != null && (e == a || e % 100 == a % 100)
            }
            "postal", "country", "region", "city" ->
                expected.trim().equals(actual.trim(), ignoreCase = true)
            else -> expected.trim() == actual.trim()
        }
    }

    /** A save prompt offer: one address or one card, with the
     *  existing entry when this is an update in place. */
    sealed interface FormOffer {
        val host: String
        val existingId: Long?
        val existingLabel: String?
        data class Address(
            override val host: String,
            val data: AddressData,
            override val existingId: Long? = null,
            override val existingLabel: String? = null
        ) : FormOffer
        data class Card(
            override val host: String,
            val data: CardData,
            override val existingId: Long? = null,
            override val existingLabel: String? = null
        ) : FormOffer
    }

    /** The chooser state: which kind is being filled and the
     *  entries to choose from (most-recently-used first — the
     *  store keeps filled entries at the front, D9). */
    data class FillChoices(
        val kind: String,
        val addresses: List<AddressEntry> = emptyList(),
        val cards: List<CardEntry> = emptyList()
    )
}
