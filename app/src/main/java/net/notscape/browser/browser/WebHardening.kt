package net.notscape.browser.browser

import android.os.Build
import android.webkit.WebSettings

/**
 * The WebView hardening baseline (0.0.997), applied to every
 * WebView the app creates: pages get no file or content
 * access, no geolocation, no saved form data, and no mixed
 * content; Safe Browsing is on where the platform offers it.
 * JavaScript stays a per-view choice - the browser honours
 * the per-site setting, the article view keeps it off - so
 * it is not set here. Nothing in the app registers a
 * JavaScript interface, and WebContents debugging is never
 * enabled (both verified in the 0.0.997 audit).
 */
internal fun applyWebHardening(settings: WebSettings) {
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.setGeolocationEnabled(false)
    @Suppress("DEPRECATION")
    settings.saveFormData = false
    settings.mixedContentMode =
        WebSettings.MIXED_CONTENT_NEVER_ALLOW
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = true
    }
}
