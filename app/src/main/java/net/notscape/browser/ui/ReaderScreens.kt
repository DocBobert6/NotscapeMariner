package net.notscape.browser.ui

import android.provider.OpenableColumns
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import android.net.Uri
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.notscape.browser.browser.Article
import net.notscape.browser.browser.ArticleCache
import net.notscape.browser.browser.applyWebHardening
import net.notscape.browser.browser.buildReaderShell
import net.notscape.browser.browser.readerPalette
import net.notscape.browser.browser.readerThemeKey
import net.notscape.browser.browser.readerFontFaceCss
import net.notscape.browser.browser.readerFontKey
import net.notscape.browser.R
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.BookRecord
import net.notscape.browser.reader.BookParser
import net.notscape.browser.reader.ParsedBook
import net.notscape.browser.reader.BookHit
import net.notscape.browser.reader.chapterPreview
import net.notscape.browser.reader.searchBook
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import net.notscape.browser.reader.Ribbon
import net.notscape.browser.reader.formatTimeLeft
import net.notscape.browser.reader.parseRibbons
import net.notscape.browser.reader.ribbonsToJson
import java.io.File

private val parsedCache = mutableMapOf<Long, ParsedBook>()

/** C17: the parsed-book cache is capped at 3 entries -
 * a parsed book holds its whole text plus images, so an
 * unbounded cache grows with every book opened. */
private fun cacheParsed(id: Long, parsed: ParsedBook) {
    parsedCache[id] = parsed
    while (parsedCache.size > 3) {
        val victim = parsedCache.keys.firstOrNull { it != id }
            ?: break
        parsedCache.remove(victim)
    }
}

/** C16: decode a bitmap downsampled so its longest side
 * fits [maxSide] - full-res covers and chapter art
 * decode on the UI thread today and can spike tens of
 * MB for one image. */
private fun decodeSampledBytes(
    bytes: ByteArray, maxSide: Int = 1024
): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options()
        .apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, bounds
    )
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) {
        sample *= 2
    }
    val opts = android.graphics.BitmapFactory.Options()
        .apply { inSampleSize = sample }
    return runCatching {
        android.graphics.BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, opts
        )
    }.getOrNull()
}

private fun decodeSampledFile(
    path: String, maxSide: Int = 1024
): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options()
        .apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) {
        sample *= 2
    }
    val opts = android.graphics.BitmapFactory.Options()
        .apply { inSampleSize = sample }
    return runCatching {
        android.graphics.BitmapFactory.decodeFile(path, opts)
    }.getOrNull()
}

/** One large bookshelf tile (0.0.990): the cover art
 * carries it, title and author beneath, a small remove
 * button over the cover's corner. */
