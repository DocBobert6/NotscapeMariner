package net.notscape.browser.media

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
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

    fun buildPlayer(context: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val upstream = CacheDataSource.Factory()
            .setCache(cacheFor(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            val ref = referer
            val scheme = spec.uri.scheme ?: ""
            // Audit S-M8: the page's Referer rides only HTTPS media
            // requests, never plain HTTP ones.
            if (ref != null && scheme == "https" &&
                !spec.httpRequestHeaders.containsKey("Referer")
            ) {
                val headers = HashMap(spec.httpRequestHeaders)
                headers["Referer"] = ref
                spec.buildUpon().setHttpRequestHeaders(headers).build()
            } else {
                spec
            }
        }
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .build()
    }
}
