package net.notscape.browser.media

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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

    fun buildPlayer(context: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val upstream = DefaultDataSource.Factory(context, http)
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            val ref = referer
            val scheme = spec.uri.scheme ?: ""
            if (ref != null && scheme.startsWith("http") &&
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
