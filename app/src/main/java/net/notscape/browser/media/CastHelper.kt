package net.notscape.browser.media

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext

/**
 * Audit P-m25: Chromecast, custom player first, then page video.
 * Remote http(s) media only: a receiver cannot fetch phone-local
 * files, so local items are never offered for casting. DLNA was
 * investigated separately (see the builder report): Cling is end
 * of life (2.1.2, 2018); jUPnP 3.0.4 (Dec 2025, Maven Central)
 * is its maintained successor, but it ships no Android turnkey
 * service, so a full control point (discovery UI + AVTransport)
 * is a dedicated build, not part of this push.
 */
object CastHelper {
    fun mimeFor(url: String): String {
        // Pure string ops on purpose: this stays unit-testable
        // on the JVM, where android.net.Uri is only a stub.
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".ogg") || path.endsWith(".oga") -> "audio/ogg"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".mov") -> "video/quicktime"
            else -> "video/mp4"
        }
    }

    fun isRemote(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    fun sessionActive(context: Context): Boolean = runCatching {
        CastContext.getSharedInstance(context)
            .sessionManager.currentCastSession?.isConnected == true
    }.getOrDefault(false)

    /** Loads [url] onto the connected receiver. False when no session. */
    fun loadUrl(context: Context, url: String, title: String, positionMs: Long): Boolean {
        if (!isRemote(url)) return false
        return runCatching {
            val client = CastContext.getSharedInstance(context)
                .sessionManager.currentCastSession?.remoteMediaClient
                ?: return false
            val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
            meta.putString(MediaMetadata.KEY_TITLE, title)
            val info = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeFor(url))
                .setMetadata(meta)
                .build()
            client.load(
                MediaLoadRequestData.Builder()
                    .setMediaInfo(info)
                    .setAutoplay(true)
                    .setCurrentTime(positionMs)
                    .build()
            )
            true
        }.getOrDefault(false)
    }
}

/** True while a remote-playback route (a Cast device) is visible. */
@Composable
fun rememberCastAvailable(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    var available by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val router = MediaRouter.getInstance(context)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
        val cb = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refresh(router)
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refresh(router)
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                refresh(router)
            }

            fun refresh(router: MediaRouter) {
                available = router.routes.any {
                    it.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                }
            }
        }
        router.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        available = router.routes.any {
            it.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        }
        onDispose { router.removeCallback(cb) }
    }
    return available
}
