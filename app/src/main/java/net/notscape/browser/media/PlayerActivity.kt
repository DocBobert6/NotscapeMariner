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
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.notscape.browser.R
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

@OptIn(ExperimentalMaterial3Api::class)
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
    // Video scaling (Phase C item 6): resize mode + pinch zoom/pan.
    // The PlayerView rides a TextureView (res/layout/player_view.xml)
    // so the graphicsLayer transform below really moves the picture.
    var scaleMode by remember {
        mutableStateOf(
            runCatching {
                ScaleMode.valueOf(store.setting("playerScale", "Fit"))
            }.getOrDefault(ScaleMode.Fit)
        )
    }
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var showScaleRow by remember { mutableStateOf(false) }
    var transformActive by remember { mutableStateOf(false) }
    fun baseScale() = if (scaleMode == ScaleMode.Crop) 1.25f else 1f
    fun effScale() = baseScale() * pinchScale
    fun resetZoom() {
        pinchScale = 1f
        panX = 0f
        panY = 0f
    }
    fun applyScaleMode(m: ScaleMode) {
        scaleMode = m
        store.putSetting("playerScale", m.name)
        resetZoom()
        playerViewRef?.resizeMode = when (m) {
            ScaleMode.Stretch -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            ScaleMode.Fill, ScaleMode.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            ScaleMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }
    LaunchedEffect(currentIndex) { resetZoom() }
    var speed by remember {
        mutableFloatStateOf(store.setting("playerSpeed", "1.0").toFloatOrNull() ?: 1.0f)
    }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var gestureLabel by remember { mutableStateOf("") }
    // Audio / subtitle track selection (Phase D player completion).
    var audioTracks by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
    var textTracks by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
    var selectedAudioId by remember { mutableStateOf<String?>(null) }
    var selectedTextId by remember { mutableStateOf<String?>(null) }
    var subtitlesOff by remember { mutableStateOf(false) }
    var showAudioRow by remember { mutableStateOf(false) }
    var showTextRow by remember { mutableStateOf(false) }
    fun refreshTracks(p: Player) {
        val groups = p.currentTracks.groups
        val audios = mutableListOf<TrackChoice>()
        val texts = mutableListOf<TrackChoice>()
        var selA: String? = null
        var selT: String? = null
        groups.forEachIndexed { gi, g ->
            if (g.type != C.TRACK_TYPE_AUDIO && g.type != C.TRACK_TYPE_TEXT) {
                return@forEachIndexed
            }
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                val fallback = if (g.type == C.TRACK_TYPE_AUDIO) {
                    "Audio ${audios.size + 1}"
                } else {
                    "Subtitles ${texts.size + 1}"
                }
                val label = f.label?.takeIf { it.isNotBlank() }
                    ?: f.language?.takeIf { it.isNotBlank() && it != "und" }
                        ?.let { runCatching { java.util.Locale.forLanguageTag(it).displayLanguage }.getOrNull() }
                    ?: fallback
                val choice = TrackChoice(gi, ti, label, "$gi:$ti")
                if (g.type == C.TRACK_TYPE_AUDIO) {
                    audios.add(choice)
                    if (g.isTrackSelected(ti)) selA = choice.id
                } else {
                    texts.add(choice)
                    if (g.isTrackSelected(ti)) selT = choice.id
                }
            }
        }
        audioTracks = audios
        textTracks = texts
        selectedAudioId = selA
        selectedTextId = selT
        subtitlesOff = texts.isNotEmpty() && selT == null
    }
    fun selectAudio(choice: TrackChoice) {
        val p = activePlayer ?: return
        val group = p.currentTracks.groups.getOrNull(choice.groupIndex) ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
            .build()
        selectedAudioId = choice.id
    }
    fun selectText(choice: TrackChoice?) {
        val p = activePlayer ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        if (choice == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            subtitlesOff = true
            selectedTextId = null
        } else {
            val group = p.currentTracks.groups.getOrNull(choice.groupIndex) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
            subtitlesOff = false
            selectedTextId = choice.id
        }
        p.trackSelectionParameters = builder.build()
    }
    // Hold-to-scan (iOS-style): holding a side of the video scans
    // through it in 3-second steps until release.
    val scanScope = androidx.compose.runtime.rememberCoroutineScope()
    var scanJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun startScan(dir: Int) {
        scanJob?.cancel()
        scanJob = scanScope.launch {
            while (true) {
                val p = activePlayer ?: break
                val dur = if (p.duration == C.TIME_UNSET) 0L else p.duration.coerceAtLeast(0)
                p.seekTo((p.currentPosition + dir * 3_000L).coerceIn(0L, dur))
                gestureLabel = if (dir > 0) "Fast-forward »" else "« Rewind"
                delay(220)
            }
        }
    }
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        if (gestureLabel == "Fast-forward »" || gestureLabel == "« Rewind") {
            gestureLabel = ""
        }
    }
    var seekingPreview by remember { mutableStateOf<Long?>(null) }
    // Controls overlay: shown on tap/interaction, auto-hides while
    // playing so it never sits over the video.
    var controlsVisible by remember { mutableStateOf(true) }
    var lastControlInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var playerError by remember { mutableStateOf<String?>(null) }
    fun pokeControls() {
        controlsVisible = true
        lastControlInteraction = System.currentTimeMillis()
    }
    LaunchedEffect(controlsVisible, isPlaying, listenMode, lastControlInteraction) {
        if (controlsVisible && isPlaying && !listenMode) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // Connect to the playback service and start this queue there. If the
    // service connection fails or stalls, fall back to a local player so
    // pressing Play can never take the app down with it.
    LaunchedEffect(Unit) {
        val idx = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        val resume = store.playbackPosition(urls.getOrNull(idx))
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
                c.setPlaybackSpeed(speed)
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
                    setPlaybackSpeed(speed)
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

                override fun onTracksChanged(tracks: Tracks) {
                    refreshTracks(c)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playerError = "This media couldn't be played — the link may not be a playable stream."
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

    LaunchedEffect(isPlaying) {
        playerViewRef?.keepScreenOn = isPlaying
    }

    LaunchedEffect(playerViewRef) {
        if (playerViewRef != null) applyScaleMode(scaleMode)
    }

    LaunchedEffect(gestureLabel) {
        if (gestureLabel.isNotEmpty()) {
            delay(900)
            gestureLabel = ""
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
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                        this.player = activePlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerViewRef = this
                    }
                },
                update = { pv ->
                    if (pv.player != activePlayer) pv.player = activePlayer
                },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val eff = effScale()
                    scaleX = eff
                    scaleY = eff
                    translationX = panX
                    translationY = panY
                }
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
                    tint = Color.White,
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

        // Gesture layer
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            // Hold-to-scan lives in the SAME tap
                            // detector (Phase G repair): as a separate
                            // pointerInput it swallowed plain taps, so
                            // the controls only ever answered a slide.
                            // Arm after 480ms — a tap or double-tap
                            // releases long before that — then scan
                            // until the press ends.
                            val job = scanScope.launch {
                                delay(480)
                                startScan(if (offset.x < size.width / 2) -1 else 1)
                            }
                            tryAwaitRelease()
                            job.cancel()
                            stopScan()
                        },
                        onDoubleTap = { offset ->
                            val p = activePlayer ?: return@detectTapGestures
                            val w = size.width
                            if (effScale() > 1.01f && offset.x > w / 3 && offset.x < 2 * w / 3) {
                                // Zoomed: a centre double-tap resets the
                                // zoom (side double-taps still seek).
                                resetZoom()
                                gestureLabel = "Zoom reset"
                            } else if (offset.x < w / 2) {
                                p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                                gestureLabel = "-10s"
                            } else {
                                p.seekTo(p.currentPosition + 10_000)
                                gestureLabel = "+10s"
                            }
                        },
                        onTap = {
                            if (controlsVisible) controlsVisible = false else pokeControls()
                        }
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
                            if (transformActive) {
                                // A pinch owns this gesture.
                                change.consume()
                                return@detectDragGestures
                            }
                            if (effScale() > 1.01f) {
                                // Zoomed in: a one-finger drag pans the
                                // picture (seek/brightness/volume return
                                // at 1x).
                                val eff = effScale()
                                val maxX = (size.width * (eff - 1f) / 2f).coerceAtLeast(0f)
                                val maxY = (size.height * (eff - 1f) / 2f).coerceAtLeast(0f)
                                panX = (panX + drag.x).coerceIn(-maxX, maxX)
                                panY = (panY + drag.y).coerceIn(-maxY, maxY)
                                change.consume()
                                return@detectDragGestures
                            }
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
                .pointerInput(Unit) {
                    // Pinch detection: this Compose version's
                    // detectTransformGestures has no start/end callbacks,
                    // so "a pinch is happening" is tracked by counting
                    // pressed pointers directly.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            transformActive = event.changes.count { it.pressed } >= 2
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (!listenMode) {
                            pinchScale = (pinchScale * zoom).coerceIn(1f, 3f)
                            val eff = effScale()
                            val maxX = (size.width * (eff - 1f) / 2f).coerceAtLeast(0f)
                            val maxY = (size.height * (eff - 1f) / 2f).coerceAtLeast(0f)
                            panX = (panX + pan.x).coerceIn(-maxX, maxX)
                            panY = (panY + pan.y).coerceIn(-maxY, maxY)
                            pokeControls()
                        }
                    }
                }
        )

        if (gestureLabel.isNotEmpty()) {
            val isLevel = gestureLabel.startsWith("Volume") || gestureLabel.startsWith("Brightness")
            if (isLevel) {
                // A slim level pill at the top-right, like the reference
                // player's volume control.
                val pct = gestureLabel.filter { it.isDigit() }.toIntOrNull() ?: 0
                Row(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp)
                        .playerCapsule()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (gestureLabel.startsWith("Volume")) Icons.Filled.VolumeUp else Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.width(72.dp).height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Text(
                        gestureLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(12.dp)
                    )
                }
            }
        }

        // Connecting / error states sit in the centre, above the gesture
        // layer and never underneath the transport cluster.
        if (playerError != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.ErrorOutline, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Couldn't play this media",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    playerError ?: "",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = onBack) { Text("Back") }
            }
        } else if (player == null) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Connecting…", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // ---- iOS 26 player layout (per the supplied reference): one
        // frosted capsule of actions at the top-left, separate frosted
        // transport circles in the centre, and at the bottom a title
        // block + options capsule over a slim scrubber. Controls fade
        // together and auto-hide while playing (3.5s). Scrims/frost
        // stand in for blur, which cannot capture a SurfaceView.
        val ready = player != null && playerError == null
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible && playerError == null,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
                    .playerCapsule()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close player", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onEnterPip, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = {
                        activePlayer?.stop()
                        onStopPlayback()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop playback", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }

        // ---- Centre transport: separate frosted circles.
        if (ready) {
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (urls.size > 1) {
                        FrostCircle(size = 48.dp, onClick = {
                            pokeControls()
                            activePlayer?.seekToPreviousMediaItem()
                        }) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    FrostCircle(size = 56.dp, onClick = {
                        pokeControls()
                        activePlayer?.let {
                            it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0))
                        }
                    }) {
                        Icon(
                            Icons.Filled.Replay10,
                            contentDescription = "Back 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    FrostCircle(size = 76.dp, onClick = {
                        pokeControls()
                        if (activePlayer?.isPlaying == true) activePlayer?.pause()
                        else activePlayer?.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    FrostCircle(size = 56.dp, onClick = {
                        pokeControls()
                        activePlayer?.let { it.seekTo(it.currentPosition + 10_000) }
                    }) {
                        Icon(
                            Icons.Filled.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    if (urls.size > 1) {
                        FrostCircle(size = 48.dp, onClick = {
                            pokeControls()
                            activePlayer?.seekToNextMediaItem()
                        }) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Bottom: title + options capsule, then the slim scrubber.
        if (ready) {
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 12.dp)
                ) {
                    if (showScaleRow && !listenMode) {
                        // Scaling modes as a capsule pill row in the same
                        // material, right-aligned over the options capsule.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier.playerCapsule()
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ScaleMode.values().forEach { m ->
                                    TextButton(
                                        onClick = {
                                            pokeControls()
                                            applyScaleMode(m)
                                        }
                                    ) {
                                        Text(
                                            m.name,
                                            color = if (scaleMode == m) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.White
                                            },
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (showAudioRow && audioTracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier.playerCapsule()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                audioTracks.forEach { choice ->
                                    TextButton(
                                        onClick = {
                                            pokeControls()
                                            selectAudio(choice)
                                        }
                                    ) {
                                        Text(
                                            choice.label,
                                            color = if (selectedAudioId == choice.id) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.White
                                            },
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (showTextRow && textTracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier.playerCapsule()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        pokeControls()
                                        selectText(null)
                                    }
                                ) {
                                    Text(
                                        "Off",
                                        color = if (subtitlesOff) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                textTracks.forEach { choice ->
                                    TextButton(
                                        onClick = {
                                            pokeControls()
                                            selectText(choice)
                                        }
                                    ) {
                                        Text(
                                            choice.label,
                                            color = if (!subtitlesOff && selectedTextId == choice.id) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.White
                                            },
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            val host = remember(currentIndex) {
                                runCatching {
                                    android.net.Uri.parse(urls.getOrNull(currentIndex) ?: "").host ?: ""
                                }.getOrDefault("")
                            }
                            if (host.isNotBlank()) {
                                Text(
                                    host,
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                titles.getOrNull(currentIndex) ?: "",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.playerCapsule()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    pokeControls()
                                    listenMode = !listenMode
                                    if (listenMode) {
                                        resetZoom()
                                        showScaleRow = false
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Headphones,
                                    contentDescription = if (listenMode) "Show video" else "Listen mode",
                                    tint = if (listenMode) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (audioTracks.size > 1) {
                                IconButton(
                                    onClick = {
                                        pokeControls()
                                        showAudioRow = !showAudioRow
                                        if (showAudioRow) {
                                            showTextRow = false
                                            showScaleRow = false
                                        }
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Audiotrack,
                                        contentDescription = "Audio track",
                                        tint = if (showAudioRow) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (textTracks.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        pokeControls()
                                        showTextRow = !showTextRow
                                        if (showTextRow) {
                                            showAudioRow = false
                                            showScaleRow = false
                                        }
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Subtitles,
                                        contentDescription = "Subtitles",
                                        tint = if (showTextRow || !subtitlesOff) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (!listenMode) {
                                IconButton(
                                    onClick = {
                                        pokeControls()
                                        showScaleRow = !showScaleRow
                                        if (showScaleRow) {
                                            showAudioRow = false
                                            showTextRow = false
                                        }
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.AspectRatio,
                                        contentDescription = "Video scaling",
                                        tint = if (showScaleRow || scaleMode != ScaleMode.Fit) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    pokeControls()
                                    val steps = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                    val next = steps[(steps.indexOf(speed) + 1) % steps.size]
                                    speed = next
                                    store.putSetting("playerSpeed", next.toString())
                                    activePlayer?.setPlaybackSpeed(next)
                                }
                            ) {
                                Text(
                                    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            TextButton(
                                onClick = {
                                    pokeControls()
                                    val steps = listOf(0, 10, 20, 30, 60)
                                    sleepMinutes = steps[(steps.indexOf(sleepMinutes) + 1) % steps.size]
                                }
                            ) {
                                Text(
                                    if (sleepMinutes == 0) "Sleep" else "${sleepMinutes}m",
                                    color = if (sleepMinutes == 0) Color.White else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Portrait: the scrubber and both times ride in
                    // one floating pill, inset from the edges (the
                    // iOS portrait player arrangement).
                    val isPortrait = androidx.compose.ui.platform.LocalConfiguration.current
                        .orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                    Row(
                        modifier = if (isPortrait) {
                            Modifier.fillMaxWidth().playerCapsule()
                                .padding(horizontal = 14.dp, vertical = 2.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { frac ->
                                pokeControls()
                                if (durationMs > 0) activePlayer?.seekTo((frac * durationMs).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier.size(11.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            },
                            track = {
                                val frac = if (durationMs > 0) {
                                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                                } else 0f
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(frac).fillMaxHeight()
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.White)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        Text(
                            "−" + formatTime((durationMs - positionMs).coerceAtLeast(0)),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * The player's one capsule material over video: near-half black with
 * a fine light hairline — the over-video counterpart of the app's
 * solid-frost recipe (Phase C kit extraction; every player capsule,
 * pill and circle shares this chain).
 */
private fun Modifier.playerCapsule(shape: Shape = RoundedCornerShape(50)): Modifier =
    this.clip(shape)
        .background(Color.Black.copy(alpha = 0.45f))
        .border(0.75.dp, Color.White.copy(alpha = 0.28f), shape)

/**
 * Player scaling modes (Phase C item 6): Fit, Stretch and Fill map
 * straight onto the Media3 resize modes; Crop is Fill (cover) with an
 * extra fixed 1.25x zoom on top, for a stronger centre crop.
 */
private enum class ScaleMode { Fit, Stretch, Fill, Crop }

/** One selectable audio/subtitle track in the player's track rows. */
private data class TrackChoice(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val id: String
)

/** A frosted circular transport button (scrim + hairline, AVKit-style). */
@Composable
private fun FrostCircle(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(size)
            .playerCapsule(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
