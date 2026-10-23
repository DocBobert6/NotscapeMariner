package net.notscape.browser.ui

import android.provider.OpenableColumns
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val speakable = if (cachedArticle != null) articleParagraphs else paragraphs
    val speakableTitle = book?.title ?: cachedArticle?.title ?: ""
    val initialItem = remember(bookId, chapterIndex) {
        val frac = if (chapterIndex == (record?.chapterIndex ?: 0)) record?.scrollFraction ?: 0f else 0f
        (frac * paragraphs.size).toInt().coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItem)

    LaunchedEffect(listState, bookId, chapterIndex) {
        if (bookId == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(600)
            .collectLatest { idx ->
                if (paragraphs.isNotEmpty()) {
                    store.updateBookProgress(bookId, chapterIndex, idx.toFloat() / paragraphs.size)
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
                chapter != null -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 84.dp, bottom = 118.dp),
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = padDp.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { chromeVisible = !chromeVisible }
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
                        itemsIndexed(paragraphs) { _, para ->
                            val imgToken = remember(para) {
                                Regex("""\[\[img:(\w+)]]""")
                                    .matchEntire(para.trim())?.groupValues?.get(1)
                            }
                            if (imgToken != null) {
                                val bmp = remember(imgToken, book) {
                                    book?.images?.get(imgToken)?.let { bytes ->
                                        runCatching {
                                            android.graphics.BitmapFactory
                                                .decodeByteArray(bytes, 0, bytes.size)
                                        }.getOrNull()
                                    }
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(bottom = (fontSize * 0.6).dp)
                                    )
                                }
                            } else {
                                Text(
                                    para,
                                    color = fg,
                                    textAlign = bodyAlign,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineMult).sp,
                                    fontFamily = fontFamily,
                                    modifier = Modifier.padding(bottom = (fontSize * 0.6).dp)
                                )
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
        if (book != null) {
            // The transport pill: the same Prev / Next glyphs in
            // the same places as the old footer row, now floating
            // over the page like the player's scrubber pill, with
            // the book's progress as a slim track in the middle.
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
                        Text(
                            "${chapterIndex + 1} / ${book.chapters.size}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        val frac = if (paragraphs.isEmpty()) {
                            (chapterIndex + 1).toFloat() / book.chapters.size
                        } else {
                            (chapterIndex + listState.firstVisibleItemIndex.toFloat() /
                                paragraphs.size) / book.chapters.size
                        }
                        Box(
                            modifier = Modifier.padding(top = 3.dp).width(110.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    androidx.compose.material3.MaterialTheme.colorScheme
                                        .onSurface.copy(alpha = 0.18f)
                                )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(frac.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(
                                        androidx.compose.material3.MaterialTheme.colorScheme
                                            .onSurface
                                    )
                            )
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
                Spacer(Modifier.height(12.dp))
                GroupedCard {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "Read aloud",
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
                                        modifier = Modifier.clickable { ttsPlayer.setEngine(key) }
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
                            "Read-aloud keeps playing after you leave " +
                                "the reader - pause or stop it from " +
                                "the notification.",
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
