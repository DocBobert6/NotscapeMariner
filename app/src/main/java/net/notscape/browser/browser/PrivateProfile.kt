package net.notscape.browser.browser

import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * True private browsing: private tabs run in their own WebView profile
 * (cookies, storage and cache fully separate from the normal profile),
 * and the whole profile is deleted when the last private tab closes —
 * and again at app startup, in case the process died while private
 * tabs were open (profiles persist on disk otherwise).
 *
 * Where the device's WebView provider doesn't support multiple
 * profiles, private tabs fall back to the older behaviour: nothing is
 * written to history or the saved session.
 */
object PrivateProfile {
    private const val NAME = "notscape_private"

    fun supported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /** Assign [wv] to the private profile. Must run before the WebView
     *  loads anything — WebView creation is the only call site. */
    fun applyTo(wv: WebView) {
        if (!supported()) return
        runCatching {
            ProfileStore.getInstance().getOrCreateProfile(NAME)
            WebViewCompat.setProfile(wv, NAME)
        }
    }

    /** Delete the private profile and everything stored in it. The
     *  platform refuses while a living WebView still uses the profile,
     *  so callers destroy private WebViews first; any failure is
     *  swallowed and retried at next startup. */
    fun wipe() {
        if (!supported()) return
        runCatching { ProfileStore.getInstance().deleteProfile(NAME) }
    }
}
