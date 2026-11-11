package net.notscape.browser.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Shared player construction: a browser user-agent and the page's Referer on
 * every media request. Many CDNs refuse playback (403) without them, which
 * looks like a broken stream. [referer] is set when playback is launched
 * from a web page; the service and the player live in the same process.
 */
object PlayerNet {

    @Volatile
    var referer: String? = null

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    /** Process-level player cache (audit P-m1): 200MB, LRU.
     *  SimpleCache forbids two instances on one folder, so this
     *  is a singleton — both the activity player and the
     *  playback service share it. */
    @Volatile
    private var playerCache: SimpleCache? = null

    private fun cacheFor(context: Context): SimpleCache {
        playerCache?.let { return it }
        synchronized(this) {
            playerCache?.let { return it }
            val cache = SimpleCache(
                java.io.File(context.cacheDir, "player"),
                LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024)
            )
            playerCache = cache
            return cache
        }
    }

    /**
     * Beta.67 (1a): true for HLS (.m3u8) and DASH (.mpd) playlist /
     * manifest URLs — the requests that must never be served from
     * the player cache. A stale cached playlist freezes the live
     * window: the player runs out of segments it knows about and
     * reports end-of-stream on a stream that is still live.
     * Pure (no Android calls) so it is unit-testable.
     */
    fun isPlaylistUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return path.endsWith(".m3u8", ignoreCase = true) ||
            path.endsWith(".mpd", ignoreCase = true)
    }

    /**
     * Beta.67 (1a): routes playlist requests straight to the
     * network and everything else through the player cache.
     * CacheDataSource has no per-request bypass — a
     * CacheKeyFactory returning null is not one (open() hands
     * the key to the cache metadata lookup), so the routing
     * happens here, before the cache is ever consulted.
     * Segments keep the cache exactly as before.
     */
    private class PlaylistBypassDataSource(
        private val cached: DataSource,
        private val direct: DataSource
    ) : DataSource {
        private var active: DataSource? = null
        private var activeUri: Uri? = null

        override fun addTransferListener(transferListener: TransferListener) {
            cached.addTransferListener(transferListener)
            direct.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val ds = if (isPlaylistUrl(dataSpec.uri.toString())) direct else cached
            active = ds
            activeUri = dataSpec.uri
            return ds.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = activeUri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders ?: emptyMap()

        override fun close() {
            active?.close()
            active = null
        }
    }

    fun buildPlayer(context: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        // Beta.67 (1a): playlist/manifest requests bypass the
        // cache (see PlaylistBypassDataSource); segments go
        // through the cache exactly as before.
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cacheFor(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
        val upstream = DataSource.Factory {
            PlaylistBypassDataSource(
                cacheFactory.createDataSource(),
                DefaultDataSource.Factory(context, http).createDataSource()
            )
        }
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            val ref = referer
            val scheme = spec.uri.scheme ?: ""
            // Audit S-M8: credentials never ride a non-HTTPS request —
            // Referer and Cookie attach only over HTTPS.
            // Beta.58 (1): playback previously sent no WebView
            // cookies at all (only Referer + UA), while the
            // quality probe in PlayerActivity DID send them —
            // cookie-gated streams 403'd in the player while
            // playing fine in-page. CookieManager returns only
            // the cookies valid for this exact URI, so the
            // scoping is the platform's, not ours.
            if (scheme != "https") {
                spec
            } else {
                var headers = spec.httpRequestHeaders
                var changed = false
                if (ref != null && !headers.containsKey("Referer")) {
                    headers = HashMap(headers).apply { put("Referer", ref) }
                    changed = true
                }
                if (!headers.containsKey("Cookie")) {
                    val cookie = runCatching {
                        android.webkit.CookieManager.getInstance()
                            .getCookie(spec.uri.toString())
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    if (cookie != null) {
                        headers = HashMap(headers).apply { put("Cookie", cookie) }
                        changed = true
                    }
                }
                if (changed) {
                    spec.buildUpon().setHttpRequestHeaders(headers).build()
                } else {
                    spec
                }
            }
        }
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .build()
    }
}
