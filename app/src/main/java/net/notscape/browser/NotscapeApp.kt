package net.notscape.browser

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs the crash reporter before anything else in the process runs,
 * so a crash in ANY entry point (the browser, the player, or a task the
 * system restores straight into an activity) leaves a report behind.
 * Reports stay on the device; the user can share one from the dialog
 * shown on the next launch, or dismiss it (which deletes it).
 *
 * A crash in native code (for example the graphics layer) cannot be
 * caught this way — it leaves no report, which is itself a useful
 * signal when diagnosing.
 */
class NotscapeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    append("Notscape Mariner ").append(BuildConfig.VERSION_NAME)
                    append(" (build ").append(BuildConfig.VERSION_CODE).append(")\n")
                    append("When: ").append(stamp).append('\n')
                    append("Android: ").append(android.os.Build.VERSION.RELEASE)
                    append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
                    append("Device: ").append(android.os.Build.MANUFACTURER)
                    append(' ').append(android.os.Build.MODEL).append('\n')
                    append("Thread: ").append(thread.name).append("\n\n")
                    append(sw.toString())
                }
                val file = File(filesDir, "last_crash.txt")
                val prior = runCatching {
                    if (file.exists()) file.readText() else ""
                }.getOrDefault("")
                // Newest first; earlier crashes kept below so a repeat
                // crash doesn't erase the evidence of the first one.
                val combined = if (prior.isBlank()) {
                    report
                } else {
                    report + "\n\n===== previous crash =====\n" + prior
                }
                file.writeText(combined.take(64_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
