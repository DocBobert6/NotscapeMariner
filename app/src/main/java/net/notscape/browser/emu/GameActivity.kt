package net.notscape.browser.emu

import android.content.Context
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.Frost
import net.notscape.browser.ui.SaveStateDialog
import net.notscape.browser.ui.NotscapeTheme
import net.notscape.browser.ui.frostBar

/**
 * The game surface (Phase J): a fullscreen libretro
 * session in the video player's chrome language -
 * floating translucent controls that auto-hide, over a
 * letterboxed GLES picture. Touch controls are real
 * multi-touch (each control tracks its own pointer), and
 * every press ticks with the adjustable haptic strength
 * (setting `gameHaptics`, Light by default).
 */
class GameActivity : ComponentActivity() {

    /** Set by GameScreen; the activity forwards pad
     * keys and axes to it. Touch input is untouched
     * and stays live alongside. */
    var gameKeyRouter: GameKeyRouter? = null
    private val seenGamepads = mutableSetOf<Int>()

    private fun announceGamepad(deviceId: Int) {
        if (!seenGamepads.add(deviceId)) return
        val name = InputDevice.getDevice(deviceId)?.name
            ?: "Controller"
        Toast.makeText(
            this, "Controller connected: $name",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD ==
            InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK ==
            InputDevice.SOURCE_JOYSTICK
        ) {
            announceGamepad(event.deviceId)
            if (gameKeyRouter?.onKey(keyCode, true) == true) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD ==
            InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK ==
            InputDevice.SOURCE_JOYSTICK
        ) {
            if (gameKeyRouter?.onKey(keyCode, false) == true) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK ==
            InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE &&
            gameKeyRouter != null
        ) {
            announceGamepad(event.deviceId)
            gameKeyRouter?.onAxes(
                event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                event.getAxisValue(MotionEvent.AXIS_X),
                event.getAxisValue(MotionEvent.AXIS_Y)
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    companion object {
        const val EXTRA_ROM_PATH = "notscape.emu.ROM_PATH"
        const val EXTRA_GAME_ID = "notscape.emu.GAME_ID"
        const val EXTRA_LOAD_SLOT = "notscape.emu.LOAD_SLOT"
        const val EXTRA_GAME_TITLE = "notscape.emu.GAME_TITLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val romPath = intent.getStringExtra(EXTRA_ROM_PATH) ?: ""
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        val gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE)
        val loadSlot = intent.getIntExtra(
            EXTRA_LOAD_SLOT, SaveStates.NO_SLOT
        )
        // Themed exactly like the player (Robert's rule:
        // the controls match the player's UI, theme
        // included) - NotscapeTheme over the shared store.
        val store = AppStore.get(this)
        intent.getStringExtra(EXTRA_GAME_ID)?.let { gameId ->
            runCatching { GameLibrary.touchPlayed(store, gameId) }
        }
        setContent {
            NotscapeTheme(store) {
                GameScreen(
                    romPath = romPath,
                    gameId = gameId,
                    gameTitle = gameTitle,
                    loadSlot = loadSlot,
                    onExit = { finish() }
                )
            }
        }
    }
}

@Composable
private fun GameScreen(
    romPath: String,
    gameId: String?,
    gameTitle: String?,
    loadSlot: Int,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    // Clean title (J9): the library's display title,
    // never the raw filename. The filename is only
    // a fallback, prettified, for launches without
    // a library entry.
    val romName = remember(romPath, gameTitle) {
        gameTitle?.takeIf { it.isNotBlank() }
            ?: File(romPath).name.substringBeforeLast('.')
                .replace('_', ' ').ifBlank { "Game" }
    }
    val consoleKey = remember(romPath) {
        GameLibrary.systemForExtension(File(romPath).extension)
            ?: "gb"
    }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    // J7: in portrait the picture is top-anchored
    // just below the camera cutout (the native side
    // reads this inset); in landscape it stays
    // centred and the inset is zero.
    val density = LocalDensity.current
    val cutoutTopPx = WindowInsets.displayCutout
        .getTop(density)
    val cutoutTop = with(density) { cutoutTopPx.toDp() }
    val topInsetPx = cutoutTopPx + with(density) {
        8.dp.toPx().toInt()
    }
    LaunchedEffect(topInsetPx, landscape) {
        RetroNative.nativeSetTopInset(
            if (landscape) 0 else topInsetPx
        )
    }
    LaunchedEffect(consoleKey) {
        val preset = ShaderPresets.forConsole(consoleKey)
        val shadersOn =
            store.setting("gameShaders", "false") == "true"
        if (preset.lcd) {
            RetroNative.nativeSetLcdShader(
                shadersOn, preset.brightScan, preset.brightLcd
            )
        } else if (preset.crt) {
            RetroNative.nativeSetCrtShader(shadersOn)
        } else {
            RetroNative.nativeSetShader(
                shadersOn, preset.scan, preset.grid, preset.blur
            )
        }
        // Curved corners (beta.4): the shared
        // toggle plus strength slider, read at
        // session start like the shader choice.
        RetroNative.nativeSetCurve(
            ShaderPresets.curvePull(
                preset,
                store.setting("gameCurvature", "false") == "true",
                store.setting("gameCurvatureAmount", "50")
                    .toIntOrNull() ?: 50
            )
        )
    }
    val layout = remember(consoleKey, landscape) {
        GameLayout.load(store, consoleKey, landscape)
    }
    val controlsAlpha =
        (store.setting("gameControlsAlpha", "100").toIntOrNull()
            ?: 100) / 100f
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf(false) }
    var stateLoaded by remember { mutableStateOf(false) }
    var showStates by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    // The title pill shows at launch only (Robert):
    // visible for the first seconds of the session,
    // then gone even when the controls come back.
    var titleVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4000)
        titleVisible = false
    }
    // FPS readout (per-console setting, off by
    // default): toggled on the console's page before
    // launching; in-game the number lives in the
    // chrome pill beside States. The readout diffs
    // fresh-frame counts twice a second, so it shows
    // the game's real output rate, not redraws.
    var fpsOn by remember {
        mutableStateOf(
            store.setting(
                "gameFpsCounter:$consoleKey", "false"
            ) == "true"
        )
    }
    var fpsText by remember { mutableStateOf("--") }
    // Fast-forward (J9): per-console setting, off by
    // default; the chrome pill toggles it, audio
    // silences while it runs.
    val ffEnabled = remember {
        store.setting("gameFastForward:$consoleKey", "false") ==
            "true"
    }
    var ffOn by remember { mutableStateOf(false) }
    LaunchedEffect(ffOn) {
        RetroNative.nativeSetSpeed(if (ffOn) 4 else 1)
    }
    // Integer scaling (J9): per-console setting,
    // off by default; applied at session start.
    LaunchedEffect(consoleKey) {
        RetroNative.nativeSetIntegerScale(
            store.setting(
                "gameIntegerScale:$consoleKey", "false"
            ) == "true"
        )
    }
    // Portrait migration (J9): once, bottom-anchored
    // portrait defaults replace saved portrait
    // layouts; landscape layouts are untouched.
    LaunchedEffect(Unit) {
        if (store.setting("portraitLayoutVersion", "1") != "2") {
            GameLayout.resetPortrait(store)
            store.putSetting("portraitLayoutVersion", "2")
        }
    }
    // Pause menu (J9): the game idles while the menu
    // is open, and stays idle if save states were
    // opened from it.
    var showPauseMenu by remember { mutableStateOf(false) }
    var showStatesFromMenu by remember {
        mutableStateOf(false)
    }
    val paused = showPauseMenu ||
        (showStates && showStatesFromMenu)
    LaunchedEffect(paused) {
        RetroNative.nativeSetPaused(paused)
    }
    // FPS sampler (moved below `paused`: locals
    // resolve in declaration order). While paused
    // no new frames arrive, so the readout freezes
    // on its last live value instead of dropping
    // to 0; `prev` stays fresh so resume is clean.
    val pausedNow = rememberUpdatedState(paused)
    LaunchedEffect(fpsOn) {
        if (!fpsOn) return@LaunchedEffect
        var prev = RetroNative.nativeGetPresentedFrames()
        while (true) {
            delay(500)
            val cur = RetroNative.nativeGetPresentedFrames()
            if (!pausedNow.value) {
                fpsText = "${(cur - prev) * 2}"
            }
            prev = cur
        }
    }
    // Hide touch controls while a gamepad is
    // connected (J9): tracked from the system's
    // input-device listener, so disconnects bring
    // the buttons straight back.
    var padConnected by remember { mutableStateOf(false) }
    val inputManager = remember {
        context.getSystemService(
            Context.INPUT_SERVICE
        ) as? InputManager
    }
    DisposableEffect(inputManager) {
        if (inputManager == null) {
            return@DisposableEffect onDispose {}
        }
        fun refresh() {
            padConnected = inputManager.inputDeviceIds.any { id ->
                val dev = InputDevice.getDevice(id)
                dev != null && (
                    (dev.sources and
                        InputDevice.SOURCE_GAMEPAD) ==
                        InputDevice.SOURCE_GAMEPAD ||
                    (dev.sources and
                        InputDevice.SOURCE_JOYSTICK) ==
                        InputDevice.SOURCE_JOYSTICK
                )
            }
        }
        refresh()
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                refresh()
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                refresh()
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                refresh()
            }
        }
        inputManager.registerInputDeviceListener(listener, null)
        onDispose {
            inputManager.unregisterInputDeviceListener(listener)
        }
    }
    var lastTouchMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val inputMask = remember { mutableStateOf(0) }

    val vibrator = remember {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    val hapticLevel = remember { store.setting("gameHaptics", "Light") }
    fun tick() {
        val v = vibrator ?: return
        val (ms, amp) = when (hapticLevel) {
            "Off" -> return
            "Medium" -> 18L to 140
            "Strong" -> 26L to 255
            else -> 12L to 60
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }
    fun poke() {
        lastTouchMs = System.currentTimeMillis()
        controlsVisible = true
    }
    val router = remember {
        GameKeyRouter(
            setBit = { bit, on ->
                inputMask.value = if (on) {
                    inputMask.value or (1 shl bit)
                } else {
                    inputMask.value and (1 shl bit).inv()
                }
                RetroNative.nativeSetInput(inputMask.value)
            },
            onActivity = { poke() },
            onTick = { tick() }
        )
    }
    DisposableEffect(router) {
        (context as? GameActivity)?.gameKeyRouter = router
        onDispose {
            (context as? GameActivity)?.gameKeyRouter = null
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (controlsVisible &&
                System.currentTimeMillis() - lastTouchMs > 3000
            ) {
                controlsVisible = false
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (gameId != null) {
                runCatching {
                    SaveStates.autoSave(context, gameId)
                }
            }
            RetroNative.nativeStop()
            RetroNative.nativeDestroy()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            if (!created) {
                                created = true
                                val corePath = CoreStore.corePathFor(ctx, consoleKey)
                                if (corePath == null) {
                                    error = "The emulator core is " +
                                        "missing from this build."
                                    return
                                }
                                val err = RetroNative.nativeCreate(
                                    corePath, romPath,
                                    CoreStore.systemDir(ctx).absolutePath,
                                    CoreStore.saveDir(ctx).absolutePath,
                                    CoreOptions.storedPairs(
                                        store, consoleKey
                                    )
                                )
                                if (err != null) {
                                    error = err
                                    return
                                }
                            }
                            RetroNative.nativeSetSurface(holder.surface)
                            RetroNative.nativeStart()
                            if (!stateLoaded) {
                                stateLoaded = true
                                if (loadSlot != SaveStates.NO_SLOT &&
                                    gameId != null
                                ) {
                                    val f = SaveStates.stateFile(
                                        ctx, gameId, loadSlot
                                    )
                                    if (f.exists()) {
                                        RetroNative.nativeLoadState(
                                            f.absolutePath
                                        )
                                    }
                                }
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int, width: Int, height: Int
                        ) {
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            RetroNative.nativeStop()
                            RetroNative.nativeSetSurface(null)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // A centre tap toggles the controls; it sits under
        // them, so buttons always win their own touches.
        Box(
            modifier = Modifier.fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember {
                        androidx.compose.foundation.interaction
                            .MutableInteractionSource()
                    }
                ) { poke() }
        )

        if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Couldn't start the game",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onExit) { Text("Close") }
            }
        } else if (controlsVisible) {
            GameControls(
                titleVisible = titleVisible,
                chromeTopExtra =
                    if (landscape) 0.dp else cutoutTop,
                romName = romName,
                layout = layout,
                controlsAlpha = controlsAlpha,
                padConnected = padConnected,
                inputMask = inputMask,
                onPoke = { poke() },
                onTick = { tick() },
                onExit = onExit,
                onStates = {
                    showStatesFromMenu = false
                    showStates = true
                },
                onMenu = { showPauseMenu = true },
                ffEnabled = ffEnabled,
                ffOn = ffOn,
                onToggleFf = { ffOn = !ffOn },
                fpsText = if (fpsOn) fpsText else null
            )
        }
        if (showStates && gameId != null) {
            SaveStateDialog(
                gameId = gameId,
                showSave = true,
                batteryExists = false,
                onDeleteBattery = null,
                onLoad = { slot ->
                    SaveStates.load(context, gameId, slot)
                },
                onDismiss = {
                    showStates = false
                    if (showStatesFromMenu) {
                        showStatesFromMenu = false
                        showPauseMenu = true
                    }
                }
            )
        }
        if (showPauseMenu) {
            PauseMenuDialog(
                ffEnabled = ffEnabled,
                ffOn = ffOn,
                onToggleFf = { ffOn = !ffOn },
                onResume = { showPauseMenu = false },
                onSaveStates = {
                    showStatesFromMenu = true
                    showStates = true
                    showPauseMenu = false
                },
                onReset = {
                    RetroNative.nativeReset()
                    showPauseMenu = false
                },
                onExit = onExit,
                onDismiss = { showPauseMenu = false }
            )
        }
    }
}

@Composable
private fun GameControls(
    titleVisible: Boolean,
    chromeTopExtra: Dp,
    romName: String,
    layout: List<ControlSpec>,
    controlsAlpha: Float,
    padConnected: Boolean,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    onExit: () -> Unit,
    onStates: () -> Unit,
    onMenu: () -> Unit,
    ffEnabled: Boolean,
    ffOn: Boolean,
    onToggleFf: () -> Unit,
    fpsText: String?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Title + exit, top edge, player-capsule style.
        Row(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(
                    start = 14.dp, end = 14.dp,
                    top = 14.dp + chromeTopExtra,
                    bottom = 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .frostBar(CircleShape)
                    .clickable { onExit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close game",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (titleVisible) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .frostBar(RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        romName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // States as a small circle directly right of
            // the exit button, with a floppy-disk icon.
            Box(
                modifier = Modifier.size(40.dp)
                    .frostBar(CircleShape)
                    .clickable { onStates() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = "Save states",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            // FPS readout: when enabled on the
            // console's page, the number lives in
            // a circle like States (Robert) - just
            // the number, no "FPS" label.
            if (fpsText != null) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.size(40.dp)
                        .frostBar(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        fpsText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // Fast-forward: only when enabled for
            // this console; lit in the accent colour
            // while it runs.
            if (ffEnabled) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .frostBar(RoundedCornerShape(50))
                        .clickable { onToggleFf() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "FF",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (ffOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            // Pause menu, last in the row.
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(40.dp)
                    .frostBar(CircleShape)
                    .clickable { onMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Pause menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // The controls themselves (J4): the pad renders
        // as ONE pointer surface; everything else keeps
        // its own pointer. Positions and sizes come from
        // the console's saved layout for this orientation.
        // With a gamepad connected (J9) the touch layer
        // hides entirely - the pad is the input now.
        if (padConnected) return@Box
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val padSpecs = layout.filter {
                GameLayout.groupOf(it.id) == "pad"
            }
            if (padSpecs.size == 4) {
                val centres = padSpecs.map { spec ->
                    spec to (w * spec.cx to h * spec.cy)
                }
                val maxR = padSpecs.maxOf { it.sizeDp }.dp / 2
                val minX = centres.minOf { it.second.first } -
                    maxR * 1.3f
                val minY = centres.minOf { it.second.second } -
                    maxR * 1.3f
                val maxX = centres.maxOf { it.second.first } +
                    maxR * 1.3f
                val maxY = centres.maxOf { it.second.second } +
                    maxR * 1.3f
                val pivot = (
                    centres.fold(0.dp) { a, p -> a + p.second.first } / 4
                    ) to (
                    centres.fold(0.dp) { a, p -> a + p.second.second } / 4
                    )
                DpadSurface(
                    visuals = centres.map { (spec, c) ->
                        PadVisual(spec, c.first - minX, c.second - minY)
                    },
                    pivotX = pivot.first - minX,
                    pivotY = pivot.second - minY,
                    inputMask = inputMask,
                    onPoke = onPoke,
                    onTick = onTick,
                    alpha = controlsAlpha,
                    modifier = Modifier.offset(x = minX, y = minY)
                        .size(maxX - minX, maxY - minY)
                )
            }
            for (spec in layout) {
                if (GameLayout.groupOf(spec.id) == "pad") continue
                val widthDp = spec.sizeDp.dp
                val heightDp = if (spec.pill) {
                    (spec.sizeDp * 0.46f).dp
                } else {
                    spec.sizeDp.dp
                }
                val x = w * spec.cx - widthDp / 2
                val y = h * spec.cy - heightDp / 2
                val controlModifier = Modifier.offset(x = x, y = y)
                    .graphicsLayer { this.alpha = controlsAlpha }
                if (spec.pill) {
                    PadPill(
                        spec.label, spec.bit, widthDp, heightDp,
                        inputMask, onPoke, onTick,
                        modifier = controlModifier
                    )
                } else {
                    PadButton(
                        spec.label, spec.bit, spec.sizeDp.dp,
                        inputMask, onPoke, onTick,
                        modifier = controlModifier
                    )
                }
            }
        }
    }
}

/** The pause menu (J9): resume, save states,
 * fast-forward (when enabled for this console),
 * reset, exit. Opening it idles the game; closing
 * it without resuming keeps it idle. */
@Composable
private fun PauseMenuDialog(
    ffEnabled: Boolean,
    ffOn: Boolean,
    onToggleFf: () -> Unit,
    onResume: () -> Unit,
    onSaveStates: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // Kit dialog material (Robert, 2026-10-24:
        // the plain white card looked basic and old):
        // the sheets' 28dp shape + sheet colour, and
        // icon rows instead of centred text buttons.
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = Frost.DialogShape,
            colors = CardDefaults.cardColors(
                containerColor = Frost.sheetColor()
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        horizontal = 20.dp, vertical = 8.dp
                    )
                )
                PauseRow(
                    Icons.Filled.PlayArrow, "Resume", onResume
                )
                PauseRow(
                    Icons.Filled.Save, "Save states", onSaveStates
                )
                if (ffEnabled) {
                    PauseRow(
                        Icons.Filled.FastForward,
                        "Fast-forward",
                        onToggleFf,
                        trailing = {
                            Switch(
                                checked = ffOn,
                                onCheckedChange = { onToggleFf() }
                            )
                        }
                    )
                }
                PauseRow(
                    Icons.Filled.Refresh, "Reset game", onReset
                )
                PauseRow(
                    Icons.Filled.Close, "Exit game", onExit
                )
            }
        }
    }
}

/** One pause-menu row: leading icon + label, full
 * width, left aligned - the kit's row language, not
 * a centred stock-dialog button. */
@Composable
private fun PauseRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** One pad button's position inside the pad surface. */
private data class PadVisual(
    val spec: ControlSpec,
    val centreX: Dp,
    val centreY: Dp
)

/**
 * The D-pad as ONE surface (J4): the four pads render at
 * their layout positions, but a single pointer owns the
 * surface, so the thumb can slide between directions and
 * diagonals register. Direction comes from the angle
 * around the pads' centre pivot (8 sectors, a small dead
 * zone in the middle), which keeps working when the
 * directions are moved apart in the editor.
 */
@Composable
private fun DpadSurface(
    visuals: List<PadVisual>,
    pivotX: Dp,
    pivotY: Dp,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val upBit = visuals.firstOrNull { it.spec.id == "up" }?.spec?.bit ?: 4
    val downBit =
        visuals.firstOrNull { it.spec.id == "down" }?.spec?.bit ?: 5
    val leftBit =
        visuals.firstOrNull { it.spec.id == "left" }?.spec?.bit ?: 6
    val rightBit =
        visuals.firstOrNull { it.spec.id == "right" }?.spec?.bit ?: 7
    val padBits = visuals.fold(0) { a, v -> a or (1 shl v.spec.bit) }
    var activeBits by remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .pointerInput(visuals) {
                val pivot = Offset(pivotX.toPx(), pivotY.toPx())
                fun bitsFor(pos: Offset): Int {
                    val dx = pos.x - pivot.x
                    val dy = pos.y - pivot.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < 14.dp.toPx()) return 0
                    val ang = Math.toDegrees(
                        atan2(dy.toDouble(), dx.toDouble())
                    )
                    return when {
                        ang >= -22.5 && ang < 22.5 -> 1 shl rightBit
                        ang >= 22.5 && ang < 67.5 ->
                            (1 shl downBit) or (1 shl rightBit)
                        ang >= 67.5 && ang < 112.5 -> 1 shl downBit
                        ang >= 112.5 && ang < 157.5 ->
                            (1 shl downBit) or (1 shl leftBit)
                        ang >= 157.5 || ang < -157.5 -> 1 shl leftBit
                        ang >= -157.5 && ang < -112.5 ->
                            (1 shl upBit) or (1 shl leftBit)
                        ang >= -112.5 && ang < -67.5 -> 1 shl upBit
                        else -> (1 shl upBit) or (1 shl rightBit)
                    }
                }
                fun apply(bits: Int) {
                    if (bits != activeBits) {
                        activeBits = bits
                        inputMask.value =
                            (inputMask.value and padBits.inv()) or bits
                        RetroNative.nativeSetInput(inputMask.value)
                        if (bits != 0) onTick()
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPoke()
                    apply(bitsFor(down.position))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change =
                                event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break
                            if (!change.pressed) break
                            apply(bitsFor(change.position))
                        }
                    } finally {
                        apply(0)
                    }
                }
            }
    ) {
        // Connected cross (J9): bars joining the
        // four direction centres, behind the
        // buttons. They stretch with the centres,
        // so the pad stays connected even when the
        // editor moves directions apart.
        val upV = visuals.firstOrNull { it.spec.id == "up" }
        val downV = visuals.firstOrNull { it.spec.id == "down" }
        val leftV = visuals.firstOrNull { it.spec.id == "left" }
        val rightV = visuals.firstOrNull { it.spec.id == "right" }
        if (upV != null && downV != null &&
            leftV != null && rightV != null
        ) {
            val vW = upV.spec.sizeDp.dp
            Box(
                modifier = Modifier.offset(
                    x = pivotX - vW / 2,
                    y = upV.centreY - upV.spec.sizeDp.dp / 2
                ).size(
                    vW,
                    downV.centreY - upV.centreY +
                        downV.spec.sizeDp.dp
                ).frostBar(RoundedCornerShape(50))
            )
            val hH = leftV.spec.sizeDp.dp
            Box(
                modifier = Modifier.offset(
                    x = leftV.centreX - leftV.spec.sizeDp.dp / 2,
                    y = pivotY - hH / 2
                ).size(
                    rightV.centreX - leftV.centreX +
                        rightV.spec.sizeDp.dp,
                    hH
                ).frostBar(RoundedCornerShape(50))
            )
        }
        for (v in visuals) {
            val size = v.spec.sizeDp.dp
            val pressed = activeBits and (1 shl v.spec.bit) != 0
            Box(
                modifier = Modifier.offset(
                    x = v.centreX - size / 2,
                    y = v.centreY - size / 2
                ).size(size).frostBar(CircleShape)
                    .then(
                        if (pressed) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.3f),
                                CircleShape
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    v.spec.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun PadButton(
    label: String,
    bit: Int,
    size: Dp,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    fun setBit(on: Boolean) {
        inputMask.value = if (on) {
            inputMask.value or (1 shl bit)
        } else {
            inputMask.value and (1 shl bit).inv()
        }
        RetroNative.nativeSetInput(inputMask.value)
    }
    Box(
        modifier = modifier.size(size)
            .frostBar(CircleShape)
            .background(
                if (pressed) {
                    MaterialTheme.colorScheme.primary
                        .copy(alpha = 0.30f)
                } else {
                    Color.Transparent
                }
            )
            .pointerInput(bit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    setBit(true)
                    onTick()
                    onPoke()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any {
                                it.id == down.id && it.pressed
                            }
                            if (!anyPressed) break
                        } while (true)
                    } finally {
                        pressed = false
                        setBit(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PadPill(
    label: String,
    bit: Int,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    fun setBit(on: Boolean) {
        inputMask.value = if (on) {
            inputMask.value or (1 shl bit)
        } else {
            inputMask.value and (1 shl bit).inv()
        }
        RetroNative.nativeSetInput(inputMask.value)
    }
    Box(
        modifier = modifier.width(width).height(height)
            .frostBar(RoundedCornerShape(50))
            .background(
                if (pressed) {
                    MaterialTheme.colorScheme.primary
                        .copy(alpha = 0.30f)
                } else {
                    Color.Transparent
                }
            )
            .pointerInput(bit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    setBit(true)
                    onTick()
                    onPoke()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any {
                                it.id == down.id && it.pressed
                            }
                            if (!anyPressed) break
                        } while (true)
                    } finally {
                        pressed = false
                        setBit(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
