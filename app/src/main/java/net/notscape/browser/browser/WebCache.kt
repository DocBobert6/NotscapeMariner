package net.notscape.browser.browser

import android.content.Context
import android.webkit.WebView
import java.lang.ref.WeakReference

/** Beta.66 #61: cache clearing done properly. Three sites
 *  used to create a throwaway WebView just to call
 *  clearCache(true) on it — never attached, garbage as
 *  soon as the call returned, so the async clear often
 *  never ran. Instead, every WebView the browser creates
 *  registers here (weakly, so nothing leaks), and [clear]
 *  clears the cache on each live one, then deletes the
 *  on-disk cache synchronously (the WebView disk cache
 *  lives under cacheDir, so this also covers WebViews
 *  that no longer exist). Call on the main thread, like
 *  every other WebView call. Returns the bytes freed
 *  from disk. */
object WebCache {
    private val live = mutableSetOf<WeakReference<WebView>>()

    /** Register a WebView at creation; dead refs are
     *  pruned on every call. */
    fun register(wv: WebView) {
        synchronized(live) {
            pruneLocked()
            live.add(WeakReference(wv))
        }
    }

    fun clear(context: Context): Long {
        synchronized(live) {
            val it = live.iterator()
            while (it.hasNext()) {
                val wv = it.next().get()
                if (wv == null) {
                    it.remove()
                } else {
                    runCatching { wv.clearCache(true) }
                }
            }
        }
        return net.notscape.browser.data.StorageInfo.clearCache(context)
    }

    private fun pruneLocked() {
        val it = live.iterator()
        while (it.hasNext()) {
            if (it.next().get() == null) it.remove()
        }
    }
}
