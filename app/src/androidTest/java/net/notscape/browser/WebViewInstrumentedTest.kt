package net.notscape.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented WebView tests (Phase O5): a real System WebView on a
 * real device loading a local page and evaluating JavaScript. Small
 * on purpose — two tests, no network, no browser UI.
 *
 * Headless on purpose: the WebView is created directly from the
 * target context on the main thread. An ActivityScenario host does
 * NOT work here — an activity declared only in the androidTest
 * manifest lives in the test APK's process, and ActivityScenario
 * refuses to launch an activity outside the target process.
 */
@RunWith(AndroidJUnit4::class)
class WebViewInstrumentedTest {

    private fun loadAndEval(html: String, script: String): String? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val finished = CountDownLatch(1)
        var webView: WebView? = null
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        finished.countDown()
                    }
                }
                loadData(html, "text/html", "UTF-8")
            }
        }
        assertTrue(finished.await(15, TimeUnit.SECONDS))
        val result = CountDownLatch(1)
        var value: String? = null
        instrumentation.runOnMainSync {
            webView?.evaluateJavascript(script) {
                value = it
                result.countDown()
            }
        }
        assertTrue(result.await(15, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { webView?.destroy() }
        return value
    }

    /** A local page loads and its title reads back through JS. */
    @Test
    fun localPageLoadsAndTitleReadsBack() {
        val title = loadAndEval(
            "<html><head><title>Probe</title></head><body>hi</body></html>",
            "document.title"
        )
        assertEquals("\"Probe\"", title)
    }

    /** JavaScript evaluation returns a computed value. */
    @Test
    fun javaScriptEvaluates() {
        val sum = loadAndEval(
            "<html><head><title>x</title></head><body>hi</body></html>",
            "(1+2).toString()"
        )
        assertEquals("\"3\"", sum)
    }
}
