package net.notscape.browser

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User-controlled, local-only diagnostic trace (beta.55).
 *
 * The user starts tracing from Settings → Data; every traced event
 * appends one timestamped line to an app-private file and flushes
 * immediately, so a crash cannot destroy the trace — an in-memory
 * buffer would die with the process exactly when the trace matters
 * most. End trace only stops appending; the file stays for export.
 * A new Start clears the previous trace. Tracing never auto-resumes
 * after a process death, but the file from a crashed session
 * remains exportable.
 *
 * Nothing leaves the device except through the user's explicit
 * export share. Logged URLs have query strings and fragments
 * stripped (host + path only), so tokens and session parameters
 * can never appear in a trace. Vault entries, passwords, autofill
 * content, and page bodies are never logged — only event names.
 */
object Trace {

    private const val ACTIVE_NAME = "trace.txt"
    private const val EXPORT_DIR = "traces"

    private var dir: File? = null
    private val lock = Any()

    /** Compose-observed state for the Settings UI. */
    val tracing = mutableStateOf(false)
    val eventCount = mutableStateOf(0)

    fun init(filesDir: File) {
        dir = filesDir
    }

    fun start() {
        val d = dir ?: return
        synchronized(lock) {
            runCatching {
                File(d, ACTIVE_NAME).writeText(formatLine("trace started") + "\n")
            }
        }
        eventCount.value = 1
        tracing.value = true
    }

    fun stop() {
        if (!tracing.value) return
        event("trace stopped")
        tracing.value = false
    }

    /** Append one timestamped line; a no-op when not tracing. */
    fun event(message: String) {
        if (!tracing.value) return
        appendLine(message)
        eventCount.value = eventCount.value + 1
    }

    /**
     * Crash-path append: best-effort and synchronous, called from
     * the uncaught-exception handler before the process dies.
     * Never throws, never touches Compose state.
     */
    fun crashEvent(message: String) {
        if (!tracing.value) return
        appendLine(message)
    }

    private fun appendLine(message: String) {
        val d = dir ?: return
        synchronized(lock) {
            runCatching {
                File(d, ACTIVE_NAME).appendText(formatLine(message) + "\n")
            }
        }
    }

    fun hasContent(): Boolean {
        val d = dir ?: return false
        val f = File(d, ACTIVE_NAME)
        return f.exists() && f.length() > 0
    }

    /**
     * Copy the active trace to a timestamped file and share it
     * through the system chooser. Returns false when there is
     * nothing to export. The file leaves the device only here,
     * only when the user taps Export.
     */
    fun export(context: Context): Boolean {
        val d = dir ?: return false
        val src = File(d, ACTIVE_NAME)
        if (!src.exists() || src.length() == 0L) return false
        return runCatching {
            val outDir = File(d, EXPORT_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(Date())
            val dst = File(outDir, "notscape-trace-$stamp.txt")
            src.copyTo(dst, overwrite = true)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dst
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(
                    send,
                    context.getString(R.string.trace_share_title)
                )
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Strip query strings, fragments, and userinfo from a URL,
     * keeping scheme + host + path. Pure string work (no
     * android.net.Uri, which is a stub under JVM unit tests).
     */
    fun sanitizeUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var u = raw.trim()
        val frag = u.indexOf('#')
        if (frag >= 0) u = u.substring(0, frag)
        val query = u.indexOf('?')
        if (query >= 0) u = u.substring(0, query)
        // Userinfo (user:pass@) is credential-bearing: drop it.
        val schemeEnd = u.indexOf("://")
        if (schemeEnd >= 0) {
            val authStart = schemeEnd + 3
            val pathStart = u.indexOf('/', authStart)
                .let { if (it < 0) u.length else it }
            val at = u.indexOf('@', authStart)
            if (at >= 0 && at < pathStart) {
                u = u.substring(0, authStart) + u.substring(at + 1)
            }
        }
        return u
    }

    fun formatLine(message: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(Date())
        return "$stamp $message"
    }
}
