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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.notscape.browser.browser.Article
import net.notscape.browser.browser.ArticleCache
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
import java.io.File

private val parsedCache = mutableMapOf<Long, ParsedBook>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(store: AppStore, onBack: () -> Unit, onOpenBook: (Long) -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importStatus by remember { mutableStateOf("") }
    var bookQuery by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
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
                parsedCache[rec.id] = parsed
                store.addBook(rec)
                importStatus = "Added “${rec.title}” (${parsed.chapters.size} chapters)"
            }.onFailure {
                importStatus = "Import failed: ${it.message ?: "unknown error"}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Bookshelf", onBack = onBack) {
            IconButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = "Import book")
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                Text("Your bookshelf is empty.", modifier = Modifier.padding(16.dp))
            }
            LazyColumn {
                items(shownBooks, key = { it.id }) { book ->
                    ListItem(
                        leadingContent = {
                            if (book.coverPath.isNotBlank()) {
                                val coverBmp = remember(book.coverPath) {
                                    runCatching {
                                        android.graphics.BitmapFactory.decodeFile(book.coverPath)
                                    }.getOrNull()
                                }
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
                        headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOf(book.format, book.author, "Chapter ${book.chapterIndex + 1}")
                                    .filter { it.isNotBlank() }.joinToString(" • ")
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                parsedCache.remove(book.id)
                                store.removeBook(book.id)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove book")
                            }
                        },
                        modifier = Modifier.clickable { onOpenBook(book.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    store: AppStore,
    bookId: Long?,
    articleUrl: String?,
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
                            BookParser.parse(File(rec.filePath)).also { parsedCache[bookId] = it }
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
        else -> FontFamily(Font(R.font.opensans))
    }

    val book = parsed
    // Read-aloud (0.0.964; full build 0.0.968): the text this
    // screen can speak - the article's paragraphs for a cached
    // extraction, else the open chapter's. Playback is global
    // now: leaving the reader or changing chapter no longer
    // stops it; the foreground service keeps it playing and
    // the strip below names what is being read.
    val ttsPlayer = remember { net.notscape.browser.tts.TtsPlayer.get(context) }
    val notifLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts
            .RequestPermission()
    ) { }
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
                                    val rightSlot = down.position.x >=
                                        size.width / 2f
                                    val dir = if (rightSlot) 1 else -1
                                    val allowed = if (dir > 0) {
                                        canSpreadForward
                                    } else {
                                        canSpreadBackward
                                    }
                                    val slotStart = if (rightSlot) {
                                        10.dp.toPx() + slotW +
                                            1.dp.toPx()
                                    } else {
                                        10.dp.toPx()
                                    }
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
                                        if (allowed && !engaged) {
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
                                                engaged = true
                                                curlDir = dir
                                                val localY =
                                                    (down.position.y -
                                                        slotTop)
                                                        .coerceIn(
                                                            0f, slotH
                                                        )
                                                curlCornerTop =
                                                    localY < slotH / 2f
                                                curlPoint = Offset(
                                                    (down.position.x -
                                                        slotStart)
                                                        .coerceIn(
                                                            0f, slotW
                                                        ),
                                                    localY
                                                )
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
                                            val rawY =
                                                (change.position.y -
                                                    slotTop)
                                                    .coerceIn(
                                                        0f, slotH
                                                    )
                                            val prev = curlPoint
                                            curlPoint = if (
                                                prev == null
                                            ) {
                                                Offset(rawX, rawY)
                                            } else {
                                                Offset(
                                                    prev.x +
                                                        (rawX - prev.x) *
                                                        0.55f,
                                                    prev.y +
                                                        (rawY - prev.y) *
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
                                            val ax = Animatable(pt.x)
                                            val ay = Animatable(pt.y)
                                            val spec = spring<Float>(
                                                dampingRatio = 0.82f,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                            coroutineScope {
                                                launch {
                                                    ax.animateTo(tx, spec) {
                                                        if (mySeq == curlSeq) {
                                                            curlPoint =
                                                                Offset(value, ay.value)
                                                        }
                                                    }
                                                }
                                                launch {
                                                    ay.animateTo(cornerY, spec) {
                                                        if (mySeq == curlSeq) {
                                                            curlPoint =
                                                                Offset(ax.value, value)
                                                        }
                                                    }
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
                                        CurlPathLayer(
                                            clip = polys.first,
                                            reflectM = null, reflectDeg = null,
                                            tint = null,
                                            shade = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.20f),
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
                                                parts = spreadPages
                                                    .getOrNull(spreadPage - 2),
                                                images = book?.images ?: emptyMap(),
                                                title = null,
                                                fg = fg,
                                                fontSize = fontSize,
                                                lineMult = lineMult,
                                                fontFamily = fontFamily,
                                                bodyAlign = bodyAlign,
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                        CurlPathLayer(
                                            clip = polys.third,
                                            reflectM = null, reflectDeg = null,
                                            tint = null, shade = null
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
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                        CurlPathLayer(
                                            clip = polys.second,
                                            reflectM = mid,
                                            reflectDeg = lineDeg,
                                            tint = bg.copy(alpha = 0.30f),
                                            shade = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.13f),
                                                    Color.Transparent
                                                ),
                                                start = mid,
                                                end = Offset(
                                                    mid.x + (cornerBack.x - mid.x) *
                                                        0.8f,
                                                    mid.y + (cornerBack.y - mid.y) *
                                                        0.8f
                                                )
                                            )
                                        ) {
                                            SpreadPage(
                                                parts = spreadPages
                                                    .getOrNull(spreadPage - 1),
                                                images = book?.images ?: emptyMap(),
                                                title = null,
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
                                        CurlPathLayer(
                                            clip = polys.first,
                                            reflectM = null, reflectDeg = null,
                                            tint = null,
                                            shade = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.20f),
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
                                                parts = spreadPages
                                                    .getOrNull(spreadPage + 3),
                                                images = book?.images ?: emptyMap(),
                                                title = null,
                                                fg = fg,
                                                fontSize = fontSize,
                                                lineMult = lineMult,
                                                fontFamily = fontFamily,
                                                bodyAlign = bodyAlign,
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                        CurlPathLayer(
                                            clip = polys.third,
                                            reflectM = null, reflectDeg = null,
                                            tint = null, shade = null
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
                                                modifier = Modifier.fillMaxSize()
                                                    .padding(horizontal = padDp.dp)
                                            )
                                        }
                                        CurlPathLayer(
                                            clip = polys.second,
                                            reflectM = mid,
                                            reflectDeg = lineDeg,
                                            tint = bg.copy(alpha = 0.30f),
                                            shade = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.13f),
                                                    Color.Transparent
                                                ),
                                                start = mid,
                                                end = Offset(
                                                    mid.x + (cornerBack.x - mid.x) *
                                                        0.8f,
                                                    mid.y + (cornerBack.y - mid.y) *
                                                        0.8f
                                                )
                                            )
                                        ) {
                                            SpreadPage(
                                                parts = spreadPages
                                                    .getOrNull(spreadPage + 2),
                                                images = book?.images ?: emptyMap(),
                                                title = null,
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
                                        modifier = Modifier.fillMaxSize()
                                            .padding(horizontal = padDp.dp)
                                    )
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
                            Text(
                                chapter.title,
                                color = fg,
                                textAlign = bodyAlign,
                                fontSize = (fontSize + 3).sp,
                                fontFamily = fontFamily,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        itemsIndexed(parts) { _, part ->
                            when (part) {
                                is net.notscape.browser.reader.ReaderPart.Image -> {
                                    val bmp = remember(part.token, book) {
                                        book?.images?.get(part.token)?.let { bytes ->
                                            runCatching {
                                                android.graphics.BitmapFactory
                                                    .decodeByteArray(bytes, 0, bytes.size)
                                            }.getOrNull()
                                        }
                                    }
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
                                    Text(
                                        part.text,
                                        color = fg,
                                        textAlign = bodyAlign,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * lineMult).sp,
                                        fontFamily = fontFamily,
                                        modifier = Modifier.padding(bottom = (fontSize * 0.6).dp)
                                    )
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
                }
                if (cachedArticle != null) {
                    IconButton(onClick = { saveClipping() }) {
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
                                ttsPlayer.speak(speakable, speakableTitle)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            modifier = Modifier.width(176.dp)
                                .height(30.dp)
                        )
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

    if (showChapters && book != null) {
        FrostSheet(onDismiss = { showChapters = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                GroupedCard {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Chapters",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
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

    if (showTypeset) {
        FrostSheet(onDismiss = { showTypeset = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
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
                                        modifier = Modifier.size(44.dp)
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
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
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
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
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
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
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
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
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
        parts?.forEach { part ->
            when (part) {
                is net.notscape.browser.reader.ReaderPart.Text -> {
                    Text(
                        part.text,
                        color = fg,
                        textAlign = bodyAlign,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineMult).sp,
                        fontFamily = fontFamily,
                        modifier = Modifier.padding(bottom = (fontSize * 0.6).dp)
                    )
                }
                is net.notscape.browser.reader.ReaderPart.Image -> {
                    val bmp = remember(part.token, images) {
                        images[part.token]?.let { bytes ->
                            runCatching {
                                android.graphics.BitmapFactory
                                    .decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                    }
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
 * reflected sheet-back, flat remainder), each clipped to
 * the page rect. Empty lifted means no curl to draw.
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
    reflected = clipHalfPlane(reflected, 0f, 0f, 1f, 0f, false)
    reflected = clipHalfPlane(reflected, w, 0f, 1f, 0f, true)
    reflected = clipHalfPlane(reflected, 0f, 0f, 0f, 1f, false)
    reflected = clipHalfPlane(reflected, 0f, h, 0f, 1f, true)
    return Triple(lifted, reflected, flat)
}

// One layer of a curling spread page (0.0.982): the page
// content drawn only inside a polygon of its slot,
// optionally reflected across the fold line - the turning
// sheet's other face - and optionally shaded.
@Composable
private fun CurlPathLayer(
    clip: List<Offset>?,
    reflectM: Offset?,
    reflectDeg: Float?,
    tint: Color?,
    shade: Brush?,
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
                                scaleX = -1f, scaleY = 1f,
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
