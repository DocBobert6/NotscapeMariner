package net.notscape.browser.browser

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.webkit.WebView

/**
 * Page extras (0.0.993): save the current page as a PDF
 * through the system print flow (the user picks "Save
 * as PDF" there), and capture a viewport screenshot
 * into Pictures/Notscape.
 */
object PageExtras {

    fun saveAsPdf(activity: Activity, webView: WebView, title: String) {
        val pm = activity.getSystemService(
            Context.PRINT_SERVICE
        ) as PrintManager
        val job = title.ifBlank { "Page" }
        pm.print(
            job,
            webView.createPrintDocumentAdapter(job),
            PrintAttributes.Builder().build()
        )
    }

    /** True when the screenshot landed in Pictures. */
    fun saveScreenshot(context: Context, webView: WebView): Boolean {
        if (webView.width <= 0 || webView.height <= 0) return false
        val bmp = Bitmap.createBitmap(
            webView.width, webView.height, Bitmap.Config.ARGB_8888
        )
        webView.draw(Canvas(bmp))
        val name = "notscape-" + System.currentTimeMillis() + ".png"
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Notscape"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: error("MediaStore insert failed")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("MediaStore stream unavailable")
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }
}