@Composable
private fun ShelfTile(
    book: BookRecord,
    store: AppStore,
    onOpenBook: (Long) -> Unit
) {
    Column(modifier = Modifier.clickable { onOpenBook(book.id) }) {
        Box {
            val coverBmp = produceState<android.graphics.Bitmap?>(
                initialValue = null, book.coverPath
            ) {
                value = withContext(Dispatchers.IO) {
                    if (book.coverPath.isNotBlank()) {
                        decodeSampledFile(book.coverPath)
                    } else {
                        null
                    }
                }
            }.value
            if (coverBmp != null) {
                Image(
                    bitmap = coverBmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(10.dp)
                        )
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(10.dp)
                        )
                        .background(
                            androidx.compose.material3.MaterialTheme
                                .colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        book.title.take(2).uppercase(),
                        style = androidx.compose.material3
                            .MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.material3
                            .MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = androidx.compose.foundation.shape
                    .CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(6.dp).size(30.dp)
            ) {
                IconButton(onClick = {
                    parsedCache.remove(book.id)
                    store.removeBook(book.id)
                }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove book",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        Text(
            book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme
                .typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            listOf(book.format, book.author)
                .filter { it.isNotBlank() }
                .joinToString(" • "),
            style = androidx.compose.material3.MaterialTheme
                .typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme
                .colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(store: AppStore, onBack: () -> Unit, onOpenBook: (Long) -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importStatus by remember { mutableStateOf("") }
    var bookQuery by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    // Large cover tiles instead of the list (0.0.990,
    // Robert), toggled from the header and persisted.
    val largeTiles = store.setting("shelfLargeTiles", "false") == "true"
    // The shelf renders this snapshot, refreshed whenever the store's
    // revision moves — so a delete (or import) shows immediately,
    // without leaving and reopening the screen.
    var books by remember { mutableStateOf(store.books.toList()) }
    LaunchedEffect(rev) { books = store.books.toList() }

    // One-time MOBI migration (Phase E): MOBI import is gone, but
    // books already on the shelf keep their text — each MOBI source
    // is converted once to an extracted text file and the record is
    // repointed at it.
    LaunchedEffect(Unit) {
        if (store.setting("mobiMigrated", "false") == "true") return@LaunchedEffect
        withContext(Dispatchers.IO) {
            store.books.filter { it.format.equals("MOBI", ignoreCase = true) }
                .forEach { rec ->
                    runCatching {
                        val legacy = BookParser.parseMobiLegacy(File(rec.filePath))
                        val dir = File(context.filesDir, "books").apply { mkdirs() }
                        val txt = File(dir, "${rec.id}.txt")
                        txt.writeText(
                            legacy.chapters.joinToString("\n\n") { ch ->
                                if (legacy.chapters.size > 1) {
                                    ch.title + "\n\n" + ch.text
                                } else {
                                    ch.text
                                }
                            }
                        )
                        store.updateBookSource(rec.id, txt.absolutePath, "TXT")
                    }
                }
        }
        store.putSetting("mobiMigrated", "true")
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        importStatus = "Importing…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = run {
                        var display = "book.txt"
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) display = c.getString(0)
                        }
                        display
                    }
                    val ext = name.substringAfterLast('.', "txt").lowercase()
                    if (ext in setOf("mobi", "azw", "azw3", "prc")) {
                        error("MOBI files are no longer supported — EPUB or TXT only")
                    }
                    val format = when (ext) {
                        "epub" -> "EPUB"
                        else -> "TXT"
                    }
                    val dir = File(context.filesDir, "books").apply { mkdirs() }
                    val dest = File(dir, "${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    } ?: error("Could not read the selected file")
                    val parsed = BookParser.parse(dest)
                    val newBookId = System.currentTimeMillis() * 1000 + (0..999).random()
                    val coverPath = parsed.cover?.let { bytes ->
                        runCatching {
                            val coverFile = File(dir, "$newBookId-cover.img")
                            coverFile.writeBytes(bytes)
                            coverFile.absolutePath
                        }.getOrNull()
                    } ?: ""
                    BookRecord(
                        id = newBookId,
                        title = parsed.title.ifBlank { name },
                        author = parsed.author,
                        filePath = dest.absolutePath,
                        format = format,
                        chapterIndex = 0,
                        scrollFraction = 0f,
                        addedAt = System.currentTimeMillis(),
                        coverPath = coverPath
                    ) to parsed
                }
            }
            importing = false
            result.onSuccess { (rec, parsed) ->
                cacheParsed(rec.id, parsed)
                store.addBook(rec)
                importStatus = "Added “${rec.title}” (${parsed.chapters.size} chapters)"
            }.onFailure {
                importStatus = "Import failed: ${it.message ?: "unknown error"}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Bookshelf", onBack = onBack) {
            IconButton(onClick = {
                store.putSetting("shelfLargeTiles", (!largeTiles).toString())
            }) {
                Icon(
                    if (largeTiles) Icons.Filled.ViewList
                    else Icons.Filled.GridView,
                    contentDescription = if (largeTiles) {
                        "List view"
                    } else {
                        "Large tiles"
                    }
                )
            }
            IconButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = "Import book")
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(
                    androidx.compose.material3.MaterialTheme
                        .colorScheme.surfaceContainerLow
                )
        ) {
            Text(
                "Import EPUB or TXT books. Books are stored on this device.",
                modifier = Modifier.padding(16.dp)
            )
            OutlinedTextField(
                value = bookQuery,
                onValueChange = { bookQuery = it },
                label = { Text("Search books") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            val shownBooks = books.filter { b ->
                bookQuery.isBlank() ||
                    b.title.contains(bookQuery, ignoreCase = true) ||
                    b.author.contains(bookQuery, ignoreCase = true)
            }
            if (books.isNotEmpty() && shownBooks.isEmpty()) {
                Text("No books match your search.", modifier = Modifier.padding(16.dp))
            }
            if (importing) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("  Importing…")
                }
            }
            if (importStatus.isNotBlank()) {
                Text(importStatus, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (books.isEmpty()) {
                PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Your bookshelf is empty.", modifier = Modifier.padding(16.dp))
                }
            }
            if (largeTiles) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shownBooks, key = { it.id }) { book ->
                        ShelfTile(book, store, onOpenBook)
                    }
                }
            } else {
            LazyColumn {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        SettingsCard {
                            shownBooks.forEachIndexed { i, book ->
                                if (i > 0) InsetDivider()
                                KitGroupedRow(
                                    onClick = { onOpenBook(book.id) },
                                    leadingWidth = 40.dp,
                                leading = {
                            if (book.coverPath.isNotBlank()) {
                                val coverBmp = produceState<android.graphics.Bitmap?>(
                                    initialValue = null, book.coverPath
                                ) {
                                    value = withContext(Dispatchers.IO) {
                                        decodeSampledFile(book.coverPath)
                                    }
                                }.value
                                if (coverBmp != null) {
                                    Image(
                                        bitmap = coverBmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(width = 40.dp, height = 56.dp)
                                    )
                                }
                            }
                        },
                                title = {
                            Text(
                                book.title, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.material3
                                    .MaterialTheme.typography.bodyLarge
                            )
                        },
                                supporting = {
                            Text(
                                listOf(book.format, book.author, "Chapter ${book.chapterIndex + 1}")
                                    .filter { it.isNotBlank() }.joinToString(" • "),
                                style = androidx.compose.material3
                                    .MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3
                                    .MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        },
                                trailing = {
                            IconButton(onClick = {
                                parsedCache.remove(book.id)
                                store.removeBook(book.id)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove book")
                            }
                        },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            }
        }
    }
}

/** A private-origin article is never saved: its save buttons do
 * not render, and the save functions refuse as well, so no book
 * record (and no progress for one) can land it in Continue
 * reading. Top-level so unit tests can pin it down. */
internal fun bookSaveAllowed(privateOrigin: Boolean): Boolean = !privateOrigin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    store: AppStore,
    bookId: Long?,
    articleUrl: String?,
    privateOrigin: Boolean = false,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var parsed by remember(bookId, articleUrl) { mutableStateOf<ParsedBook?>(null) }
    var loadError by remember(bookId, articleUrl) { mutableStateOf("") }
    var loading by remember(bookId, articleUrl) { mutableStateOf(true) }
    val record = bookId?.let { id -> store.books.firstOrNull { it.id == id } }
    var chapterIndex by remember(bookId, articleUrl) {
        mutableIntStateOf(record?.chapterIndex ?: 0)
    }
    var showChapters by remember { mutableStateOf(false) }
    // In-book search (0.0.984).
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<BookHit>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchDone by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showTypeset by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    // An article extracted in the browser tab (Mozilla Readability)
    // renders from the cache; the fetch path below is only the fallback
    // for article URLs that arrive without an extraction.
    val cachedArticle = remember(articleUrl) {
        articleUrl?.let { u -> ArticleCache.current?.takeIf { it.url == u } }
    }

    val readerScope = rememberCoroutineScope()
    var clippingNote by remember { mutableStateOf("") }

    /** Save the extracted article as an offline bookshelf clipping. */
    fun saveClipping() {
        if (!bookSaveAllowed(privateOrigin)) {
            clippingNote = "Private tabs don't save to the bookshelf"
            return
        }
        val a = cachedArticle ?: return
        readerScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "books").apply { mkdirs() }
                    val id = System.currentTimeMillis() * 1000 + (0..999).random()
                    val f = File(dir, "$id-clipping.txt")
                    val text = BookParser.htmlToText(a.contentHtml)
                    f.writeText(a.title + "\n\n" + text)
                    store.addBook(
                        BookRecord(
                            id = id,
                            title = a.title.ifBlank { "Clipping" },
                            author = a.siteName,
                            filePath = f.absolutePath,
                            format = "Clipping",
                            chapterIndex = 0,
                            scrollFraction = 0f,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                }.isSuccess
            }
            clippingNote = if (ok) {
                "Saved to your bookshelf"
            } else {
                "Couldn't save that article"
            }
        }
    }

    /** Save a fetched (non-cached) article as a bookshelf entry.
     *  The cached path keeps its own saveClipping; this one covers
     *  articles fetched straight from a URL. */
    fun saveFetchedArticle() {
        if (!bookSaveAllowed(privateOrigin)) {
            clippingNote = "Private tabs don't save to the bookshelf"
            return
        }
        val p = parsed ?: return
        readerScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "books").apply { mkdirs() }
                    val id = System.currentTimeMillis() * 1000 + (0..999).random()
                    val f = File(dir, "$id-article.txt")
                    val text = p.chapters.joinToString("\n\n") { ch ->
                        if (ch.title.isNotBlank()) {
                            "${ch.title}\n\n${ch.text}"
                        } else ch.text
                    }
                    f.writeText(p.title + "\n\n" + text)
                    store.addBook(
                        BookRecord(
                            id = id,
                            title = p.title.ifBlank { "Article" },
                            author = p.author,
                            filePath = f.absolutePath,
                            format = "Article",
                            chapterIndex = 0,
                            scrollFraction = 0f,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                }.isSuccess
            }
            clippingNote = if (ok) {
                "Saved to your bookshelf"
            } else {
                "Couldn't save that article"
            }
        }
    }

    LaunchedEffect(bookId, articleUrl) {
        if (articleUrl != null && cachedArticle != null) {
            parsed = null
            loadError = ""
            loading = false
            return@LaunchedEffect
        }
        loading = true
        loadError = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    bookId != null -> {
                        parsedCache[bookId] ?: run {
                            val rec = store.books.firstOrNull { it.id == bookId }
                                ?: error("Book not found")
                            BookParser.parse(File(rec.filePath)).also { cacheParsed(bookId, it) }
                        }
                    }
                    articleUrl != null -> BookParser.fetchArticle(articleUrl)
                    else -> error("Nothing to read")
                }
            }
        }
        result.onSuccess { parsed = it }.onFailure { loadError = it.message ?: "Could not open" }
        loading = false
    }

    val fontSize = store.setting("readerFontSize", "18").toFloatOrNull() ?: 18f
    val lineMult = store.setting("readerLineMult", "1.5").toFloatOrNull() ?: 1.5f
    val padDp = store.setting("readerPadding", "20").toFloatOrNull() ?: 20f
    val themeName = readerThemeKey(store.setting("readerTheme", "white"))
    val fontName = readerFontKey(store.setting("readerFont", "opensans"))
    // Text alignment (Robert, 0.0.965): Default keeps the
    // platform's start alignment; the rest map straight through.
    val alignKey = store.setting("readerAlign", "default")
    // Read-aloud choices (0.0.968): pack, voice, speed - read
    // here so the typeset sheet's pills track the live settings.
    val ttsEngineKey = store.setting("ttsEngine", "kokoro")
    val ttsVoiceSid = store.setting(
        if (ttsEngineKey == "piper") "ttsVoicePiper" else "ttsVoiceKokoro",
        if (ttsEngineKey == "piper") "0" else "3"
    ).toIntOrNull() ?: 3
    val ttsSpeedValue = store.setting("ttsSpeed", "1.0").toFloatOrNull() ?: 1.0f
    // Keep-screen-awake (0.0.985): the flag lives only while
    // this reader is on screen and the setting is on.
    val keepAwake = store.setting("readerKeepAwake", "false") == "true"
    val activityWindow = (context as? android.app.Activity)?.window
    DisposableEffect(keepAwake, activityWindow) {
        if (keepAwake) {
            activityWindow?.addFlags(
                android.view.WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON
            )
        }
        onDispose {
            activityWindow?.clearFlags(
                android.view.WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON
            )
        }
    }
    val bodyAlign = when (alignKey) {
        "left" -> TextAlign.Left
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        "justify" -> TextAlign.Justify
        else -> null
    }
    val palette = readerPalette(themeName)
    val bg = Color(android.graphics.Color.parseColor(palette.bg))
    val fg = Color(android.graphics.Color.parseColor(palette.fg))
    val fontFamily = when (fontName) {
        "inter" -> FontFamily(Font(R.font.inter))
        "roboto" -> FontFamily.SansSerif
        "custom" -> {
            // His own imported face (0.0.985), registered the
            // same way as the interface font; a missing file
            // falls back to Open Sans rather than failing.
            val f = java.io.File(context.filesDir, "fonts/reader-font")
            if (f.exists()) {
                FontFamily(
                    Font(f, FontWeight.Normal),
                    Font(f, FontWeight.Bold)
                )
            } else {
                FontFamily(Font(R.font.opensans))
            }
        }
        else -> FontFamily(Font(R.font.opensans))
    }

    val book = parsed
    // Character counts for the time-left estimates (0.0.985).
    val bookCharsTotal = remember(book) {
        book?.chapters?.sumOf { it.text.length } ?: 0
    }
    val pieceChars = book?.chapters?.getOrNull(chapterIndex)?.text?.length ?: 0
    // Read-aloud (0.0.964; full build 0.0.968): the text this
    // screen can speak - the article's paragraphs for a cached
    // extraction, else the open chapter's. Changing chapter
    // doesn't stop playback, and the strip below names what
    // is being read - but leaving the reader pauses it
    // (0.0.994, Robert), and closing the app stops it
    // entirely (the service's onTaskRemoved).
    val ttsPlayer = remember { net.notscape.browser.tts.TtsPlayer.get(context) }
    // Leaving the reader pauses the reading (0.0.994,
    // Robert): read-aloud used to keep playing after you
    // navigated away from the book or article. Pausing
    // keeps the place - the transport, the Voice sheet,
    // and the notification can all resume it.
    DisposableEffect(Unit) {
        onDispose {
            if (net.notscape.browser.tts.TtsState.phase ==
                net.notscape.browser.tts.TtsPhase.Speaking
            ) {
                ttsPlayer.pause()
            }
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts
            .RequestPermission()
    ) { }
    // Reader custom font (0.0.985): the same gate as the
    // interface font - known magic bytes and the platform
    // typeface loader must both accept the file before it
    // replaces anything.
    var readerFontCustomName by remember {
        mutableStateOf(store.setting("readerFontCustomName", ""))
    }
    val readerFontPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts
            .OpenDocument()
    ) { uri ->
        if (uri != null) {
            Thread {
                var loadedName: String? = null
                val ok = runCatching {
                    val dir = java.io.File(
                        context.filesDir, "fonts"
                    ).apply { mkdirs() }
                    val tmp = java.io.File(dir, "reader-font.tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("unreadable")
                    val name = runCatching {
                        context.contentResolver.query(
                            uri,
                            arrayOf(
                                android.provider.OpenableColumns
                                    .DISPLAY_NAME
                            ),
                            null, null, null
                        )?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull()
                    val magic = ByteArray(4)
                    val read = tmp.inputStream().use { it.read(magic) }
                    val asText = if (read == 4) {
                        String(magic, Charsets.US_ASCII)
                    } else {
                        ""
                    }
                    val magicOk = read == 4 && (
                        (magic[0] == 0.toByte() && magic[1] == 1.toByte() &&
                            magic[2] == 0.toByte() &&
                            magic[3] == 0.toByte()) ||
                            asText == "OTTO" || asText == "true"
                        )
                    val faceOk = magicOk && runCatching {
                        android.graphics.Typeface
                            .createFromFile(tmp) != null
                    }.getOrDefault(false)
                    if (!faceOk) {
                        tmp.delete()
                        false
                    } else {
                        tmp.copyTo(
                            java.io.File(dir, "reader-font"),
                            overwrite = true
                        )
                        tmp.delete()
                        loadedName = name ?: "Custom font"
                        true
                    }
                }.getOrDefault(false)
                android.os.Handler(
                    android.os.Looper.getMainLooper()
                ).post {
                    if (ok) {
                        readerFontCustomName = loadedName ?: "Custom font"
                        store.putSetting(
                            "readerFontCustomName", readerFontCustomName
                        )
                        store.putSetting("readerFont", "custom")
                        android.widget.Toast.makeText(
                            context, "Font loaded",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context, "That file is not a usable font",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.start()
        }
    }
    val articleParagraphs = remember(cachedArticle) {
        cachedArticle?.let { a ->
            BookParser.htmlToText(a.contentHtml)
                .split(Regex("\n{2,}"))
                .map { it.replace("\n", " ").trim() }
                .filter { it.isNotBlank() }
        } ?: emptyList()
    }
    val chapter = book?.chapters?.getOrNull(chapterIndex)
    val paragraphs = remember(chapter) {
        chapter?.text?.split(Regex("\n{2,}"))?.map { it.replace("\n", " ").trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()
    }
    // Display parts (0.0.969): image tokens anywhere in a
    // paragraph become images in place; the spoken text never
    // carries a token at all.
    val parts = remember(paragraphs) {
        net.notscape.browser.reader.readerParts(paragraphs)
    }
    val speakable = if (cachedArticle != null) {
        articleParagraphs
    } else {
        paragraphs
            .map { net.notscape.browser.reader.stripImageTokens(it) }
            .filter { it.isNotBlank() }
    }
    val speakableTitle = book?.title ?: cachedArticle?.title ?: ""
    // Follow-along (0.0.987): which text this screen owns,
    // and the index bridges between the spoken list, the
    // piece's paragraphs and the rendered parts.
    val readerSourceKey = when {
        book != null && bookId != null -> "b:$bookId:$chapterIndex"
        cachedArticle != null -> "a:${cachedArticle.title}"
        else -> ""
    }
    val speakableParaIdx = remember(paragraphs) {
        paragraphs.mapIndexedNotNull { idx, p ->
            if (net.notscape.browser.reader
                    .stripImageTokens(p).isBlank()
            ) {
                null
            } else {
                idx
            }
        }
    }
    val paraFirstPart = remember(parts, paragraphs) {
        var cursor = 0
        paragraphs.map { p ->
            val first = cursor
            cursor += net.notscape.browser.reader
                .readerParts(listOf(p)).size
            first
        }
    }
    val followActive = readerSourceKey.isNotEmpty() &&
        net.notscape.browser.tts.TtsState.nowSourceKey ==
            readerSourceKey &&
        (net.notscape.browser.tts.TtsState.phase ==
            net.notscape.browser.tts.TtsPhase.Speaking ||
            net.notscape.browser.tts.TtsState.phase ==
            net.notscape.browser.tts.TtsPhase.Paused)
    val spokenPara = if (followActive) {
        speakableParaIdx.getOrNull(
            net.notscape.browser.tts.TtsState.nowParagraph
        )
    } else {
        null
    }
    val spokenPart = spokenPara?.let {
        paraFirstPart.getOrNull(it)
    }
    // Tap-to-read-from-here: while this piece is the one
    // being read, a tap on a paragraph restarts the voice
    // at that paragraph.
    val seekToPart: (Int) -> Unit = { partIdx ->
        val para = paraFirstPart.indexOfLast { it <= partIdx }
            .coerceAtLeast(0)
        val sIdx = speakableParaIdx.indexOfFirst { it >= para }
        if (sIdx >= 0) {
            ttsPlayer.speak(
                speakable, speakableTitle, readerSourceKey,
                startAt = sIdx
            )
            android.widget.Toast.makeText(
                context, "Reading from here",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    // Book spread (0.0.971, Robert's spec): in landscape with
    // the option on, a book chapter shows as fixed pages of
    // about 1,450 characters, two at a time. Articles and the
    // portrait reader keep the continuous scroll.
    val spreadSetting = store.setting("readerSpread", "false") == "true"
    val orientation = LocalConfiguration.current.orientation
    val spreadActive = book != null && cachedArticle == null &&
        spreadSetting &&
        orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Spread geometry (0.0.977): the spread area reports its
    // size, and pages are packed by MEASURED text against the
    // real page - the 0.0.971 character budget clipped lines
    // at the page edge and hid the overflow inside a private
    // page scroll.
    var spreadSize by remember {
        mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    }
    val spreadDensity = androidx.compose.ui.platform.LocalDensity.current
    val spreadMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val spreadBodyStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineMult).sp
    )
    val spreadTitleStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = fontFamily,
        fontSize = (fontSize + 3).sp
    )
    // Neighbouring chapters paginated with the same
    // measure (0.0.99992): a seam turn's sheet back and
    // revealed page are the adjacent chapter's real pages,
    // not nulls dressed as a blank proxy sheet.
    fun paginateChapter(
        ch: net.notscape.browser.reader.Chapter?
    ): List<List<net.notscape.browser.reader.ReaderPart>> {
        if (ch == null || !spreadActive ||
            spreadSize.width <= 0
        ) {
            return emptyList()
        }
        val paras = ch.text.split(Regex("\\n{2,}"))
            .map { it.replace("\n", " ").trim() }
            .filter { it.isNotBlank() }
        val rowPadH = with(spreadDensity) { 20.dp.toPx() }
        val rowPadV = with(spreadDensity) { 28.dp.toPx() }
        val spine = with(spreadDensity) { 1.dp.toPx() }
        val pagePad = with(spreadDensity) { padDp.dp.toPx() }
        val textW = ((spreadSize.width - rowPadH - spine) / 2f -
            2f * pagePad).toInt().coerceAtLeast(1)
        val textH = (spreadSize.height - rowPadV).toInt()
            .coerceAtLeast(1)
        val spacing = with(spreadDensity) {
            (fontSize * 0.6f).dp.toPx()
        }.toInt()
        val titleGap = with(spreadDensity) {
            12.dp.toPx()
        }.toInt()
        return paginateMeasured(
            net.notscape.browser.reader.readerParts(paras),
            ch.title, spreadMeasurer,
            spreadBodyStyle, spreadTitleStyle,
            textW, textH, spacing, titleGap
        )
    }
    val spreadPages = remember(
        parts, spreadActive, spreadSize, fontSize, lineMult,
        fontFamily, padDp, chapter?.title
    ) {
        if (!spreadActive || spreadSize.width <= 0) {
            emptyList()
        } else {
            val rowPadH = with(spreadDensity) { 20.dp.toPx() }
            val rowPadV = with(spreadDensity) { 28.dp.toPx() }
            val spine = with(spreadDensity) { 1.dp.toPx() }
            val pagePad = with(spreadDensity) { padDp.dp.toPx() }
            val textW = ((spreadSize.width - rowPadH - spine) / 2f -
                2f * pagePad).toInt().coerceAtLeast(1)
            val textH = (spreadSize.height - rowPadV).toInt()
                .coerceAtLeast(1)
            val spacing = with(spreadDensity) {
                (fontSize * 0.6f).dp.toPx()
            }.toInt()
            val titleGap = with(spreadDensity) {
                12.dp.toPx()
            }.toInt()
            paginateMeasured(
                parts, chapter?.title, spreadMeasurer,
                spreadBodyStyle, spreadTitleStyle,
                textW, textH, spacing, titleGap
            )
        }
    }
    val prevChapterPages = remember(
        book, chapterIndex, spreadActive, spreadSize,
        fontSize, lineMult, fontFamily, padDp
    ) {
        paginateChapter(book?.chapters?.getOrNull(chapterIndex - 1))
    }
    val nextChapterPages = remember(
        book, chapterIndex, spreadActive, spreadSize,
        fontSize, lineMult, fontFamily, padDp
    ) {
        paginateChapter(book?.chapters?.getOrNull(chapterIndex + 1))
    }
    // Page lookup across the seam (0.0.99992): indices
    // inside the current chapter read its pages; beyond
    // them the neighbouring chapter's pages continue the
    // sequence, so a seam turn carries real content.
    fun spreadPageAt(
        idx: Int
    ): List<net.notscape.browser.reader.ReaderPart>? = when {
        idx in spreadPages.indices -> spreadPages[idx]
        idx < 0 -> prevChapterPages.getOrNull(
            prevChapterPages.size + idx
        )
        else -> nextChapterPages.getOrNull(
            idx - spreadPages.size
        )
    }
    // A curl layer showing a neighbouring chapter's FIRST
    // page must carry its title, or the title pops in when
    // the turn lands (0.0.99992).
    fun curlTitleAt(idx: Int): String? = when {
        idx == spreadPages.size ->
            book?.chapters?.getOrNull(chapterIndex + 1)?.title
        idx < 0 && prevChapterPages.size + idx == 0 ->
            book?.chapters?.getOrNull(chapterIndex - 1)?.title
        else -> null
    }
    var spreadPage by remember(bookId, chapterIndex) { mutableStateOf(0) }
    // The saved place is applied in the SAME composition that
    // produces the pages (0.0.977): the old effect ran a frame
    // late, so a chapter change first drew the wrong spread
    // and then jumped - the flicker in Robert's recording.
    // Guarded by the pages' identity, so the write settles
    // at once and never fights a page turn.
    var soughtPages by remember(bookId, chapterIndex) {
        mutableStateOf<List<List<net.notscape.browser.reader.ReaderPart>>?>(null)
    }
    // Flowing back across a piece seam (0.0.978) lands on the
    // previous piece's LAST spread, not its saved fraction:
    // the turn handlers name the piece they are heading to,
    // and the seek below honours it exactly once.
    var spreadLandEndFor by remember(bookId) {
        mutableStateOf<Int?>(null)
    }
    // Spread turns (0.0.979): one pair of hands for taps,
    // drags and the curl, seams included (0.0.978).
    val turnSpreadForward: () -> Unit = {
        if (spreadPage + 2 < spreadPages.size) {
            spreadPage += 2
        } else if (chapterIndex < (book?.chapters?.size ?: 0) - 1) {
            chapterIndex++
        }
    }
    val turnSpreadBackward: () -> Unit = {
        if (spreadPage > 0) {
            spreadPage -= 2
        } else if (chapterIndex > 0) {
            spreadLandEndFor = chapterIndex - 1
            chapterIndex--
        }
    }
    val canSpreadForward = spreadPage + 2 < spreadPages.size ||
        chapterIndex < (book?.chapters?.size ?: 0) - 1
    val canSpreadBackward = spreadPage > 0 || chapterIndex > 0
    // Finger-follow page curl (0.0.979, Robert's choice):
    // while a drag turns a page, curlDir says which page is
    // moving (+1 the right page forward, -1 the left page
    // backward); curlPoint is the (smoothed) drag point in
    // that page's coordinates and curlCornerTop says which
    // outer corner the sheet is lifting from (0.0.982).
    var curlDir by remember { mutableStateOf(0) }
    var curlPoint by remember { mutableStateOf<Offset?>(null) }
    var curlCornerTop by remember { mutableStateOf(true) }
    var curlSeq by remember { mutableIntStateOf(0) }
    // Transport scrub slider (0.0.981): scrubFrac is the
    // finger's whole-book fraction while the pill's slider
    // is being dragged; scrubTarget is a one-shot landing
    // order (piece, place inside it) that the chapter-turn
    // effect honours instead of the top.
    var scrubFrac by remember { mutableStateOf<Float?>(null) }
    var scrubTarget by remember {
        mutableStateOf<Pair<Int, Float>?>(null)
    }
    if (spreadActive && spreadPages.isNotEmpty() &&
        soughtPages !== spreadPages
    ) {
        soughtPages = spreadPages
        val target = if (spreadLandEndFor == chapterIndex) {
            spreadLandEndFor = null
            spreadPages.size - 1
        } else {
            val frac = if (chapterIndex == (record?.chapterIndex ?: 0)) {
                record?.scrollFraction ?: 0f
            } else {
                0f
            }
            (frac * spreadPages.size).toInt()
                .coerceIn(0, spreadPages.size - 1)
        }
        spreadPage = target - (target % 2)
    }
    LaunchedEffect(spreadPage, spreadActive) {
        if (bookId == null || !spreadActive || spreadPages.isEmpty()) {
            return@LaunchedEffect
        }
        store.updateBookProgress(
            bookId, chapterIndex,
            spreadPage.toFloat() / spreadPages.size
        )
    }
    val initialItem = remember(bookId, chapterIndex) {
        val frac = if (chapterIndex == (record?.chapterIndex ?: 0)) record?.scrollFraction ?: 0f else 0f
        (frac * parts.size).toInt().coerceIn(0, (parts.size - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItem)

    LaunchedEffect(listState, bookId, chapterIndex, spreadActive) {
        if (bookId == null || spreadActive) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(600)
            .collectLatest { idx ->
                if (paragraphs.isNotEmpty()) {
                    store.updateBookProgress(bookId, chapterIndex, idx.toFloat() / paragraphs.size)
                }
            }
    }

    // Chapter turns start at the top (0.0.974, Robert's
    // report): the list state is created once with the saved
    // position and was never reset on a turn, so the old
    // offset carried into the new chapter and left him at
    // its bottom. Opening a book still restores his place
    // through initialItem - this fires only when the chapter
    // changes under him (buttons, swipe, read-aloud follow).
    var lastChapterIndex by remember(bookId) { mutableStateOf(chapterIndex) }
    LaunchedEffect(chapterIndex, spreadActive) {
        if (!spreadActive && chapterIndex != lastChapterIndex) {
            lastChapterIndex = chapterIndex
            val st = scrubTarget
            if (st != null && st.first == chapterIndex) {
                scrubTarget = null
                val idx = (st.second * paragraphs.size).toInt()
                    .coerceIn(
                        0, (paragraphs.size - 1).coerceAtLeast(0)
                    )
                listState.scrollToItem(idx)
            } else {
                listState.scrollToItem(0)
            }
        }
    }
    // Chrome steps aside while scrolling (0.0.974); a tap on
    // the page brings it back.
    LaunchedEffect(listState, bookId) {
        if (bookId == null) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) chromeVisible = false
            }
    }

    // Follow-along keeps the spoken paragraph in view
    // (0.0.987): scroll to it in scroll mode; turn to its
    // spread in spread mode - never mid-curl.
    LaunchedEffect(
        spokenPart, followActive, spreadActive, spreadPages
    ) {
        val target = spokenPart ?: return@LaunchedEffect
        if (!followActive) return@LaunchedEffect
        if (spreadActive) {
            var acc = 0
            var page = -1
            for ((i, pg) in spreadPages.withIndex()) {
                if (target in acc until acc + pg.size) {
                    page = i
                    break
                }
                acc += pg.size
            }
            if (page >= 0 && page != spreadPage &&
                page != spreadPage + 1 && curlPoint == null
            ) {
                spreadPage = page - (page % 2)
            }
        } else {
            val itemIndex = target + 1
            val visible = listState.layoutInfo.visibleItemsInfo
                .any { it.index == itemIndex }
            if (!visible) {
                listState.animateScrollToItem(itemIndex)
            }
        }
    }

    val chromeShown = chromeVisible || cachedArticle != null || book == null
    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                loadError.isNotBlank() -> Text(
                    "Could not open: $loadError",
                    modifier = Modifier.padding(20.dp), color = fg
                )
                cachedArticle != null -> {
                    ArticleWebView(
                        article = cachedArticle,
                        themeKey = themeName,
                        font = fontName,
                        fontSize = fontSize.toInt(),
                        alignKey = alignKey,
                        onOpenUrl = onOpenUrl
                    )
                }
                spreadActive && chapter != null -> {
                    // Two-page book spread (0.0.971): tap the
                    // right side for the next spread, the left
                    // for the previous, the middle for the chrome.
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .onSizeChanged { spreadSize = it }
                            .pointerInput(
                                spreadPage, chapterIndex,
                                spreadPages.size,
                                canSpreadForward, canSpreadBackward
                            ) {
                                // One pair of hands for the
                                // spread (0.0.979): a tap turns
                                // or shows the chrome, exactly as
                                // before; a horizontal drag becomes
                                // the finger-follow page curl, and
                                // releasing past a third of the
                                // page (or with a good flick)
                                // completes the turn.
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false
                                    )
                                    curlSeq++
                                    val mySeq = curlSeq
                                    val slotW = (size.width -
                                        20.dp.toPx() -
                                        1.dp.toPx()) / 2f
                                    if (slotW <= 0f) {
                                        return@awaitEachGesture
                                    }
                                    // Direction comes from the
                                    // swipe itself (0.0.986,
                                    // Robert): a leftward swipe
                                    // turns forward, a rightward
                                    // swipe turns back, wherever
                                    // the finger lands. dir stays
                                    // 0 until the drag declares
                                    // itself at engagement, then
                                    // locks for the rest of that
                                    // drag; slotStart follows the
                                    // turning page.
                                    var dir = 0
                                    var slotStart = 10.dp.toPx()
                                    val slotTop = 14.dp.toPx()
                                    val slotH = size.height -
                                        28.dp.toPx()
                                    var engaged = false
                                    var upPos = down.position
                                    var released = false
                                    while (true) {
                                        val event =
                                            awaitPointerEvent()
                                        val change = event.changes
                                            .firstOrNull {
                                                it.id == down.id
                                            } ?: break
                                        upPos = change.position
                                        if (!change.pressed) {
                                            released = true
                                            break
                                        }
                                        if (!engaged) {
                                            val dx =
                                                change.position.x -
                                                    down.position.x
                                            val dy =
                                                change.position.y -
                                                    down.position.y
                                            if (kotlin.math.abs(dx) >
                                                viewConfiguration
                                                    .touchSlop &&
                                                kotlin.math.abs(dx) >
                                                kotlin.math.abs(dy) *
                                                    1.2f
                                            ) {
                                                val want = if (dx < 0) {
                                                    1
                                                } else {
                                                    -1
                                                }
                                                val allowed = if (
                                                    want > 0
                                                ) {
                                                    canSpreadForward
                                                } else {
                                                    canSpreadBackward
                                                }
                                                if (allowed) {
                                                    engaged = true
                                                    dir = want
                                                    curlDir = want
                                                    slotStart = if (
                                                        want > 0
                                                    ) {
                                                        10.dp.toPx() +
                                                            slotW +
                                                            1.dp.toPx()
                                                    } else {
                                                        10.dp.toPx()
                                                    }
                                                    val localY =
                                                        (down.position.y -
                                                            slotTop)
                                                            .coerceIn(
                                                                0f, slotH
                                                            )
                                                    curlCornerTop =
                                                        localY <
                                                            slotH / 2f
                                                    val cornerY = if (
                                                        curlCornerTop
                                                    ) {
                                                        0f
                                                    } else {
                                                        slotH
                                                    }
                                                    val grabY =
                                                        (change.position.y -
                                                            slotTop)
                                                            .coerceIn(
                                                                0f, slotH
                                                            )
                                                    curlPoint = constrainCurlFinger(
                                                        Offset(
                                                            if (want > 0) slotW else 0f,
                                                            cornerY
                                                        ),
                                                        Offset(
                                                            (change.position.x -
                                                                slotStart)
                                                                .coerceIn(
                                                                    if (want > 0) {
                                                                        -slotW * 0.35f
                                                                    } else {
                                                                        0f
                                                                    },
                                                                    if (want > 0) {
                                                                        slotW
                                                                    } else {
                                                                        slotW * 1.35f
                                                                    }
                                                                ),
                                                            grabY
                                                        ),
                                                        intoPositiveX = want < 0
                                                    )
                                                }
                                            }
                                        }
                                        if (engaged) {
                                            val rawX =
                                                (change.position.x -
                                                    slotStart)
                                                    .coerceIn(
                                                        if (dir > 0) {
                                                            -slotW * 0.35f
                                                        } else {
                                                            0f
                                                        },
                                                        if (dir > 0) {
                                                            slotW
                                                        } else {
                                                            slotW * 1.35f
                                                        }
                                                    )
                                            // The fold follows the finger's real
                                            // height (0.0.994): the old 0.4 height
                                            // damping plus a second smoothing made
                                            // the fold lag and swim; the angle band
                                            // in constrainCurlFinger now keeps the
                                            // fold physical on its own.
                                            val rawY =
                                                (change.position.y -
                                                    slotTop)
                                                    .coerceIn(
                                                        0f, slotH
                                                    )
                                            val cornerPt = Offset(
                                                if (dir > 0) slotW else 0f,
                                                if (curlCornerTop) 0f else slotH
                                            )
                                            val target = constrainCurlFinger(
                                                cornerPt, Offset(rawX, rawY),
                                                intoPositiveX = dir < 0
                                            )
                                            val prev = curlPoint
                                            curlPoint = if (
                                                prev == null
                                            ) {
                                                target
                                            } else {
                                                Offset(
                                                    prev.x +
                                                        (target.x - prev.x) *
                                                        0.55f,
                                                    prev.y +
                                                        (target.y - prev.y) *
                                                        0.55f
                                                )
                                            }
                                            change.consume()
                                        }
                                    }
                                    if (!released) {
                                        curlPoint = null
                                        curlDir = 0
                                        return@awaitEachGesture
                                    }
                                    if (!engaged) {
                                        val wander = maxOf(
                                            kotlin.math.abs(
                                                upPos.x -
                                                    down.position.x
                                            ),
                                            kotlin.math.abs(
                                                upPos.y -
                                                    down.position.y
                                            )
                                        )
                                        if (wander <
                                            viewConfiguration
                                                .touchSlop * 2f
                                        ) {
                                            when {
                                                down.position.x <
                                                    size.width / 3f ->
                                                    turnSpreadBackward()
                                                down.position.x >
                                                    size.width * 2f /
                                                        3f ->
                                                    turnSpreadForward()
                                                else -> {
                                                    chromeVisible =
                                                        !chromeVisible
                                                }
                                            }
                                        }
                                        return@awaitEachGesture
                                    }
                                    val pt = curlPoint ?: Offset(
                                        if (dir > 0) slotW else 0f,
                                        if (curlCornerTop) 0f else slotH
                                    )
                                    val lifted = if (dir > 0) {
                                        (slotW - pt.x) / slotW
                                    } else {
                                        pt.x / slotW
                                    }
                                    val dragDist = kotlin.math.abs(
                                        upPos.x - down.position.x
                                    )
                                    val complete = lifted > 0.32f ||
                                        dragDist > slotW * 0.45f
                                    val cornerY = if (curlCornerTop) 0f else slotH
                                    val tx = if (dir > 0) {
                                        if (complete) -slotW * 0.6f else slotW
                                    } else {
                                        if (complete) slotW * 1.6f else 0f
                                    }
                                    readerScope.launch {
                                        try {
                                            // One parameter drives the settle (0.0.994):
                                            // the virtual finger travels a single straight
                                            // path from the release point to its target,
                                            // angle-clamped like the drag. The old pair of
                                            // independent X/Y springs finished at different
                                            // rates, and the fold line - derived from that
                                            // point - somersaulted across the other page
                                            // instead of the leaf flattening onto it
                                            // (Robert's video).
                                            val settleAnim = Animatable(0f)
                                            val startPt = pt
                                            val targetPt = Offset(tx, cornerY)
                                            val cornerPt = Offset(
                                                if (dir > 0) slotW else 0f, cornerY
                                            )
                                            settleAnim.animateTo(
                                                1f,
                                                spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) {
                                                if (mySeq == curlSeq) {
                                                    val t = value.coerceIn(0f, 1f)
                                                    curlPoint = constrainCurlFinger(
                                                        cornerPt,
                                                        Offset(
                                                            startPt.x +
                                                                (targetPt.x - startPt.x) * t,
                                                            startPt.y +
                                                                (targetPt.y - startPt.y) * t
                                                        ),
                                                        intoPositiveX = dir < 0
                                                    )
                                                }
                                            }
                                            if (complete && mySeq == curlSeq) {
                                                if (dir > 0) {
                                                    turnSpreadForward()
                                                } else {
                                                    turnSpreadBackward()
                                                }
                                            }
                                        } finally {
                                            if (mySeq == curlSeq) {
                                                curlPoint = null
                                                curlDir = 0
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        // Follow-along on the spread
                        // (0.0.987): the current pages'
                        // part offsets + highlight colour.
                        val spokenHl = androidx.compose.material3
                            .MaterialTheme.colorScheme.primary
                            .copy(alpha = 0.16f)
                        val leftStart = spreadPages
                            .take(spreadPage).sumOf { it.size }
                        val rightStart = leftStart +
                            (spreadPages.getOrNull(spreadPage)
                                ?.size ?: 0)
                        Row(
                            modifier = Modifier.fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 14.dp)
                        ) {
                            val slotWPx = (spreadSize.width -
                                with(spreadDensity) { 21.dp.toPx() }) / 2f
                            val slotHPx = spreadSize.height -
                                with(spreadDensity) { 28.dp.toPx() }
                            val pt = curlPoint
                            Box(
                                modifier = Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .background(bg)
                            ) {
                                if (curlDir == -1 && pt != null &&
                                    slotWPx > 0f && slotHPx > 0f
                                ) {
                                    val corner = Offset(
                                        0f,
                                        if (curlCornerTop) 0f else slotHPx
                                    )
                                    val polys = foldPolygons(
                                        slotWPx, slotHPx, corner, pt
                                    )
                                    if (polys.first.isEmpty()) {
                                        SpreadPage(
                                            parts = spreadPages
                                                .getOrNull(spreadPage),
                                            images = book?.images ?: emptyMap(),
                                            title = if (spreadPage == 0) chapter.title else null,
                                            fg = fg,
                                            fontSize = fontSize,
                                            lineMult = lineMult,
                                            fontFamily = fontFamily,
                                            bodyAlign = bodyAlign,
                                            highlightPart = spokenPart ?: -1,
                                            partOffset = leftStart,
                                            highlight = spokenHl,
                                            modifier = Modifier.fillMaxSize()
                                                .padding(horizontal = padDp.dp)
                                        )
                                    } else {
                                        val mid = Offset(
                                            (corner.x + pt.x) / 2f,
                                            (corner.y + pt.y) / 2f
                                        )
                                        val segLen = kotlin.math.hypot(
                                            pt.x - corner.x, pt.y - corner.y
                                        ).coerceAtLeast(1f)
                                        val ux = -(pt.y - corner.y) / segLen
                                        val uy = (pt.x - corner.x) / segLen
                                        val lineDeg = (kotlin.math.atan2(
                                            uy.toDouble(), ux.toDouble()
                                        ) * 180.0 / kotlin.math.PI).toFloat()
                                        val cornerBack = reflectAcross(
                                            corner, mid.x, mid.y, ux, uy
                                        )
                                        if (spreadPageAt(spreadPage - 2) != null) {
                                        CurlPathLayer(
                                            clip = polys.first,
                                            reflectM = null, reflectDeg = null,
                                            tint = null,
                                            pageBg = bg,
                                            shade = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.30f),
                                                    Color.Transparent
                                                ),
                                                start = mid,
                                                end = Offset(
                                                    mid.x + (corner.x - mid.x) * 0.9f,
                                                    mid.y + (corner.y - mid.y) * 0.9f
                                                )
                                            )
                                        ) {
                                            SpreadPage(
                                                parts = spreadPageAt(spreadPage - 2),
                                                images = book?.images ?: emptyMap(),
                                                title = curlTitleAt(spreadPage - 2),
                                                fg = fg,
                                                fontSize = fontSize,
                                                lineMult = lineMult,
                                                fontFamily = fontFamily,
                                                bodyAlign = bodyAlign,
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                        }
                                        CurlPathLayer(
                                            clip = polys.third,
                                            reflectM = null, reflectDeg = null,
                                            tint = null, shade = null,
                                            pageBg = bg
                                        ) {
                                            SpreadPage(
                                                parts = spreadPages
                                                    .getOrNull(spreadPage),
                                                images = book?.images ?: emptyMap(),
                                                title = if (spreadPage == 0) chapter.title else null,
                                                fg = fg,
                                                fontSize = fontSize,
                                                lineMult = lineMult,
                                                fontFamily = fontFamily,
                                                bodyAlign = bodyAlign,
                                                highlightPart = spokenPart ?: -1,
                                                partOffset = leftStart,
                                                highlight = spokenHl,
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                    }
                                } else {
                                    SpreadPage(
                                        parts = spreadPages
                                            .getOrNull(spreadPage),
                                        images = book?.images ?: emptyMap(),
                                        title = if (spreadPage == 0) chapter.title else null,
                                        fg = fg,
                                        fontSize = fontSize,
                                        lineMult = lineMult,
                                        fontFamily = fontFamily,
                                        bodyAlign = bodyAlign,
                                        highlightPart = spokenPart ?: -1,
                                        partOffset = leftStart,
                                        highlight = spokenHl,
                                        modifier = Modifier.fillMaxSize()
                                            .padding(horizontal = padDp.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxHeight()
                                    .width(1.dp)
                                    .background(fg.copy(alpha = 0.18f))
                            )
                            Box(
                                modifier = Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .background(bg)
                            ) {
                                if (curlDir == 1 && pt != null &&
                                    slotWPx > 0f && slotHPx > 0f
                                ) {
                                    val corner = Offset(
                                        slotWPx,
                                        if (curlCornerTop) 0f else slotHPx
                                    )
                                    val polys = foldPolygons(
                                        slotWPx, slotHPx, corner, pt
                                    )
                                    if (polys.first.isEmpty()) {
                                        SpreadPage(
                                            parts = spreadPages
                                                .getOrNull(spreadPage + 1),
                                            images = book?.images ?: emptyMap(),
                                            title = null,
                                            fg = fg,
                                            fontSize = fontSize,
                                            lineMult = lineMult,
                                            fontFamily = fontFamily,
                                            bodyAlign = bodyAlign,
                                            highlightPart = spokenPart ?: -1,
                                            partOffset = rightStart,
                                            highlight = spokenHl,
                                            modifier = Modifier.fillMaxSize()
                                                .padding(horizontal = padDp.dp)
                                        )
                                    } else {
                                        val mid = Offset(
                                            (corner.x + pt.x) / 2f,
                                            (corner.y + pt.y) / 2f
                                        )
                                        val segLen = kotlin.math.hypot(
                                            pt.x - corner.x, pt.y - corner.y
                                        ).coerceAtLeast(1f)
                                        val ux = -(pt.y - corner.y) / segLen
                                        val uy = (pt.x - corner.x) / segLen
                                        val lineDeg = (kotlin.math.atan2(
                                            uy.toDouble(), ux.toDouble()
                                        ) * 180.0 / kotlin.math.PI).toFloat()
                                        val cornerBack = reflectAcross(
                                            corner, mid.x, mid.y, ux, uy
                                        )
                                        if (spreadPageAt(spreadPage + 3) != null) {
                                        CurlPathLayer(
                                            clip = polys.first,
                                            reflectM = null, reflectDeg = null,
                                            tint = null,
                                            pageBg = bg,
                                            shade = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.30f),
                                                    Color.Transparent
                                                ),
                                                start = mid,
                                                end = Offset(
                                                    mid.x + (corner.x - mid.x) * 0.9f,
                                                    mid.y + (corner.y - mid.y) * 0.9f
                                                )
                                            )
                                        ) {
                                            SpreadPage(
                                                parts = spreadPageAt(spreadPage + 3),
                                                images = book?.images ?: emptyMap(),
                                                title = curlTitleAt(spreadPage + 3),
                                                fg = fg,
                                                fontSize = fontSize,
                                                lineMult = lineMult,
                                                fontFamily = fontFamily,
                                                bodyAlign = bodyAlign,
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                        }
                                        CurlPathLayer(
                                            clip = polys.third,
                                            reflectM = null, reflectDeg = null,
                                            tint = null, shade = null,
                                            pageBg = bg
                                        ) {
                                            SpreadPage(
                                                parts = spreadPages
                                                    .getOrNull(spreadPage + 1),
                                                images = book?.images ?: emptyMap(),
                                                title = null,
                                                fg = fg,
                                                fontSize = fontSize,
                                                lineMult = lineMult,
                                                fontFamily = fontFamily,
                                                bodyAlign = bodyAlign,
                                                highlightPart = spokenPart ?: -1,
                                                partOffset = rightStart,
                                                highlight = spokenHl,
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                    }
                                } else {
                                    SpreadPage(
                                        parts = spreadPages
                                            .getOrNull(spreadPage + 1),
                                        images = book?.images ?: emptyMap(),
                                        title = null,
                                        fg = fg,
                                        fontSize = fontSize,
                                        lineMult = lineMult,
                                        fontFamily = fontFamily,
                                        bodyAlign = bodyAlign,
                                        highlightPart = spokenPart ?: -1,
                                        partOffset = rightStart,
                                        highlight = spokenHl,
                                        modifier = Modifier.fillMaxSize()
                                            .padding(horizontal = padDp.dp)
                                    )
                                }
                            }
                        }
                        // The turning sheet's back, drawn
                        // over the WHOLE spread (0.0.989):
                        // a turned leaf lies over the other
                        // page, so its back cannot live
                        // inside its own slot - clipped
                        // there, it piled into the spine and
                        // showed through its own page
                        // (Robert's video). The overlay
                        // layer spans the spread; the page
                        // content sits slot-sized inside it
                        // and the draw transform carries it
                        // to the turning slot's origin.
                        val curlPt = curlPoint
                        if (curlPt != null && curlDir != 0) {
                            val oSlotW = (spreadSize.width -
                                with(spreadDensity) {
                                    21.dp.toPx()
                                }) / 2f
                            val oSlotH = spreadSize.height -
                                with(spreadDensity) {
                                    28.dp.toPx()
                                }
                            if (oSlotW > 0f && oSlotH > 0f) {
                                val turningRight = curlDir == 1
                                val cornerO = Offset(
                                    if (turningRight) oSlotW
                                    else 0f,
                                    if (curlCornerTop) 0f
                                    else oSlotH
                                )
                                val polysO = foldPolygons(
                                    oSlotW, oSlotH, cornerO, curlPt
                                )
                                if (polysO.second.isNotEmpty()) {
                                    val midO = Offset(
                                        (cornerO.x + curlPt.x) / 2f,
                                        (cornerO.y + curlPt.y) / 2f
                                    )
                                    val segO = kotlin.math.hypot(
                                        curlPt.x - cornerO.x,
                                        curlPt.y - cornerO.y
                                    ).coerceAtLeast(1f)
                                    val uxO = -(curlPt.y - cornerO.y) / segO
                                    val uyO = (curlPt.x - cornerO.x) / segO
                                    val degO = (kotlin.math.atan2(
                                        uyO.toDouble(),
                                        uxO.toDouble()
                                    ) * 180.0 / kotlin.math.PI).toFloat()
                                    val backO = reflectAcross(
                                        cornerO, midO.x, midO.y,
                                        uxO, uyO
                                    )
                                    // Foreshortening
                                    // (0.0.99992): the sheet
                                    // back narrows toward
                                    // the fold mid-turn, the
                                    // way a real sheet
                                    // rotating away presents
                                    // a thinner face, and is
                                    // exact again as it lands.
                                    val liftU = if (turningRight) {
                                        (oSlotW - curlPt.x) / oSlotW
                                    } else {
                                        curlPt.x / oSlotW
                                    }.coerceIn(0f, 1f)
                                    val pressK = 0.6f + 0.4f *
                                        kotlin.math.abs(
                                            2f * liftU - 1f
                                        )
                                    val originO = Offset(
                                        with(spreadDensity) {
                                            10.dp.toPx()
                                        } + if (turningRight) {
                                            oSlotW +
                                                with(spreadDensity) {
                                                    1.dp.toPx()
                                                }
                                        } else {
                                            0f
                                        },
                                        with(spreadDensity) {
                                            14.dp.toPx()
                                        }
                                    )
                                    CurlBackOverlay(
                                        origin = originO,
                                        slotW = oSlotW,
                                        slotH = oSlotH,
                                        clip = compressAroundFold(
                                            polysO.second, midO,
                                            uxO, uyO, pressK
                                        ),
                                        reflectM = midO,
                                        reflectDeg = degO,
                                        pressK = pressK,
                                        foldDir = Offset(uxO, uyO),
                                        pageBg = bg,
                                        tint = bg.copy(alpha = 0.30f),
                                        shade = Brush.linearGradient(
                                            colors = listOf(
                                                Color.White
                                                    .copy(alpha = 0.13f),
                                                Color.Transparent
                                            ),
                                            start = midO,
                                            end = Offset(
                                                midO.x + (backO.x - midO.x) * 0.8f,
                                                midO.y + (backO.y - midO.y) * 0.8f
                                            )
                                        )
                                    ) {
                                        SpreadPage(
                                            parts = spreadPageAt(
                                                if (turningRight) {
                                                    spreadPage + 2
                                                } else {
                                                    spreadPage - 1
                                                }
                                            ),
                                            images = book?.images
                                                ?: emptyMap(),
                                            title = curlTitleAt(
                                                if (turningRight) {
                                                    spreadPage + 2
                                                } else {
                                                    spreadPage - 1
                                                }
                                            ),
                                            fg = fg,
                                            fontSize = fontSize,
                                            lineMult = lineMult,
                                            fontFamily = fontFamily,
                                            bodyAlign = bodyAlign,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(
                                                    horizontal = padDp.dp
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        if (spreadPages.isNotEmpty()) {
                            Text(
                                "Pages ${spreadPage + 1} - " +
                                    "${minOf(spreadPage + 2, spreadPages.size)}" +
                                    " of ${spreadPages.size}",
                                color = fg.copy(alpha = 0.55f),
                                style = androidx.compose.material3.MaterialTheme
                                    .typography.bodySmall,
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                            )
                        }
                    }
                }
                chapter != null -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 84.dp, bottom = 118.dp),
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = padDp.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { chromeVisible = !chromeVisible }
                            }
                            .pointerInput(chapterIndex, bookId) {
                                // Swipe to turn (0.0.974): watched in
                                // the initial pass and never consumed,
                                // so the list's own scrolling and the
                                // tap-to-show-chrome are untouched;
                                // the turn is judged at release, and
                                // only when the drag is clearly
                                // horizontal.
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial
                                        )
                                        var totalX = 0f
                                        var totalY = 0f
                                        var change = down
                                        while (change.pressed) {
                                            totalX += change.positionChange().x
                                            totalY += change.positionChange().y
                                            val event = awaitPointerEvent(
                                                PointerEventPass.Initial
                                            )
                                            change = event.changes
                                                .firstOrNull { it.id == down.id }
                                                ?: break
                                        }
                                        val threshold = 72.dp.toPx()
                                        val chapters =
                                            book?.chapters?.size ?: 0
                                        if (kotlin.math.abs(totalX) > threshold &&
                                            kotlin.math.abs(totalX) >
                                            kotlin.math.abs(totalY) * 1.8f
                                        ) {
                                            if (totalX < 0) {
                                                if (chapterIndex <
                                                    chapters - 1
                                                ) {
                                                    chapterIndex++
                                                }
                                            } else if (chapterIndex > 0) {
                                                chapterIndex--
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        item {
                            // Selectable text (0.0.985), scroll
                            // mode only: long-press selects and
                            // copies, like any reading app.
                            SelectionContainer {
                                Text(
                                    chapter.title,
                                    color = fg,
                                    textAlign = bodyAlign,
                                    fontSize = (fontSize + 3).sp,
                                    fontFamily = fontFamily,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                        }
                        itemsIndexed(parts) { partIdx, part ->
                            when (part) {
                                is net.notscape.browser.reader.ReaderPart.Image -> {
                                    val bmp = produceState<android.graphics.Bitmap?>(
                                        initialValue = null, part.token, book
                                    ) {
                                        value = withContext(Dispatchers.IO) {
                                            book?.images?.get(part.token)?.let { bytes ->
                                                decodeSampledBytes(bytes)
                                            }
                                        }
                                    }.value
                                    // An image that cannot be
                                    // resolved renders as nothing -
                                    // never as its raw marker.
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(bottom = (fontSize * 0.6).dp)
                                        )
                                    }
                                }
                                is net.notscape.browser.reader.ReaderPart.Text -> {
                                    SelectionContainer {
                                        Text(
                                            part.text,
                                            color = fg,
                                            textAlign = bodyAlign,
                                            fontSize = fontSize.sp,
                                            lineHeight = (fontSize * lineMult).sp,
                                            fontFamily = fontFamily,
                                            modifier = Modifier
                                                .then(
                                                    if (followActive &&
                                                        spokenPart == partIdx
                                                    ) {
                                                        Modifier.background(
                                                            androidx.compose.material3
                                                                .MaterialTheme
                                                                .colorScheme
                                                                .primary
                                                                .copy(alpha = 0.16f),
                                                            androidx.compose.foundation
                                                                .shape
                                                                .RoundedCornerShape(8.dp)
                                                        )
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .then(
                                                    if (followActive) {
                                                        Modifier.clickable {
                                                            seekToPart(partIdx)
                                                        }
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .padding(bottom = (fontSize * 0.6).dp)
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
        // Player-style chrome (0.0.952): the reader wears the same
        // floating capsules as the video player — a top pill with
        // back, title and the same action glyphs in the same
        // places, floating over the page instead of heading it.
        // A tap on the text hides and shows them, like the
        // player's tap.
        AnimatedVisibility(
            visible = chromeShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().frostBar(Frost.PillShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    book?.title ?: cachedArticle?.title ?: "Reader",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                if (cachedArticle == null && book != null) {
                    IconButton(onClick = { showChapters = true }) {
                        Icon(Icons.Filled.List, contentDescription = "Chapters")
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search in book")
                    }
                    // Bookmark this place (0.0.9992): the
                    // ribbon store, finally on the reading
                    // surface itself instead of only inside
                    // the Chapters sheet. Same position math
                    // and the same per-book JSON.
                    val ribbonsNow = if (bookId != null) {
                        parseRibbons(store.setting("ribbons:$bookId", ""))
                    } else {
                        emptyList()
                    }
                    val withinNow = when {
                        paragraphs.isEmpty() -> 0f
                        spreadActive && spreadPages.isNotEmpty() ->
                            spreadPage.toFloat() / spreadPages.size
                        else -> listState.firstVisibleItemIndex
                            .toFloat() / paragraphs.size
                    }
                    val bookmarkedHere = ribbonsNow.any {
                        it.piece == chapterIndex &&
                            (it.fraction * 100).toInt() ==
                                (withinNow * 100).toInt()
                    }
                    IconButton(onClick = {
                        if (bookId != null && book != null) {
                            if (bookmarkedHere) {
                                store.putSetting(
                                    "ribbons:$bookId",
                                    ribbonsToJson(
                                        ribbonsNow.filterNot {
                                            it.piece == chapterIndex &&
                                                (it.fraction * 100)
                                                    .toInt() ==
                                                    (withinNow * 100)
                                                        .toInt()
                                        }
                                    )
                                )
                            } else {
                                val clean = net.notscape.browser
                                    .reader.stripImageTokens(
                                        book.chapters[chapterIndex].text
                                    )
                                val at = (withinNow * clean.length)
                                    .toInt()
                                    .coerceIn(0, clean.length)
                                val snippet = clean.substring(at)
                                    .replace(Regex("\\s+"), " ")
                                    .trim().take(70)
                                val merged = (
                                    ribbonsNow + Ribbon(
                                        chapterIndex, withinNow, snippet
                                    )
                                    ).distinctBy {
                                        it.piece to
                                            (it.fraction * 100).toInt()
                                    }
                                    .sortedWith(
                                        compareBy(
                                            { it.piece }, { it.fraction }
                                        )
                                    )
                                store.putSetting(
                                    "ribbons:$bookId",
                                    ribbonsToJson(merged)
                                )
                            }
                        }
                    }) {
                        Icon(
                            if (bookmarkedHere) {
                                Icons.Filled.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = if (bookmarkedHere) {
                                "Remove bookmark"
                            } else {
                                "Bookmark this place"
                            }
                        )
                    }
                }
                if (cachedArticle != null && bookSaveAllowed(privateOrigin)) {
                    IconButton(onClick = { saveClipping() }) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save to bookshelf")
                    }
                }
                if (articleUrl != null && cachedArticle == null &&
                    parsed != null && bookSaveAllowed(privateOrigin)
                ) {
                    IconButton(onClick = { saveFetchedArticle() }) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save to bookshelf")
                    }
                }
                IconButton(onClick = { showTypeset = true }) {
                    Icon(Icons.Filled.FormatSize, contentDescription = "Typesetting")
                }
                if (speakable.isNotEmpty()) {
                    IconButton(onClick = {
                        when (net.notscape.browser.tts.TtsState.phase) {
                            net.notscape.browser.tts.TtsPhase.Speaking -> ttsPlayer.pause()
                            net.notscape.browser.tts.TtsPhase.Paused -> ttsPlayer.resume()
                            net.notscape.browser.tts.TtsPhase.Downloading,
                            net.notscape.browser.tts.TtsPhase.Extracting,
                            net.notscape.browser.tts.TtsPhase.Loading -> Unit
                            else -> {
                                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                    androidx.core.content.ContextCompat
                                        .checkSelfPermission(
                                            context,
                                            android.Manifest.permission
                                                .POST_NOTIFICATIONS
                                        ) != android.content.pm.PackageManager
                                        .PERMISSION_GRANTED
                                ) {
                                    notifLauncher.launch(
                                        android.Manifest.permission
                                            .POST_NOTIFICATIONS
                                    )
                                }
                                ttsPlayer.speak(speakable, speakableTitle, readerSourceKey)
                            }
                        }
                    }) {
                        Icon(
                            when (net.notscape.browser.tts.TtsState.phase) {
                                net.notscape.browser.tts.TtsPhase.Speaking ->
                                    Icons.Filled.Pause
                                net.notscape.browser.tts.TtsPhase.Paused ->
                                    Icons.Filled.PlayArrow
                                else -> Icons.Filled.RecordVoiceOver
                            },
                            contentDescription = "Read aloud"
                        )
                    }
                    IconButton(onClick = { showVoice = true }) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = "Voice settings"
                        )
                    }
                }
            }
        }
        if (clippingNote.isNotBlank()) {
            Text(
                clippingNote,
                color = fg,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp)
            )
        }
        if (net.notscape.browser.tts.TtsState.phase !=
            net.notscape.browser.tts.TtsPhase.Idle
        ) {
            // Read-aloud status strip: same floating pill family,
            // parked above the transport pill on books.
            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        start = 12.dp, end = 12.dp,
                        bottom = if (book != null) 78.dp else 12.dp
                    )
                    .fillMaxWidth().frostBar(Frost.PillShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (net.notscape.browser.tts.TtsState.phase) {
                        net.notscape.browser.tts.TtsPhase.Downloading ->
                            "Downloading voice" +
                                (net.notscape.browser.tts.TtsState.progress
                                    .takeIf { it >= 0 }?.let { " - $it%" } ?: "")
                        net.notscape.browser.tts.TtsPhase.Extracting ->
                            "Preparing voice" +
                                (net.notscape.browser.tts.TtsState.progress
                                    .takeIf { it >= 0 }?.let { " - $it%" } ?: "")
                        net.notscape.browser.tts.TtsPhase.Loading ->
                            "Loading voice"
                        net.notscape.browser.tts.TtsPhase.Speaking ->
                            (if (net.notscape.browser.tts.TtsState.nowTitle
                                    .isNotBlank() &&
                                net.notscape.browser.tts.TtsState.nowTitle !=
                                speakableTitle
                            ) {
                                "Reading: " +
                                    net.notscape.browser.tts.TtsState.nowTitle +
                                    " - "
                            } else {
                                ""
                            }) + "Reading aloud - " +
                                net.notscape.browser.tts.TtsState.engineLabel
                        net.notscape.browser.tts.TtsPhase.Paused ->
                            "Paused - " + net.notscape.browser.tts.TtsState.engineLabel
                        else -> net.notscape.browser.tts.TtsState.message
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { ttsPlayer.stop() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop reading")
                }
            }
        }
        // Releasing the scrub slider (0.0.981) jumps the
        // book. The record is written first, so the spread's
        // own seek lands on the same place the scroll effect
        // is aiming for.
        val commitScrub: () -> Unit = {
            val target = scrubFrac
            val bk = book
            if (target != null && bk != null && bookId != null) {
                val total = bk.chapters.size
                val chapterF = (target * total)
                    .coerceIn(0f, total.toFloat())
                val tChapter = chapterF.toInt()
                    .coerceIn(0, total - 1)
                val within = (chapterF - tChapter.toFloat())
                    .coerceIn(0f, 1f)
                if (tChapter == chapterIndex) {
                    if (spreadActive && spreadPages.isNotEmpty()) {
                        val p = (within * spreadPages.size).toInt()
                            .coerceIn(0, spreadPages.size - 1)
                        spreadPage = p - (p % 2)
                    } else if (paragraphs.isNotEmpty()) {
                        val idx = (within * paragraphs.size).toInt()
                            .coerceIn(0, paragraphs.size - 1)
                        readerScope.launch {
                            listState.scrollToItem(idx)
                        }
                    }
                    store.updateBookProgress(
                        bookId, tChapter, within
                    )
                } else {
                    if (!spreadActive) {
                        scrubTarget = tChapter to within
                    }
                    store.updateBookProgress(
                        bookId, tChapter, within
                    )
                    chapterIndex = tChapter
                }
            }
            scrubFrac = null
        }
        if (book != null) {
            // The transport pill: the same Prev / Next glyphs in
            // the same places as the old footer row, now floating
            // over the page like the player's scrubber pill, with
            // a scrub slider in the middle (0.0.981).
            AnimatedVisibility(
                visible = chromeShown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().frostBar(Frost.PillShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (chapterIndex > 0) chapterIndex-- },
                        enabled = chapterIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text(" Prev")
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        val frac = if (paragraphs.isEmpty()) {
                            (chapterIndex + 1).toFloat() / book.chapters.size
                        } else if (spreadActive && spreadPages.isNotEmpty()) {
                            (chapterIndex + spreadPage.toFloat() /
                                spreadPages.size) / book.chapters.size
                        } else {
                            (chapterIndex + listState.firstVisibleItemIndex.toFloat() /
                                paragraphs.size) / book.chapters.size
                        }
                        val scrub = scrubFrac
                        Text(
                            if (scrub != null) {
                                "\u2192 ${(scrub * book.chapters.size).toInt()
                                    .coerceIn(0, book.chapters.size - 1) + 1}" +
                                    " / ${book.chapters.size}"
                            } else {
                                "${chapterIndex + 1} / ${book.chapters.size}"
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = (scrub ?: frac).coerceIn(0f, 1f),
                            onValueChange = { scrubFrac = it },
                            onValueChangeFinished = { commitScrub() },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                                .height(30.dp)
                        )
                        // Time left (0.0.985): character counts
                        // at an average reading pace; hidden
                        // while scrubbing, when the landing
                        // counter takes the space instead.
                        if (scrub == null && bookCharsTotal > 0) {
                            val within = when {
                                paragraphs.isEmpty() -> 0f
                                spreadActive && spreadPages.isNotEmpty() ->
                                    spreadPage.toFloat() / spreadPages.size
                                else -> listState.firstVisibleItemIndex
                                    .toFloat() / paragraphs.size
                            }
                            val bookLeft = formatTimeLeft(
                                ((1f - frac) * bookCharsTotal).toInt()
                            )
                            val pieceLeft = formatTimeLeft(
                                ((1f - within) * pieceChars).toInt()
                            )
                            val label = when {
                                bookLeft.isBlank() -> ""
                                pieceLeft.isBlank() ||
                                    pieceLeft == bookLeft ->
                                    "≈ $bookLeft left"
                                else ->
                                    "≈ $pieceLeft here · $bookLeft in book"
                            }
                            if (label.isNotBlank()) {
                                Text(
                                    label,
                                    style = androidx.compose.material3
                                        .MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.material3
                                        .MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { if (chapterIndex < book.chapters.size - 1) chapterIndex++ },
                        enabled = chapterIndex < book.chapters.size - 1
                    ) {
                        Text("Next ")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }

    // In-book search actions (0.0.984): the scan runs off the
    // main thread; landing mirrors the scrub slider's rules -
    // record first, then the position.
    val runSearch: () -> Unit = {
        val q = searchQuery.trim()
        val bk = book
        if (q.length >= 2 && bk != null && !searchBusy) {
            searchBusy = true
            searchDone = false
            readerScope.launch {
                val hits = withContext(Dispatchers.Default) {
                    searchBook(bk.chapters, q)
                }
                searchHits = hits
                searchBusy = false
                searchDone = true
            }
        }
    }
    val landOnHit: (BookHit) -> Unit = { hit ->
        if (book != null && bookId != null) {
            val within = if (hit.pieceLength > 0) {
                (hit.offset.toFloat() / hit.pieceLength)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            if (hit.piece == chapterIndex) {
                if (spreadActive && spreadPages.isNotEmpty()) {
                    val p = (within * spreadPages.size).toInt()
                        .coerceIn(0, spreadPages.size - 1)
                    spreadPage = p - (p % 2)
                } else if (paragraphs.isNotEmpty()) {
                    val idx = (within * paragraphs.size).toInt()
                        .coerceIn(0, paragraphs.size - 1)
                    readerScope.launch {
                        listState.scrollToItem(idx)
                    }
                }
                store.updateBookProgress(bookId, hit.piece, within)
            } else {
                if (!spreadActive) {
                    scrubTarget = hit.piece to within
                }
                store.updateBookProgress(bookId, hit.piece, within)
                chapterIndex = hit.piece
            }
        }
        showSearch = false
    }

    if (showChapters && book != null) {
        FrostSheet(onDismiss = { showChapters = false }) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                GroupedCard {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Chapters",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        // Ribbons (0.0.985): saved places in
                        // this book, as JSON in the settings
                        // table under ribbons:<bookId>.
                        val ribbonsHere = if (bookId != null) {
                            parseRibbons(store.setting("ribbons:$bookId", ""))
                        } else {
                            emptyList()
                        }
                        ListItem(
                            headlineContent = { Text("Bookmark this place") },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Bookmark,
                                    contentDescription = null
                                )
                            },
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.clickable {
                                if (bookId != null) {
                                    val within = when {
                                        paragraphs.isEmpty() -> 0f
                                        spreadActive &&
                                            spreadPages.isNotEmpty() ->
                                            spreadPage.toFloat() /
                                                spreadPages.size
                                        else -> listState
                                            .firstVisibleItemIndex
                                            .toFloat() / paragraphs.size
                                    }
                                    val clean = net.notscape.browser
                                        .reader.stripImageTokens(
                                            book.chapters[chapterIndex].text
                                        )
                                    val at = (within * clean.length)
                                        .toInt()
                                        .coerceIn(0, clean.length)
                                    val snippet = clean.substring(at)
                                        .replace(Regex("\\s+"), " ")
                                        .trim().take(70)
                                    val merged = (
                                        ribbonsHere + Ribbon(
                                            chapterIndex, within, snippet
                                        )
                                        ).distinctBy {
                                            it.piece to
                                                (it.fraction * 100).toInt()
                                        }
                                        .sortedWith(
                                            compareBy(
                                                { it.piece }, { it.fraction }
                                            )
                                        )
                                    store.putSetting(
                                        "ribbons:$bookId",
                                        ribbonsToJson(merged)
                                    )
                                }
                            }
                        )
                        if (ribbonsHere.isNotEmpty()) {
                            Text(
                                "Bookmarks",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ribbonsHere.forEach { r ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            book.chapters.getOrNull(r.piece)?.title ?: "",
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        if (r.snippet.isNotBlank()) {
                                            Text(
                                                r.snippet, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                if (bookId != null) {
                                                    store.putSetting(
                                                        "ribbons:$bookId",
                                                        ribbonsToJson(
                                                            ribbonsHere - r
                                                        )
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Remove ribbon"
                                            )
                                        }
                                    },
                                    colors = androidx.compose.material3.ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.clickable {
                                        val len = book.chapters
                                            .getOrNull(r.piece)?.text?.length ?: 0
                                        landOnHit(
                                            BookHit(
                                                r.piece,
                                                (r.fraction * len).toInt(),
                                                len, r.snippet
                                            )
                                        )
                                        showChapters = false
                                    }
                                )
                                }
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                            itemsIndexed(book.chapters) { idx, ch ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            ch.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = if (idx == chapterIndex) {
                                                androidx.compose.material3.MaterialTheme.colorScheme.primary
                                            } else Color.Unspecified
                                        )
                                    },
                                    supportingContent = {
                                        val preview = chapterPreview(ch.text)
                                        if (preview.isNotBlank()) {
                                            Text(
                                                preview, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        if (idx == chapterIndex) {
                                            Icon(
                                                Icons.Filled.Check, contentDescription = "Current chapter",
                                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    colors = androidx.compose.material3.ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.clickable {
                                        chapterIndex = idx
                                        showChapters = false
                                        if (bookId != null) store.updateBookProgress(bookId, idx, 0f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearch && book != null) {
        FrostSheet(onDismiss = { showSearch = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                GroupedCard {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Search in this book",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { v ->
                                    searchQuery = v
                                    if (v.isBlank()) {
                                        searchHits = emptyList()
                                        searchDone = false
                                    }
                                },
                                singleLine = true,
                                placeholder = { Text("Word or phrase") },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { runSearch() }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { runSearch() },
                                enabled = !searchBusy &&
                                    searchQuery.trim().length >= 2
                            ) {
                                Text("Search")
                            }
                        }
                        when {
                            searchBusy -> LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                            searchDone && searchHits.isEmpty() -> Text(
                                "No matches in this book",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                            searchDone -> {
                                Text(
                                    if (searchHits.size == 1) "1 match" else "${searchHits.size} matches",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                                    items(searchHits) { hit ->
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    book.chapters.getOrNull(hit.piece)?.title ?: "",
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            supportingContent = {
                                                Text(
                                                    hit.snippet, maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                                containerColor = Color.Transparent
                                            ),
                                            modifier = Modifier.clickable {
                                                landOnHit(hit)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTypeset) {
        FrostSheet(onDismiss = { showTypeset = false }) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                GroupedCard {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "Theme",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            listOf(
                                "white" to "White", "sepia" to "Sepia",
                                "gray" to "Gray", "night" to "Night"
                            ).forEach { (key, label) ->
                                val pal = readerPalette(key)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(48.dp)
                                            .border(
                                                if (themeName == key) 2.5.dp else 1.dp,
                                                if (themeName == key)
                                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                                else androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                            .background(
                                                Color(android.graphics.Color.parseColor(pal.bg)),
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable { store.putSetting("readerTheme", key) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "A",
                                            color = Color(android.graphics.Color.parseColor(pal.fg)),
                                            fontSize = 17.sp
                                        )
                                    }
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        color = if (themeName == key)
                                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Text(
                                "Text size",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    store.putSetting(
                                        "readerFontSize",
                                        (fontSize - 1f).coerceAtLeast(14f).toString()
                                    )
                                }
                            ) { Text("A−", fontSize = 15.sp) }
                            Text("${fontSize.toInt()}", modifier = Modifier.padding(horizontal = 4.dp))
                            TextButton(
                                onClick = {
                                    store.putSetting(
                                        "readerFontSize",
                                        (fontSize + 1f).coerceAtMost(28f).toString()
                                    )
                                }
                            ) { Text("A+", fontSize = 20.sp) }
                        }
                        HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Text(
                                "Font",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("opensans" to "Open Sans", "roboto" to "Roboto", "inter" to "Inter").forEach { (f, label) ->
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                        color = if (fontName == f) {
                                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.clickable { store.putSetting("readerFont", f) }
                                    ) {
                                        Text(
                                            label,
                                            color = if (fontName == f) Color.White
                                            else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            if (readerFontCustomName.isNotBlank()) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    color = if (fontName == "custom") {
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier.clickable {
                                        store.putSetting("readerFont", "custom")
                                    }
                                ) {
                                    Text(
                                        readerFontCustomName,
                                        color = if (fontName == "custom") Color.White
                                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                                            .widthIn(max = 150.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            TextButton(
                                onClick = {
                                    readerFontPicker.launch(arrayOf("*/*"))
                                }
                            ) {
                                Text(
                                    if (readerFontCustomName.isBlank()) {
                                        "Import a custom font"
                                    } else {
                                        "Replace custom font"
                                    }
                                )
                            }
                        }
                        HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Text(
                            "Alignment",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "default" to "Default", "left" to "Left",
                                "center" to "Center", "right" to "Right",
                                "justify" to "Justify"
                            ).forEach { (key, label) ->
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    color = if (alignKey == key) {
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier.clickable {
                                        store.putSetting("readerAlign", key)
                                    }
                                ) {
                                    Text(
                                        label,
                                        color = if (alignKey == key) Color.White
                                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Keep screen awake",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "The screen stays on while this reader is open.",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = keepAwake,
                                onCheckedChange = {
                                    store.putSetting("readerKeepAwake", it.toString())
                                }
                            )
                        }
                        if (book != null) {
                            HorizontalDivider(
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Book spread in landscape",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Two pages side by side when the phone is sideways, about 1,450 characters a page.",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = spreadSetting,
                                    onCheckedChange = {
                                        store.putSetting("readerSpread", it.toString())
                                    }
                                )
                            }
                            HorizontalDivider(
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                            Text(
                                "Line spacing",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Slider(
                                value = lineMult,
                                onValueChange = { store.putSetting("readerLineMult", it.toString()) },
                                valueRange = 1.1f..2.2f,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Text(
                                "Margins",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Slider(
                                value = padDp,
                                onValueChange = { store.putSetting("readerPadding", it.toString()) },
                                valueRange = 8f..44f,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }

            }
        }
    }
    if (showVoice) {
        FrostSheet(onDismiss = { showVoice = false }) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                GroupedCard {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "Voice",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Text(
                                "Voice pack",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("kokoro" to "Kokoro", "piper" to "Piper").forEach { (key, label) ->
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                        color = if (ttsEngineKey == key) {
                                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.clickable {
                                            ttsPlayer.setEngine(key)
                                            ttsPlayer.preview()
                                        }
                                    ) {
                                        Text(
                                            label,
                                            color = if (ttsEngineKey == key) Color.White
                                            else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        val chosenPack = if (ttsEngineKey == "piper") {
                            net.notscape.browser.tts.TtsModels.piper
                        } else {
                            net.notscape.browser.tts.TtsModels.kokoro
                        }
                        val packReady = net.notscape.browser.tts.TtsModels
                            .isInstalled(context, chosenPack)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (packReady) {
                                    chosenPack.label +
                                        " is downloaded - it works fully offline."
                                } else if (net.notscape.browser.tts.TtsState.installing) {
                                    "Downloading " + chosenPack.label +
                                        (net.notscape.browser.tts.TtsState.progress
                                            .takeIf { it >= 0 }
                                            ?.let { " - $it%" } ?: "")
                                } else {
                                    chosenPack.label +
                                        " needs a one-time download (" +
                                        (if (ttsEngineKey == "piper") {
                                            "about 65 MB"
                                        } else {
                                            "about 350 MB"
                                        }) + ")."
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (!packReady) {
                                TextButton(
                                    onClick = { ttsPlayer.installPack(chosenPack) },
                                    enabled = !net.notscape.browser.tts.TtsState.installing
                                ) { Text("Download") }
                            }
                        }
                        HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Text(
                            "Voice",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            net.notscape.browser.tts.TtsVoices
                                .forPack(ttsEngineKey).forEach { voice ->
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                        color = if (ttsVoiceSid == voice.sid) {
                                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.clickable {
                                            ttsPlayer.setVoice(voice.sid)
                                            ttsPlayer.preview()
                                        }
                                    ) {
                                        Text(
                                            voice.name,
                                            color = if (ttsVoiceSid == voice.sid) Color.White
                                            else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                        )
                                    }
                                }
                        }
                        HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Text(
                                "Speed",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    0.75f to "0.75x", 1.0f to "1x",
                                    1.25f to "1.25x", 1.5f to "1.5x",
                                    2.0f to "2x"
                                ).forEach { (speed, label) ->
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                        color = if (ttsSpeedValue == speed) {
                                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        } else {
                                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.clickable {
                                            ttsPlayer.setSpeed(speed)
                                            ttsPlayer.preview()
                                        }
                                    ) {
                                        Text(
                                            label,
                                            color = if (ttsSpeedValue == speed) Color.White
                                            else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        Text(
                            "Sleep timer",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                0 to "Off", 10 to "10 min",
                                20 to "20 min", 30 to "30 min",
                                45 to "45 min", 60 to "60 min"
                            ).forEach { (mins, label) ->
                                val chosen =
                                    net.notscape.browser.tts.TtsState
                                        .sleepMinutes == mins
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    color = if (chosen) {
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier.clickable {
                                        ttsPlayer.setSleepTimer(mins)
                                    }
                                ) {
                                    Text(
                                        label,
                                        color = if (chosen) Color.White
                                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "Tap a voice or a speed to hear a short " +
                                "sample. Read-aloud keeps playing " +
                                "after you leave the reader - pause " +
                                "or stop it from the notification.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}




/**
 * Measured pagination for the book spread (0.0.977): pages
 * are packed by laying the text out with the reader's real
 * style against the real page size, so a page is complete
 * by construction - nothing clips, and nothing hides in a
 * private scroll. A paragraph that straddles a page splits
 * at a line boundary; an image always gets its own page.
 */
private fun paginateMeasured(
    parts: List<net.notscape.browser.reader.ReaderPart>,
    title: String?,
    measurer: androidx.compose.ui.text.TextMeasurer,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    titleStyle: androidx.compose.ui.text.TextStyle,
    contentWidthPx: Int,
    contentHeightPx: Int,
    spacingPx: Int,
    titleGapPx: Int
): List<List<net.notscape.browser.reader.ReaderPart>> {
    if (parts.isEmpty()) return listOf(emptyList())
    val constraints = androidx.compose.ui.unit.Constraints(
        maxWidth = contentWidthPx.coerceAtLeast(1)
    )
    val titleHeight = if (title != null) {
        measurer.measure(
            text = title, style = titleStyle, constraints = constraints
        ).size.height +
            titleGapPx
    } else {
        0
    }
    val pages =
        mutableListOf<List<net.notscape.browser.reader.ReaderPart>>()
    var page = mutableListOf<net.notscape.browser.reader.ReaderPart>()
    var remaining = contentHeightPx - titleHeight
    fun closePage() {
        pages.add(page)
        page = mutableListOf()
        remaining = contentHeightPx
    }
    for (part in parts) {
        when (part) {
            is net.notscape.browser.reader.ReaderPart.Image -> {
                if (page.isNotEmpty()) closePage()
                pages.add(listOf(part))
                remaining = contentHeightPx
            }
            is net.notscape.browser.reader.ReaderPart.Text -> {
                var rest = part.text
                while (rest.isNotEmpty()) {
                    val layout = measurer.measure(
                        text = rest, style = bodyStyle,
                        constraints = constraints
                    )
                    if (layout.size.height <= remaining) {
                        page.add(
                            net.notscape.browser.reader.ReaderPart
                                .Text(rest)
                        )
                        remaining -= layout.size.height + spacingPx
                        rest = ""
                    } else {
                        var lines = 0
                        while (lines < layout.lineCount &&
                            layout.getLineBottom(lines) <= remaining
                        ) {
                            lines++
                        }
                        if (lines == 0 && page.isNotEmpty()) {
                            closePage()
                            continue
                        }
                        if (lines == 0) lines = 1
                        val cut = layout.getLineEnd(lines - 1, true)
                        val head = rest.substring(0, cut).trimEnd()
                        val tail = rest.substring(cut).trimStart()
                        if (cut <= 0 || head.isEmpty() ||
                            tail.isEmpty()
                        ) {
                            if (page.isNotEmpty()) {
                                closePage()
                                continue
                            }
                            page.add(part)
                            remaining = 0
                            rest = ""
                        } else {
                            page.add(
                                net.notscape.browser.reader.ReaderPart
                                    .Text(head)
                            )
                            closePage()
                            rest = tail
                        }
                    }
                }
            }
        }
    }
    if (page.isNotEmpty()) closePage()
    return if (pages.isEmpty()) listOf(emptyList()) else pages
}

/** One fixed page of the landscape book spread (0.0.971). */
@Composable
private fun SpreadPage(
    parts: List<net.notscape.browser.reader.ReaderPart>?,
    images: Map<String, ByteArray>,
    title: String?,
    fg: Color,
    fontSize: Float,
    lineMult: Float,
    fontFamily: FontFamily,
    bodyAlign: TextAlign?,
    highlightPart: Int = -1,
    partOffset: Int = 0,
    highlight: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (title != null) {
            Text(
                title,
                color = fg,
                textAlign = bodyAlign,
                fontSize = (fontSize + 3).sp,
                fontFamily = fontFamily,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        parts?.forEachIndexed { partIdx, part ->
            when (part) {
                is net.notscape.browser.reader.ReaderPart.Text -> {
                    Text(
                        part.text,
                        color = fg,
                        textAlign = bodyAlign,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineMult).sp,
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .then(
                                if (highlightPart ==
                                    partOffset + partIdx
                                ) {
                                    Modifier.background(
                                        highlight,
                                        androidx.compose.foundation
                                            .shape
                                            .RoundedCornerShape(8.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(bottom = (fontSize * 0.6).dp)
                    )
                }
                is net.notscape.browser.reader.ReaderPart.Image -> {
                    val bmp = produceState<android.graphics.Bitmap?>(
                        initialValue = null, part.token, images
                    ) {
                        value = withContext(Dispatchers.IO) {
                            images[part.token]?.let { bytes ->
                                decodeSampledBytes(bytes)
                            }
                        }
                    }.value
                    if (bmp != null) {
                        if (parts?.size == 1) {
                            // A page of its own (0.0.977): the
                            // plate fits INSIDE the page, centred,
                            // instead of overflowing it.
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(
                                        bottom = (fontSize * 0.6).dp
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleWebView(
    article: Article,
    themeKey: String,
    font: String,
    fontSize: Int,
    alignKey: String = "default",
    onOpenUrl: (String) -> Unit
) {
    val palette = readerPalette(themeKey)
    val context = LocalContext.current
    val shell = remember(article, themeKey, font, fontSize, alignKey) {
        buildReaderShell(
            article, themeKey, font, fontSize,
            fontFaceCss = readerFontFaceCss(context, font),
            alignKey = alignKey
        )
    }
    var lastLoaded by remember { mutableStateOf("") }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.loadsImagesAutomatically = true
                applyWebHardening(settings)
                setBackgroundColor(android.graphics.Color.parseColor(palette.bg))
                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val u = url.orEmpty()
                        if (u.startsWith("http://") || u.startsWith("https://")) onOpenUrl(u)
                        return true
                    }
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val u = request?.url?.toString().orEmpty()
                        if (u.startsWith("http://") || u.startsWith("https://")) onOpenUrl(u)
                        return true
                    }
                }
            }
        },
        update = { wv ->
            if (lastLoaded != shell) {
                lastLoaded = shell
                wv.setBackgroundColor(android.graphics.Color.parseColor(palette.bg))
                wv.loadDataWithBaseURL(article.url, shell, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Import a book handed to the app from outside (the system
 * Open-with flow for EPUB files). Mirrors the bookshelf's own
 * picker: the file is copied into the private books folder,
 * parsed, and added to the shelf. Returns a user-facing result
 * line; throws on failure so the caller can show the fallback
 * message instead.
 */
fun importBookFromUri(context: Context, store: AppStore, uri: Uri): String {
    val name = runCatching {
        var display = "book"
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) display = c.getString(0)
        }
        display
    }.getOrDefault("book")
    val ext = name.substringAfterLast('.', "epub").lowercase()
    require(ext !in setOf("mobi", "azw", "azw3", "prc")) {
        "MOBI files are not supported"
    }
    val format = if (ext == "epub") "EPUB" else "TXT"
    val dir = File(context.filesDir, "books").apply { mkdirs() }
    val dest = File(dir, "${System.currentTimeMillis()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { input.copyTo(it) }
    } ?: error("Could not read the selected file")
    val parsed = BookParser.parse(dest)
    val newBookId = System.currentTimeMillis() * 1000 + (0..999).random()
    val coverPath = parsed.cover?.let { bytes ->
        runCatching {
            val coverFile = File(dir, "$newBookId-cover.img")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        }.getOrNull()
    } ?: ""
    store.addBook(
        BookRecord(
            id = newBookId,
            title = parsed.title.ifBlank { name },
            author = parsed.author,
            filePath = dest.absolutePath,
            format = format,
            chapterIndex = 0,
            scrollFraction = 0f,
            addedAt = System.currentTimeMillis(),
            coverPath = coverPath
        )
    )
    return "Added “${parsed.title.ifBlank { name }}” to your bookshelf"
}

// Fold geometry for the corner-follow curl (0.0.982): pure
// functions, unit-tested in CurlGeometryTest. The fold line
// is the perpendicular bisector of the segment from the
// page's outer corner to the reader's finger; the sheet's
// lifted part is the page rect clipped to the corner side
// of that line, its back is that polygon reflected across
// the line, and the flat remainder is the other half.

/** Keeps the half of [poly] on one side of a line. */
internal fun clipHalfPlane(
    poly: List<Offset>,
    mx: Float,
    my: Float,
    nx: Float,
    ny: Float,
    keepNegative: Boolean
): List<Offset> {
    if (poly.isEmpty()) return emptyList()
    fun side(p: Offset): Float = (p.x - mx) * nx + (p.y - my) * ny
    val out = ArrayList<Offset>()
    var prev = poly.last()
    var prevD = side(prev)
    var prevIn = if (keepNegative) prevD <= 0f else prevD >= 0f
    for (cur in poly) {
        val curD = side(cur)
        val curIn = if (keepNegative) curD <= 0f else curD >= 0f
        if (curIn != prevIn) {
            val t = prevD / (prevD - curD)
            out.add(
                Offset(
                    prev.x + (cur.x - prev.x) * t,
                    prev.y + (cur.y - prev.y) * t
                )
            )
        }
        if (curIn) out.add(cur)
        prev = cur
        prevD = curD
        prevIn = curIn
    }
    return out
}

/** Reflects [p] across the line through (mx, my) with unit
 * direction (ux, uy). */
internal fun reflectAcross(
    p: Offset,
    mx: Float,
    my: Float,
    ux: Float,
    uy: Float
): Offset {
    val dx = p.x - mx
    val dy = p.y - my
    val proj = dx * ux + dy * uy
    return Offset(
        mx + 2f * proj * ux - dx,
        my + 2f * proj * uy - dy
    )
}

/**
 * The three polygons of a curling page: (lifted/vacated,
 * reflected sheet-back, flat remainder). Lifted and flat
 * clip to the page rect; the sheet-back clips to the
 * WHOLE SPREAD ([-w, 2w] in this page's space) - a turned
 * leaf lies over the other page, and clipping its back
 * to its own page folded it into the spine (0.0.989,
 * Robert's video). Empty lifted means no curl to draw.
 */
internal fun foldPolygons(
    w: Float,
    h: Float,
    corner: Offset,
    finger: Offset
): Triple<List<Offset>, List<Offset>, List<Offset>> {
    val rect = listOf(
        Offset(0f, 0f), Offset(w, 0f),
        Offset(w, h), Offset(0f, h)
    )
    val dx = finger.x - corner.x
    val dy = finger.y - corner.y
    val len = kotlin.math.hypot(dx, dy)
    if (len < 1f) {
        return Triple(emptyList(), emptyList(), rect)
    }
    val nx = dx / len
    val ny = dy / len
    val mx = (corner.x + finger.x) / 2f
    val my = (corner.y + finger.y) / 2f
    val lifted = clipHalfPlane(rect, mx, my, nx, ny, true)
    val flat = clipHalfPlane(rect, mx, my, nx, ny, false)
    val ux = -ny
    val uy = nx
    var reflected = lifted.map { reflectAcross(it, mx, my, ux, uy) }
    reflected = clipHalfPlane(reflected, -w, 0f, 1f, 0f, false)
    reflected = clipHalfPlane(reflected, 2f * w, 0f, 1f, 0f, true)
    reflected = clipHalfPlane(reflected, 0f, 0f, 0f, 1f, false)
    reflected = clipHalfPlane(reflected, 0f, h, 0f, 1f, true)
    return Triple(lifted, reflected, flat)
}

/**
 * Compresses [poly] toward the fold line (through [mid],
 * direction ([ux], [uy])) by [k] along the fold normal
 * (0.0.99992): the flat reflection model draws the sheet
 * back at full width for the whole turn, where a real
 * rotating sheet presents a foreshortened face mid-turn.
 * Points on the fold line do not move, so the compressed
 * polygon always meets the flat remainder exactly.
 */
internal fun compressAroundFold(
    poly: List<Offset>,
    mid: Offset,
    ux: Float,
    uy: Float,
    k: Float
): List<Offset> {
    if (poly.isEmpty() || k >= 0.999f) return poly
    return poly.map { p ->
        val dx = p.x - mid.x
        val dy = p.y - mid.y
        val along = dx * ux + dy * uy
        val nx = dx - along * ux
        val ny = dy - along * uy
        Offset(
            mid.x + along * ux + nx * k,
            mid.y + along * uy + ny * k
        )
    }
}

/**
 * Keeps a curl's virtual finger physically plausible
 * (0.0.994): the direction from the grabbed corner to the
 * finger stays within [maxDeg] of the horizontal axis
 * pointing into the page, so the fold line - always
 * perpendicular to that direction - stays within the
 * same angle of vertical, the way a real sheet behaves.
 * The drag distance is preserved; only the direction is
 * rotated to the nearest in-band direction.
 */
internal fun constrainCurlFinger(
    corner: Offset,
    finger: Offset,
    intoPositiveX: Boolean,
    maxDeg: Float = 38f
): Offset {
    val dx = finger.x - corner.x
    val dy = finger.y - corner.y
    val len = kotlin.math.hypot(dx, dy)
    if (len < 1f) return finger
    val axisDeg = if (intoPositiveX) 0.0 else 180.0
    var rel = (kotlin.math.atan2(dy.toDouble(), dx.toDouble()) *
        180.0 / kotlin.math.PI) - axisDeg
    while (rel <= -180.0) rel += 360.0
    while (rel > 180.0) rel -= 360.0
    val clamped = rel.coerceIn(
        -maxDeg.toDouble(), maxDeg.toDouble()
    )
    if (clamped == rel) return finger
    val ang = (axisDeg + clamped) * kotlin.math.PI / 180.0
    return Offset(
        corner.x + (len * kotlin.math.cos(ang)).toFloat(),
        corner.y + (len * kotlin.math.sin(ang)).toFloat()
    )
}

// One layer of a curling spread page (0.0.982): the page
// content drawn only inside a polygon of its slot,
// optionally reflected across the fold line - the turning
// sheet's other face - and optionally shaded.
// The reflection conjugates Scale(1, -1) by the fold
// angle: Rot(a) then Scale(1,-1) then Rot(-a) reflects
// across the line at angle a. (0.0.982 shipped Scale(-1, 1)
// here, which reflects across the line PERPENDICULAR to
// the fold - the garbled sheet backs in Robert's video.)
@Composable
private fun CurlPathLayer(
    clip: List<Offset>?,
    reflectM: Offset?,
    reflectDeg: Float?,
    tint: Color?,
    shade: Brush?,
    pageBg: Color? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().drawWithContent {
            val contentScope = this
            val path = clip?.let { poly ->
                if (poly.size < 3) {
                    null
                } else {
                    Path().apply {
                        moveTo(poly[0].x, poly[0].y)
                        for (i in 1 until poly.size) {
                            lineTo(poly[i].x, poly[i].y)
                        }
                        close()
                    }
                }
            }
            if (clip != null && path == null) {
                // A degenerate sliver: draw nothing.
            } else if (path != null) {
                clipPath(path) {
                    if (pageBg != null) {
                        // A turning leaf is paper, not glass
                        // (0.0.9992): without an opaque page
                        // behind the text, the page beneath
                        // showed through the whole leaf.
                        drawRect(color = pageBg)
                    }
                    if (reflectM != null && reflectDeg != null) {
                        withTransform({
                            translate(
                                left = reflectM.x, top = reflectM.y
                            )
                            rotate(
                                degrees = reflectDeg,
                                pivot = Offset.Zero
                            )
                            scale(
                                scaleX = 1f, scaleY = -1f,
                                pivot = Offset.Zero
                            )
                            rotate(
                                degrees = -reflectDeg,
                                pivot = Offset.Zero
                            )
                            translate(
                                left = -reflectM.x, top = -reflectM.y
                            )
                        }) { contentScope.drawContent() }
                    } else {
                        contentScope.drawContent()
                    }
                    if (tint != null) {
                        drawRect(color = tint)
                    }
                    if (shade != null) {
                        drawRect(brush = shade)
                    }
                }
            } else {
                contentScope.drawContent()
                if (tint != null) {
                    drawRect(color = tint)
                }
                if (shade != null) {
                    drawRect(brush = shade)
                }
            }
        }
    ) { content() }
}


// The turning sheet's back over the whole spread
// (0.0.989): a full-spread layer whose draw phase is
// translated to the turning slot's origin, so the
// reflected polygon - computed in slot space, now
// allowed to cross the spine - lands over the other
// page. The content composes slot-sized at the layer's
// top-left; the transform carries it, so nothing about
// the page's own layout changes.
@Composable
private fun CurlBackOverlay(
    origin: Offset,
    slotW: Float,
    slotH: Float,
    clip: List<Offset>,
    reflectM: Offset,
    reflectDeg: Float,
    pageBg: Color,
    tint: Color,
    shade: Brush,
    pressK: Float,
    foldDir: Offset,
    content: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform
        .LocalDensity.current
    Box(
        modifier = Modifier.fillMaxSize().drawWithContent {
            val contentScope = this
            val path = if (clip.size < 3) {
                null
            } else {
                Path().apply {
                    moveTo(clip[0].x, clip[0].y)
                    for (i in 1 until clip.size) {
                        lineTo(clip[i].x, clip[i].y)
                    }
                    close()
                }
            }
            if (path != null) {
                withTransform({
                    translate(left = origin.x, top = origin.y)
                }) {
                    clipPath(path) {
                        drawRect(color = pageBg)
                        withTransform({
                            translate(
                                left = reflectM.x,
                                top = reflectM.y
                            )
                            rotate(
                                degrees = reflectDeg,
                                pivot = Offset.Zero
                            )
                            scale(
                                scaleX = 1f, scaleY = pressK,
                                pivot = Offset.Zero
                            )
                            rotate(
                                degrees = -reflectDeg,
                                pivot = Offset.Zero
                            )
                            translate(
                                left = -reflectM.x,
                                top = -reflectM.y
                            )
                        }) {
                            withTransform({
                                translate(
                                    left = reflectM.x,
                                    top = reflectM.y
                                )
                                rotate(
                                    degrees = reflectDeg,
                                    pivot = Offset.Zero
                                )
                                scale(
                                    scaleX = 1f, scaleY = -1f,
                                    pivot = Offset.Zero
                                )
                                rotate(
                                    degrees = -reflectDeg,
                                    pivot = Offset.Zero
                                )
                                translate(
                                    left = -reflectM.x,
                                    top = -reflectM.y
                                )
                            }) { contentScope.drawContent() }
                        }
                        drawRect(color = tint)
                        drawRect(brush = shade)
                    }
                    // The fold's own edge (0.0.99992): a
                    // soft shadow line with a paper
                    // highlight beside it, drawn whether
                    // or not the sheet carries any text -
                    // a blank page's turn is visible by
                    // its edge alone.
                    val fdx = foldDir.x
                    val fdy = foldDir.y
                    val fa = Offset(
                        reflectM.x - fdx * 4000f,
                        reflectM.y - fdy * 4000f
                    )
                    val fb = Offset(
                        reflectM.x + fdx * 4000f,
                        reflectM.y + fdy * 4000f
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.20f),
                        start = fa, end = fb, strokeWidth = 3f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(
                            fa.x + fdy * 2f, fa.y - fdx * 2f
                        ),
                        end = Offset(
                            fb.x + fdy * 2f, fb.y - fdx * 2f
                        ),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier.size(
                width = with(density) { slotW.toDp() },
                height = with(density) { slotH.toDp() }
            )
        ) { content() }
    }
}
