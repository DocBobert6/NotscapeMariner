package net.notscape.browser.media

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.NotscapeTheme
import java.io.File
import kotlin.math.abs

class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_QUEUE_URLS = "queue_urls"
        private const val EXTRA_QUEUE_TITLES = "queue_titles"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_REFERER = "referer"

        fun launch(context: Context, url: String, title: String, referer: String? = null) {
            val uri = if (url.startsWith("/")) Uri.fromFile(File(url)).toString() else url
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, uri)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_REFERER, referer)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun launchQueue(context: Context, urls: List<String>, titles: List<String>, index: Int, referer: String? = null) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putStringArrayListExtra(EXTRA_QUEUE_URLS, ArrayList(urls))
                    .putStringArrayListExtra(EXTRA_QUEUE_TITLES, ArrayList(titles))
                    .putExtra(EXTRA_INDEX, index)
                    .putExtra(EXTRA_REFERER, referer)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_REFERER)?.let { PlayerNet.referer = it }
        val queueUrls = intent.getStringArrayListExtra(EXTRA_QUEUE_URLS)
        val queueTitles = intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES)
        val urls: List<String>
        val titles: List<String>
        val startIndex: Int
        if (!queueUrls.isNullOrEmpty()) {
            urls = queueUrls
            titles = queueTitles ?: queueUrls.map { it.substringAfterLast('/') }
            startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        } else {
            val u = intent.getStringExtra(EXTRA_URL) ?: intent.dataString ?: ""
            urls = listOf(u)
            titles = listOf(intent.getStringExtra(EXTRA_TITLE) ?: u.substringAfterLast('/'))
            startIndex = 0
        }
        val store = AppStore.get(this)
        setContent {
            NotscapeTheme(store) {
                PlayerScreen(
                    store = store,
                    urls = urls,
                    titles = titles,
                    startIndex = startIndex,
                    onBack = { finish() },
                    onStopPlayback = {
                        runCatching { stopService(Intent(this, PlaybackService::class.java)) }
                        finish()
                    },
                    onEnterPip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            runCatching {
                                enterPictureInPictureMode(
                                    PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(
    store: AppStore,
    urls: List<String>,
    titles: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
    onStopPlayback: () -> Unit,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }
    var localPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    val activePlayer: Player? = controller ?: localPlayer

    fun buildItems(): List<MediaItem> = urls.mapIndexed { i, u ->
        MediaItem.Builder()
            .setUri(u)
            .setMediaId(u)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titles.getOrNull(i) ?: "").build())
            .build()
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentIndex by remember { mutableIntStateOf(startIndex) }
    var listenMode by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var gestureLabel by remember { mutableStateOf("") }
    var seekingPreview by remember { mutableStateOf<Long?>(null) }

    // Connect to the playback service and start this queue there. If the
    // service connection fails or stalls, fall back to a local player so
    // pressing Play can never take the app down with it.
    LaunchedEffect(Unit) {
        val idx = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        val resume = store.playbackPositions[urls.getOrNull(idx)] ?: 0L
        try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                val c = runCatching { future.get() }.getOrNull() ?: return@addListener
                // If the fallback already started, hand over seamlessly.
                val local = localPlayer
                val startIdx = local?.currentMediaItemIndex ?: idx
                val startPos = local?.currentPosition ?: resume
                if (local != null) {
                    local.release()
                    localPlayer = null
                }
                controller = c
                playerViewRef?.player = c
                c.setMediaItems(buildItems(), startIdx, startPos)
                c.prepare()
                c.play()
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            // Fall through to the local-player fallback below.
        }
        launch {
            delay(6_000)
            if (controller == null && localPlayer == null) {
                localPlayer = PlayerNet.buildPlayer(context.applicationContext).apply {
                    setMediaItems(buildItems(), idx, resume)
                    prepare()
                    play()
                }
            }
        }
    }

    DisposableEffect(activePlayer) {
        val c = activePlayer
        if (c == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentIndex = c.currentMediaItemIndex
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        urls.getOrNull(c.currentMediaItemIndex)?.let { store.clearPlaybackPosition(it) }
                    }
                }
            }
            c.addListener(listener)
            onDispose {
                // Save resume point before leaving.
                val u = urls.getOrNull(c.currentMediaItemIndex)
                if (u != null && c.playbackState != Player.STATE_ENDED) {
                    store.setPlaybackPosition(u, c.currentPosition.coerceAtLeast(0))
                }
                c.removeListener(listener)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controllerFuture?.let { MediaController.releaseFuture(it) }
            localPlayer?.release()
        }
    }

    LaunchedEffect(activePlayer) {
        val c = activePlayer ?: return@LaunchedEffect
        var ticks = 0
        while (true) {
            positionMs = c.currentPosition.coerceAtLeast(0)
            durationMs = if (c.duration == androidx.media3.common.C.TIME_UNSET) 0 else c.duration.coerceAtLeast(0)
            ticks++
            if (ticks % 10 == 0) {
                val u = urls.getOrNull(c.currentMediaItemIndex)
                if (u != null && c.playbackState != Player.STATE_ENDED) {
                    store.setPlaybackPosition(u, c.currentPosition.coerceAtLeast(0))
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            activePlayer?.pause()
            sleepMinutes = 0
        }
    }

    val player: Player? = activePlayer

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!listenMode) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = activePlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerViewRef = this
                    }
                },
                update = { pv ->
                    if (pv.player != activePlayer) pv.player = activePlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = Color(0xFFE8C468),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    titles.getOrNull(currentIndex) ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Text("Listen mode — keeps playing with the screen off", color = Color.White.copy(alpha = 0.7f))
            }
        }

        if (player == null) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Text("Connecting to player…", color = Color.White)
            }
        }

        // Gesture layer
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val p = activePlayer ?: return@detectTapGestures
                            val w = size.width
                            if (offset.x < w / 2) {
                                p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                                gestureLabel = "-10s"
                            } else {
                                p.seekTo(p.currentPosition + 10_000)
                                gestureLabel = "+10s"
                            }
                        },
                        onTap = { }
                    )
                }
                .pointerInput(Unit) {
                    var totalDx = 0f
                    var totalDy = 0f
                    var startX = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            totalDx = 0f
                            totalDy = 0f
                            startX = offset.x
                        },
                        onDrag = { change, drag ->
                            val p = activePlayer
                            totalDx += drag.x
                            totalDy += drag.y
                            if (abs(totalDy) > abs(totalDx) && abs(totalDy) > 24f) {
                                val fraction = -totalDy / size.height
                                if (startX < size.width / 2) {
                                    val activity = context as? android.app.Activity
                                    val lp = activity?.window?.attributes
                                    if (lp != null) {
                                        val cur = if (lp.screenBrightness >= 0) lp.screenBrightness else 0.5f
                                        lp.screenBrightness = (cur + fraction * 0.25f).coerceIn(0.05f, 1.0f)
                                        activity.window.attributes = lp
                                        gestureLabel = "Brightness ${(lp.screenBrightness * 100).toInt()}%"
                                    }
                                } else {
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val target = (cur + fraction * max).toInt().coerceIn(0, max)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                    gestureLabel = "Volume ${(target * 100) / max.coerceAtLeast(1)}%"
                                }
                                totalDy = 0f
                            } else if (abs(totalDx) > 60f && p != null) {
                                val seekBy = (totalDx / size.width * 120_000).toLong()
                                val dur = if (p.duration == androidx.media3.common.C.TIME_UNSET) 0 else p.duration.coerceAtLeast(0)
                                seekingPreview = (p.currentPosition + seekBy).coerceIn(0, dur)
                                gestureLabel = "Seek ${formatTime(seekingPreview ?: 0)}"
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            seekingPreview?.let { activePlayer?.seekTo(it) }
                            seekingPreview = null
                            gestureLabel = ""
                        }
                    )
                }
        )

        if (gestureLabel.isNotEmpty()) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Text(
                    gestureLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(12.dp)
                )
            }
        }

        // Controls
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f)).padding(12.dp)
        ) {
            Text(
                titles.getOrNull(currentIndex) ?: "",
                color = Color.White,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(positionMs), color = Color.White)
                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onValueChange = { frac ->
                        if (durationMs > 0) activePlayer?.seekTo((frac * durationMs).toLong())
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(formatTime(durationMs), color = Color.White)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                IconButton(onClick = { activePlayer?.seekToPreviousMediaItem() }, enabled = urls.size > 1) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }
                IconButton(onClick = { if (activePlayer?.isPlaying == true) activePlayer?.pause() else activePlayer?.play() }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { activePlayer?.seekToNextMediaItem() }, enabled = urls.size > 1) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
                }
                IconButton(onClick = { listenMode = !listenMode }) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = "Listen mode",
                        tint = if (listenMode) Color(0xFFE8C468) else Color.White
                    )
                }
                IconButton(onClick = onEnterPip) {
                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White)
                }
                IconButton(onClick = {
                    activePlayer?.stop()
                    onStopPlayback()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop playback", tint = Color.White)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", color = Color.White.copy(alpha = 0.8f))
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                    TextButton(onClick = {
                        speed = s
                        activePlayer?.setPlaybackSpeed(s)
                    }) {
                        Text(
                            "${s}×",
                            color = if (speed == s) Color(0xFFE8C468) else Color.White
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sleep", color = Color.White.copy(alpha = 0.8f))
                listOf(0, 10, 20, 30, 60).forEach { m ->
                    TextButton(onClick = { sleepMinutes = m }) {
                        Text(
                            if (m == 0) "Off" else "${m}m",
                            color = if (sleepMinutes == m) Color(0xFFE8C468) else Color.White
                        )
                    }
                }
                if (sleepMinutes > 0) {
                    Text("Stops in ${sleepMinutes}m", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
