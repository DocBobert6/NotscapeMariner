package net.notscape.browser.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var importing by remember { mutableStateOf(false) }

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
                    val format = when (ext) {
                        "epub" -> "EPUB"
                        "mobi", "azw", "azw3", "prc" -> "MOBI"
                        else -> "TXT"
                    }
                    val dir = File(context.filesDir, "books").apply { mkdirs() }
                    val dest = File(dir, "${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    } ?: error("Could not read the selected file")
                    val parsed = BookParser.parse(dest)
                    BookRecord(
                        id = System.currentTimeMillis() * 1000 + (0..999).random(),
                        title = parsed.title.ifBlank { name },
                        author = parsed.author,
                        filePath = dest.absolutePath,
                        format = format,
                        chapterIndex = 0,
                        scrollFraction = 0f,
                        addedAt = System.currentTimeMillis()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookshelf") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        launcher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Import book")
                    }
                }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Text(
                "Import ePub, Mobi, or TXT books. Books are stored on this device.",
                modifier = Modifier.padding(16.dp)
            )
            if (importing) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("  Importing…")
                }
            }
            if (importStatus.isNotBlank()) {
                Text(importStatus, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (store.books.isEmpty()) {
                Text("Your bookshelf is empty.", modifier = Modifier.padding(16.dp))
            }
            LazyColumn {
                items(store.books.toList(), key = { it.id }) { book ->
                    ListItem(
                        headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOf(book.format, book.author, "chapter ${book.chapterIndex + 1}")
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
    onBack: () -> Unit
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
    var showTypeset by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, articleUrl) {
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
    val themeName = store.setting("readerTheme", "light")
    val fontName = store.setting("readerFont", "serif")
    val (bg, fg) = when (themeName) {
        "sepia" -> Color(0xFFF5E9D0) to Color(0xFF3B2F2F)
        "dark" -> Color(0xFF121212) to Color(0xFFE8E8E8)
        else -> Color.White to Color(0xFF141414)
    }
    val fontFamily = when (fontName) {
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }

    val book = parsed
    val chapter = book?.chapters?.getOrNull(chapterIndex)
    val paragraphs = remember(chapter) {
        chapter?.text?.split(Regex("\n{2,}"))?.map { it.replace("\n", " ").trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()
    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        book?.title ?: "Reader",
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showChapters = true }) {
                        Icon(Icons.Filled.List, contentDescription = "Chapters")
                    }
                    IconButton(onClick = { showTypeset = true }) {
                        Icon(Icons.Filled.FormatSize, contentDescription = "Typesetting")
                    }
                }
            )
        },
        bottomBar = {
            if (book != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
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
                    Text("${chapterIndex + 1} / ${book.chapters.size}")
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
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad).background(bg)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                loadError.isNotBlank() -> Text(
                    "Could not open: $loadError",
                    modifier = Modifier.padding(20.dp), color = fg
                )
                chapter != null -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = padDp.dp, vertical = 12.dp)
                    ) {
                        item {
                            Text(
                                chapter.title,
                                color = fg,
                                fontSize = (fontSize + 3).sp,
                                fontFamily = fontFamily,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        itemsIndexed(paragraphs) { _, para ->
                            Text(
                                para,
                                color = fg,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * lineMult).sp,
                                fontFamily = fontFamily,
                                modifier = Modifier.padding(bottom = (fontSize * 0.6).dp)
                            )
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }

    if (showChapters && book != null) {
        AlertDialog(
            onDismissRequest = { showChapters = false },
            title = { Text("Chapters") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(book.chapters) { idx, ch ->
                        ListItem(
                            headlineContent = { Text(ch.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable {
                                chapterIndex = idx
                                showChapters = false
                                if (bookId != null) store.updateBookProgress(bookId, idx, 0f)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChapters = false }) { Text("Close") }
            }
        )
    }

    if (showTypeset) {
        AlertDialog(
            onDismissRequest = { showTypeset = false },
            title = { Text("Typesetting") },
            text = {
                Column {
                    Text("Text size: ${fontSize.toInt()}sp")
                    Slider(
                        value = fontSize,
                        onValueChange = { store.putSetting("readerFontSize", it.toString()) },
                        valueRange = 13f..30f
                    )
                    Text("Line spacing")
                    Slider(
                        value = lineMult,
                        onValueChange = { store.putSetting("readerLineMult", it.toString()) },
                        valueRange = 1.1f..2.2f
                    )
                    Text("Margins")
                    Slider(
                        value = padDp,
                        onValueChange = { store.putSetting("readerPadding", it.toString()) },
                        valueRange = 8f..44f
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Theme")
                    Row {
                        listOf("light", "sepia", "dark").forEach { t ->
                            TextButton(onClick = { store.putSetting("readerTheme", t) }) {
                                Text(
                                    t.replaceFirstChar { it.uppercase() },
                                    color = if (themeName == t) Color(0xFFB8860B) else Color.Unspecified
                                )
                            }
                        }
                    }
                    Text("Font")
                    Row {
                        listOf("serif", "sans", "mono").forEach { f ->
                            TextButton(onClick = { store.putSetting("readerFont", f) }) {
                                Text(
                                    f.replaceFirstChar { it.uppercase() },
                                    color = if (fontName == f) Color(0xFFB8860B) else Color.Unspecified
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTypeset = false }) { Text("Done") }
            }
        )
    }
}
