package net.notscape.browser.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Audit P-M4: a PDF viewer screen. Pager + zoom, reader chrome.
 * The file lives in app storage (fetched by the browser, never in
 * the WebView, never in public Downloads unless the user taps
 * Download). Save as PDF (printing) is a different feature.
 */
@Composable
fun PdfViewerScreen(
    path: String?,
    sourceUrl: String?,
    onBack: () -> Unit,
    onDownload: () -> Unit
) {
    val file = path?.let { File(it) }
    val renderer = remember(path) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file!!, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd)
        }.getOrNull()
    }
    val openError = file == null || !file.exists() || renderer == null
    DisposableEffect(path) {
        onDispose { runCatching { renderer?.close() } }
    }
    val pageCount = renderer?.pageCount ?: 0
    val pagerState = key(path) { rememberPagerState(pageCount = { pageCount }) }
    val page = pagerState.currentPage
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var bitmap by remember(page, path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page, path) {
        panX = 0f
        panY = 0f
        bitmap = null
        val r = renderer ?: return@LaunchedEffect
        runCatching {
            r.openPage(page).use { p ->
                val scale = minOf(2f, 3000f / maxOf(p.width, p.height))
                val w = (p.width * scale).toInt().coerceAtLeast(1)
                val h = (p.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap = bmp
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = sourceUrl?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() } ?: "PDF",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (pageCount > 0) {
                Text(
                    text = "${page + 1} / $pageCount",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Download, contentDescription = "Download PDF")
            }
        }
        if (openError || pageCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "This PDF is no longer on the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${page + 1}",
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = panX
                                translationY = panY
                            }
                            .then(
                                if (zoom > 1f) {
                                    Modifier.pointerInput(zoom) {
                                        detectDragGestures { _, drag ->
                                            panX += drag.x
                                            panY += drag.y
                                        }
                                    }
                                } else Modifier
                            )
                    )
                } else {
                    CircularProgressIndicator()
                }
                // Pager sits above the image only as a swipe
                // surface when not zoomed; when zoomed, the
                // drag above pans instead.
                if (zoom <= 1f) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zoom", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                listOf(1f to "100%", 1.5f to "150%", 2f to "200%").forEach { (z, label) ->
                    TextButton(onClick = { zoom = z }) {
                        Text(
                            label,
                            color = if (zoom == z) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Swipe to turn pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
