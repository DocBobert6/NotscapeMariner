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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.Frost
import net.notscape.browser.ui.SaveStateDialog
import net.notscape.browser.ui.NotscapeTheme
import net.notscape.browser.ui.frostBar
import net.notscape.browser.ui.GameLayoutEditor
import net.notscape.browser.ui.systemLabel

/**
 * The game surface (Phase J): a fullscreen libretro
 * session in the video player's chrome language -
 * floating translucent controls that auto-hide, over a
 * letterboxed GLES picture. Touch controls are real
 * multi-touch (each control tracks its own pointer), and
 * every press ticks with the adjustable haptic strength
 * (setting `gameHaptics:<console>`, falling back to the old shared key, Light by default).
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
                event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RZ)
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    companion object {
        const val EXTRA_ROM_PATH = "notscape.emu.ROM_PATH"
        const val EXTRA_GAME_ID = "notscape.emu.GAME_ID"
        const val EXTRA_LOAD_SLOT = "notscape.emu.LOAD_SLOT"
        const val EXTRA_LOAD_PATH = "notscape.emu.LOAD_PATH"
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
        val loadPath = intent.getStringExtra(EXTRA_LOAD_PATH)
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
                    loadPath = loadPath,
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
    loadPath: String?,
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
    // A library entry's system wins over the file
    // extension: .cue/.m3u/.chd are shared by
    // PlayStation and Sega CD, so the extension
    // alone cannot tell them apart. Launches
    // without a library entry keep the extension
    // fallback.
    val consoleKey = remember(romPath, gameId) {
        val fromLibrary = gameId?.let { id ->
            GameLibrary.load(store)
                .firstOrNull { it.id == id }?.system
        }
        fromLibrary
            ?: GameLibrary.systemForExtension(File(romPath).extension)
            ?: "gb"
    }
    // Disc consoles: PlayStation and Sega CD both
    // expose the libretro disk interface (GX gates
    // eject on Sega CD hardware), so both get the
    // pause menu's Swap disc row.
    val isDiscConsole =
        consoleKey == "ps1" || consoleKey == "segacd"
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
    // DS portrait bezel (beta.31, Robert): the DS
    // picture draws from the very top with just a
    // small fixed bezel, instead of the cutout +
    // 8dp anchor every other console keeps. The
    // punch-hole camera sits over the top screen's
    // top strip; that is intended.
    val dsBezelPx = with(density) { 8.dp.toPx().toInt() }
    val portraitTopInsetPx =
        if (consoleKey == "ds") dsBezelPx else topInsetPx
    val portraitTopInsetDp =
        if (consoleKey == "ds") 8.dp else cutoutTop
    LaunchedEffect(portraitTopInsetPx, landscape) {
        RetroNative.nativeSetTopInset(
            if (landscape) 0 else portraitTopInsetPx
        )
    }
    LaunchedEffect(consoleKey) {
        val preset = ShaderPresets.forGame(
            store::setting, consoleKey, gameId
        )
        val shadersOn =
            store.setting(
                "gameShaders:$consoleKey",
                store.setting("gameShaders", "false")
            ) == "true"
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
        // Per-console color profile (beta.22): rides
        // with the shader toggle, like the other
        // preset values, and is read at session start.
        RetroNative.nativeSetColorProfile(
            preset.color.saturation,
            preset.color.warm,
            preset.color.gamma,
            preset.color.bright,
            preset.color.black,
            preset.color.segaFix
        )
        // Display curvature (beta.4, defaults beta.6):
        // the per-console toggle plus strength
        // slider, read at session start like the
        // shader choice, falling back to the
        // preset's gentle defaults where unset.
        RetroNative.nativeSetCurve(
            ShaderPresets.curvePull(
                preset,
                ShaderPresets.curveOnGame(
                    store::setting, consoleKey, gameId
                ),
                ShaderPresets.curveAmountGame(
                    store::setting, consoleKey, gameId
                )
            )
        )
        // Rounded corners (beta.5, defaults beta.6):
        // the same mesh path, per console,
        // independent of the curve toggle and the
        // shaders toggle.
        RetroNative.nativeSetRound(
            if (ShaderPresets.roundOnGame(
                    store::setting, consoleKey, gameId
                ))
                ShaderPresets.roundAmountGame(
                    store::setting, consoleKey, gameId
                ) / 100f
            else 0f
        )
    }
    // Keyed on store.revision too, so a layout
    // saved from the in-game editor applies live
    // instead of waiting for a rotation or relaunch.
    val layout = remember(consoleKey, landscape, store.revision) {
        GameLayout.load(store, consoleKey, landscape)
    }
    val controlsAlpha =
        (store.setting(
            "gameControlsAlpha:$consoleKey",
            store.setting("gameControlsAlpha", "100")
        ).toIntOrNull() ?: 100) / 100f
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
    // Collapsed chrome (restored, beta.32): the
    // expand pill toggles the top pills; Back opens
    // the pause menu, as it did in beta.31.
    var chromeExpanded by remember { mutableStateOf(false) }
    // FPS readout (per-console setting, off by
    // default): toggled on the console's page before
    // launching; in-game the number is its own
    // always-on overlay, top-right. The readout diffs
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
    // N3 disc picker state: count refreshed whenever
    // the pause menu opens; labels/index filled when
    // the Swap disc row is tapped.
    var showDiscPicker by remember { mutableStateOf(false) }
    var discCount by remember { mutableStateOf(0) }
    var discLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var discIndex by remember { mutableStateOf(-1) }
    var showStatesFromMenu by remember {
        mutableStateOf(false)
    }
    var showEditor by remember { mutableStateOf(false) }
    // DS (dual-screen) session state. Arrangement and
    // swap persist per orientation, like layouts; the
    // PiP corner, upper-tap, lid and mic settings are
    // shared across orientations.
    var dsArrangement by remember(consoleKey, landscape) {
        mutableStateOf(
            DsLayout.arrangementFor(
                { key, default -> store.setting(key, default) },
                landscape
            )
        )
    }
    var dsSwapped by remember(consoleKey, landscape) {
        mutableStateOf(
            store.setting(DsLayout.swapKey(landscape), "false") ==
                "true"
        )
    }
    var dsPipCorner by remember(consoleKey) {
        mutableStateOf(
            DsLayout.pipCornerFor(
                { key, default -> store.setting(key, default) }
            )
        )
    }
    var dsUpperTap by remember(consoleKey) {
        mutableStateOf(
            DsLayout.upperTapFor(
                { key, default -> store.setting(key, default) }
            )
        )
    }
    var dsLid by remember(consoleKey) {
        mutableStateOf(
            DsLayout.lidFor(
                { key, default -> store.setting(key, default) }
            )
        )
    }
    var dsMic by remember(consoleKey) {
        mutableStateOf(
            DsLayout.micFor(
                { key, default -> store.setting(key, default) }
            )
        )
    }
    var dsForceStacked by remember(consoleKey) {
        mutableStateOf(
            DsLayout.forceStackedFor(
                { key, default -> store.setting(key, default) }
            )
        )
    }
    var dsInteger by remember(consoleKey) {
        mutableStateOf(
            store.setting(
                "gameIntegerScale:$consoleKey", "false"
            ) == "true"
        )
    }
    var dsMicApplied by remember { mutableStateOf(false) }
    var dsFocusFallbackToasted by remember {
        mutableStateOf(false)
    }
    // Plain showStates pauses too: the chrome
    // States button used to open over a game that
    // kept running underneath the dialog.
    val paused = showPauseMenu ||
        showStates ||
        showEditor ||
        showDiscPicker
    LaunchedEffect(paused) {
        RetroNative.nativeSetPaused(paused)
    }
    // N3: disc count for the pause menu's Swap disc
    // row, refreshed every time the menu opens.
    LaunchedEffect(showPauseMenu) {
        if (showPauseMenu && isDiscConsole) {
            discCount = RetroNative.nativeDiskGetCount()
        }
    }
    // Crash autosave (beta.7): every 5 minutes of
    // live play, the current run's auto-state is
    // rewritten, so a crash loses at most ~5
    // minutes. Pausing restarts the wait (the
    // effect re-runs on `paused`); leaving the game
    // saves on dispose as before.
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            delay(5 * 60 * 1000L)
            if (gameId != null) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        SaveStates.autoSave(context, gameId)
                    }
                }
            }
        }
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

    // Analogue sticks (beta.17, PS1): the four int16
    // axes, kept beside the button mask and pushed
    // on every stick move.
    val analogValues = remember { IntArray(4) }
    fun reportAnalog(stick: Int, x: Float, y: Float) {
        val base = stick * 2
        if (base < 0 || base + 1 >= analogValues.size) return
        analogValues[base] = AnalogInput.axis(x)
        analogValues[base + 1] = AnalogInput.axis(y)
        RetroNative.nativeSetAnalog(
            analogValues[0], analogValues[1],
            analogValues[2], analogValues[3]
        )
    }

    val vibrator = remember {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    val hapticLevel = remember {
        store.setting(
            "gameHaptics:$consoleKey",
            store.setting("gameHaptics", "Light")
        )
    }
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
    // Back opens the pause menu (beta.31, Robert):
    // system Back (gesture or key) while playing
    // opens the menu; the game already idles while
    // it is open. The pill row itself expands and
    // collapses only by tapping the expand pill, as
    // it used to. Back inside the menu or any dialog
    // is handled by the dialog itself, so this stays
    // disabled while anything is open.
    BackHandler(
        enabled = !showPauseMenu && !showStates &&
            !showEditor && !showDiscPicker && error == null
    ) {
        showPauseMenu = true
    }
    // One-time Back-menu hint: a transient toast on
    // the first game launch after this change, then
    // never again (flagged in settings).
    LaunchedEffect(Unit) {
        if (store.setting("gameBackHintShown", "false") != "true") {
            Toast.makeText(
                context, "Back opens the pause menu",
                Toast.LENGTH_LONG
            ).show()
            store.putSetting("gameBackHintShown", "true")
        }
    }
    fun setDsBit(bit: Int, on: Boolean) {
        inputMask.value = if (on) {
            inputMask.value or (1 shl bit)
        } else {
            inputMask.value and (1 shl bit).inv()
        }
        RetroNative.nativeSetInput(inputMask.value)
    }
    fun swapDsScreens() {
        if (dsArrangement == DsArrangement.FOCUS_TOP) {
            dsArrangement = DsArrangement.FOCUS_BOTTOM
            store.putSetting(
                DsLayout.arrangementKey(landscape), "FOCUS_BOTTOM"
            )
        } else if (dsArrangement == DsArrangement.FOCUS_BOTTOM) {
            dsArrangement = DsArrangement.FOCUS_TOP
            store.putSetting(
                DsLayout.arrangementKey(landscape), "FOCUS_TOP"
            )
        } else {
            dsSwapped = !dsSwapped
            store.putSetting(
                DsLayout.swapKey(landscape), dsSwapped.toString()
            )
        }
    }
    fun cycleDsLayout() {
        val next =
            DsLayout.nextArrangement(dsArrangement, landscape)
        dsArrangement = next
        store.putSetting(
            DsLayout.arrangementKey(landscape), next.name
        )
    }
    // DS microphone: the pinned core has no real-mic
    // input, so the toggle holds the L2 bit, which
    // the core reads as synthetic mic noise. The bit
    // is held only while the toggle is on and the
    // game is running, so it mutes on pause and
    // resumes with the game. Gamepad L2 holds while
    // the toggle is off are never touched.
    LaunchedEffect(dsMic, paused, inputMask.value) {
        if (consoleKey != "ds") return@LaunchedEffect
        if (dsMic && !paused) {
            if ((inputMask.value and (1 shl DsLayout.MIC_BIT)) == 0) {
                setDsBit(DsLayout.MIC_BIT, true)
            }
            dsMicApplied = true
        } else if (dsMicApplied && (paused || !dsMic)) {
            if ((inputMask.value and (1 shl DsLayout.MIC_BIT)) != 0) {
                setDsBit(DsLayout.MIC_BIT, false)
            }
            if (!dsMic) dsMicApplied = false
        }
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
    // Physical gamepad analogue (F1): PS1 with an
    // analogue variant drives the core's sticks; any
    // other console (or Digital) keeps D-pad mapping.
    val analogVariant = remember(consoleKey) {
        if (consoleKey == "ps1") {
            GameLayout.controllerVariant(store, "ps1")
        } else {
            "Digital"
        }
    }
    LaunchedEffect(router, analogVariant) {
        router.analogMode =
            consoleKey == "ps1" && analogVariant != "Digital"
        router.onAnalog = { stick, x, y ->
            reportAnalog(stick, x, y)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (controlsVisible &&
                System.currentTimeMillis() - lastTouchMs > 10000
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        val canvasWpx = with(density) { maxWidth.toPx() }
        val canvasHpx = with(density) { maxHeight.toPx() }
        // DS geometry in surface pixels: the design's
        // dp budgets converted at this density, so the
        // reference budgets reproduce exactly there.
        val dsGeometry = remember(
            consoleKey, landscape, dsArrangement, dsSwapped,
            dsPipCorner, dsInteger, dsForceStacked,
            maxWidth, maxHeight, portraitTopInsetPx
        ) {
            if (consoleKey != "ds") null else DsLayout.geometry(
                canvasWpx, canvasHpx, landscape, dsArrangement,
                dsSwapped, dsPipCorner, dsInteger,
                if (landscape) 0f else portraitTopInsetPx.toFloat(),
                with(density) { 16.dp.toPx() },
                with(density) { 84.dp.toPx() },
                with(density) {
                    if (landscape) 183.dp.toPx() else 146.dp.toPx()
                },
                with(density) {
                    if (landscape) 137.dp.toPx() else 110.dp.toPx()
                },
                with(density) { 91.dp.toPx() },
                with(density) { 183.dp.toPx() },
                dsForceStacked
            )
        }
        LaunchedEffect(dsGeometry) {
            val g = dsGeometry
            if (g == null) {
                RetroNative.nativeSetDsLayout(false, null)
            } else {
                val floats = FloatArray(16)
                for ((i, quad) in g.quads.withIndex()) {
                    DsLayout.ndcQuad(
                        quad.rect, quad.content, canvasWpx, canvasHpx
                    ).copyInto(floats, i * 8)
                }
                RetroNative.nativeSetDsLayout(true, floats)
            }
        }
        LaunchedEffect(dsGeometry?.focusFallback) {
            if (dsGeometry?.focusFallback == true &&
                !dsFocusFallbackToasted
            ) {
                dsFocusFallbackToasted = true
                Toast.makeText(
                    context,
                    "Screens too small for stacked layout - " +
                        "using focus mode instead. You can " +
                        "force stacked layout in the pause " +
                        "menu's Screens section.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            if (!created) {
                                created = true
                                // One hash per launch: the status
                                // check is the only SHA-256 pass.
                                // A corrupt download is refused,
                                // not loaded, and removed without
                                // a second hash; a verified one
                                // resolves straight to its file.
                                val coreStatus = CoreDownload.status(
                                    ctx, consoleKey
                                )
                                if (coreStatus ==
                                    CoreDownload.Status.CORRUPT
                                ) {
                                    Toast.makeText(
                                        ctx,
                                        "Downloaded core failed " +
                                            "verification and was " +
                                            "removed - download the " +
                                            "core again from the " +
                                            "console page.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    CoreDownload.removeDownloaded(
                                        ctx, consoleKey
                                    )
                                }
                                val corePath = if (coreStatus ==
                                    CoreDownload.Status.DOWNLOADED
                                ) {
                                    CoreDownload
                                        .downloadedFileIfPresent(
                                            ctx, consoleKey
                                        )?.absolutePath
                                } else {
                                    null
                                }
                                if (corePath == null) {
                                    error = "No core downloaded for " +
                                        "this console yet - download " +
                                        "it from the console page."
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
                                // Controller device
                                // (beta.17, PS1): the
                                // core defaults to a
                                // digital pad; an
                                // analogue variant
                                // needs DualShock, so
                                // the sticks read.
                                if (consoleKey == "ps1") {
                                    val variant =
                                        GameLayout.controllerVariant(
                                            store, "ps1"
                                        )
                                    RetroNative
                                        .nativeSetControllerDevice(
                                            if (variant == "Digital") {
                                                AnalogInput.DEVICE_JOYPAD
                                            } else {
                                                AnalogInput
                                                    .DEVICE_DUALSHOCK
                                            }
                                        )
                                }
                            }
                            RetroNative.nativeSetSurface(holder.surface)
                            RetroNative.nativeStart()
                            if (!stateLoaded) {
                                stateLoaded = true
                                // A path wins over a slot
                                // (beta.7: archived auto-saves
                                // are files, not slots).
                                val loadFile = loadPath
                                    ?.let { File(it) }
                                    ?.takeIf { it.exists() }
                                    ?: if (loadSlot !=
                                        SaveStates.NO_SLOT &&
                                        gameId != null
                                    ) {
                                        SaveStates.stateFile(
                                            ctx, gameId, loadSlot
                                        ).takeIf { it.exists() }
                                    } else {
                                        null
                                    }
                                if (loadFile != null) {
                                    RetroNative.nativeLoadState(
                                        loadFile.absolutePath
                                    )
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

        // DS overlays, in order: hinge band, PiP tap,
        // upper-screen tap, touch surface. They sit
        // above the centre-tap layer and below the
        // controls, so buttons always win their own
        // touches. The touch surface never hides:
        // it stays live even with a gamepad, since
        // a pad cannot touch the screen.
        if (consoleKey == "ds" && error == null) {
            val g = dsGeometry
            if (g != null) {
                g.hinge?.let { hinge ->
                    DsHingeBand(hinge, density)
                }
                if (g.pipRect != null) {
                    val pip = g.pipRect
                    Box(
                        modifier = Modifier.offset(
                            x = with(density) {
                                pip.left.toDp()
                            },
                            y = with(density) {
                                pip.top.toDp()
                            }
                        )
                            .size(
                                with(density) {
                                    pip.width.toDp()
                                },
                                with(density) {
                                    pip.height.toDp()
                                }
                            )
                            .pointerInput(pip) {
                                detectTapGestures {
                                    swapDsScreens()
                                }
                            }
                    )
                }
                if (dsUpperTap &&
                    dsArrangement != DsArrangement.FOCUS_BOTTOM
                ) {
                    val topQuad = g.quads.firstOrNull {
                        it.content == DsContent.TOP
                    }
                    if (topQuad != null) {
                        val r = topQuad.rect
                        Box(
                            modifier = Modifier.offset(
                                x = with(density) {
                                    r.left.toDp()
                                },
                                y = with(density) {
                                    r.top.toDp()
                                }
                            )
                                .size(
                                    with(density) {
                                        r.width.toDp()
                                    },
                                    with(density) {
                                        r.height.toDp()
                                    }
                                )
                                .pointerInput(r) {
                                    detectTapGestures {
                                        swapDsScreens()
                                    }
                                }
                        )
                    }
                }
                g.touchRect?.let { touch ->
                    DsTouchSurface(touch, density) { poke() }
                }
            }
        }
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
                topInsetDp =
                    if (landscape) 0.dp else portraitTopInsetDp,
                romName = romName,
                layout = layout,
                controlsAlpha = controlsAlpha,
                padConnected = padConnected,
                inputMask = inputMask,
                onPoke = { poke() },
                onTick = { tick() },
                onAnalog = { stick, x, y ->
                    reportAnalog(stick, x, y)
                },
                onStates = {
                    showStatesFromMenu = false
                    showStates = true
                },
                onMenu = { showPauseMenu = true },
                chromeExpanded = chromeExpanded,
                onToggleChrome = { chromeExpanded = !chromeExpanded },
                ffEnabled = ffEnabled,
                ffOn = ffOn,
                onToggleFf = { ffOn = !ffOn },
                dsSwap = if (consoleKey == "ds") {
                    { swapDsScreens() }
                } else null,
                dsLayoutLabel = if (consoleKey == "ds") {
                    DsLayout.arrangementLabel(dsArrangement)
                } else null,
                onDsLayout = if (consoleKey == "ds") {
                    { cycleDsLayout() }
                } else null,
                dsLidPass = consoleKey == "ds" && dsLid == "Pass",
                onDsLidDown = if (consoleKey == "ds") {
                    { setDsBit(DsLayout.LID_BIT, true) }
                } else null,
                onDsLidUp = if (consoleKey == "ds") {
                    { setDsBit(DsLayout.LID_BIT, false) }
                } else null
            )
        }
        // FPS + mic status overlay (beta.31): when
        // the per-console FPS setting is on, the
        // number sits top-right, always, never
        // auto-hiding with the controls; the red
        // dot shows while the DS mic is live. Both
        // are display-only, never tappable.
        if ((fpsOn || (consoleKey == "ds" && dsMic)) &&
            !paused && error == null
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    modifier = Modifier.padding(
                        top = 14.dp + portraitTopInsetDp,
                        end = 14.dp
                    ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    if (fpsOn) {
                        Text(
                            fpsText,
                            style = MaterialTheme.typography
                                .titleSmall.copy(
                                    color = Color.White,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(0f, 1f),
                                        blurRadius = 4f
                                    )
                                )
                        )
                    }
                    if (consoleKey == "ds" && dsMic) {
                        if (fpsOn) Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(12.dp)
                                .background(
                                    Color(0xFFC03030), CircleShape
                                )
                        )
                    }
                }
            }
        }
        if (showStates && gameId != null) {
            SaveStateDialog(
                gameId = gameId,
                showSave = true,
                batteryExists = consoleKey == "ps1" &&
                    File(
                        CoreStore.saveDir(context),
                        File(romPath).nameWithoutExtension + ".srm"
                    ).exists(),
                onDeleteBattery = null,
                // Safe in-game form (C10): the card row
                // shows status only. Import/export live
                // on the console page - importing into a
                // running core would be overwritten by
                // the exit autosave.
                isPs1 = consoleKey == "ps1",
                cardStatusOnly = true,
                onLoad = { slot ->
                    if (SaveStates.load(context, gameId, slot)) {
                        showStates = false
                        if (showStatesFromMenu) {
                            showStatesFromMenu = false
                            showPauseMenu = true
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Couldn't load that save state.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onLoadPath = { path ->
                    if (RetroNative.nativeLoadState(path)) {
                        showStates = false
                        if (showStatesFromMenu) {
                            showStatesFromMenu = false
                            showPauseMenu = true
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Couldn't load that save state.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
                onEditLayout = {
                    showPauseMenu = false
                    showEditor = true
                },
                onReset = {
                    RetroNative.nativeReset()
                    showPauseMenu = false
                },
                onSwapDisc = if (
                    isDiscConsole && discCount > 1
                ) ({
                    val count = RetroNative.nativeDiskGetCount()
                    discLabels = (0 until count).map { i ->
                        DiscSwap.displayLabel(
                            i, RetroNative.nativeDiskGetLabel(i)
                        )
                    }
                    discIndex = RetroNative.nativeDiskGetIndex()
                    discCount = count
                    showDiscPicker = true
                    showPauseMenu = false
                }) else null,
                onExit = onExit,
                onDismiss = { showPauseMenu = false },
                romName = romName,
                dsScreens = if (consoleKey == "ds") ({
                    var arrangementExpanded by remember { mutableStateOf(false) }
                    var lidExpanded by remember { mutableStateOf(false) }
                    var pipExpanded by remember { mutableStateOf(false) }
                    PauseRow(
                        Icons.Filled.Tune, "Screen arrangement",
                        onClick = { arrangementExpanded = !arrangementExpanded },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    DsLayout.arrangementLabel(dsArrangement),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    if (arrangementExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                    if (arrangementExpanded) {
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                    for (a in DsLayout.arrangementsFor(landscape)) {
                        PauseRow(
                            Icons.Filled.Tune,
                            DsLayout.arrangementLabel(a),
                            onClick = {
                                dsArrangement = a
                                store.putSetting(
                                    DsLayout.arrangementKey(
                                        landscape
                                    ),
                                    a.name
                                )
                            },
                            trailing = if (dsArrangement == a) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme
                                            .primary
                                    )
                                }
                            } else null
                        )
                    }
                        }
                    }
                    PauseRow(
                        Icons.Filled.SwapHoriz, "Swap screens",
                        onClick = { swapDsScreens() }
                    )
                    PauseRow(
                        Icons.Filled.Mic, "Microphone",
                        onClick = {
                            val next = !dsMic
                            dsMic = next
                            store.putSetting(
                                DsLayout.MIC_KEY, next.toString()
                            )
                        },
                        trailing = {
                            Switch(
                                checked = dsMic,
                                onCheckedChange = { next ->
                                    dsMic = next
                                    store.putSetting(
                                        DsLayout.MIC_KEY,
                                        next.toString()
                                    )
                                }
                            )
                        }
                    )
                    PauseRow(
                        Icons.Filled.Flip, "Lid",
                        onClick = { lidExpanded = !lidExpanded },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (dsLid == "Pass") "Passes to core" else "Pauses game",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    if (lidExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                    if (lidExpanded) {
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                    PauseRow(
                        Icons.Filled.Flip, "Lid pauses game",
                        onClick = {
                            dsLid = "Pause"
                            store.putSetting(
                                DsLayout.LID_KEY, "Pause"
                            )
                        },
                        trailing = if (dsLid == "Pause") {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme
                                        .primary
                                )
                            }
                        } else null
                    )
                    PauseRow(
                        Icons.Filled.Flip, "Lid passes to core",
                        onClick = {
                            dsLid = "Pass"
                            store.putSetting(
                                DsLayout.LID_KEY, "Pass"
                            )
                        },
                        trailing = if (dsLid == "Pass") {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme
                                        .primary
                                )
                            }
                        } else null
                    )
                        }
                    }
                    PauseRow(
                        Icons.Filled.TouchApp,
                        "Tap top screen swaps",
                        onClick = {
                            val next = !dsUpperTap
                            dsUpperTap = next
                            store.putSetting(
                                DsLayout.UPPER_TAP_KEY,
                                next.toString()
                            )
                        },
                        trailing = {
                            Switch(
                                checked = dsUpperTap,
                                onCheckedChange = { next ->
                                    dsUpperTap = next
                                    store.putSetting(
                                        DsLayout.UPPER_TAP_KEY,
                                        next.toString()
                                    )
                                }
                            )
                        }
                    )
                    PauseRow(
                        Icons.Filled.Crop, "PiP corner",
                        onClick = { pipExpanded = !pipExpanded },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    DsLayout.pipCornerLabel(dsPipCorner),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    if (pipExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                    if (pipExpanded) {
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                    for (corner in DsPipCorner.entries) {
                        PauseRow(
                            Icons.Filled.Crop,
                            "PiP corner: " +
                                DsLayout.pipCornerLabel(corner),
                            onClick = {
                                dsPipCorner = corner
                                store.putSetting(
                                    DsLayout.PIP_CORNER_KEY,
                                    corner.name
                                )
                            },
                            trailing = if (dsPipCorner == corner) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme
                                            .primary
                                    )
                                }
                            } else null
                        )
                    }
                        }
                    }
                    PauseRow(
                        Icons.Filled.Tune, "Force stacked layout",
                        onClick = {
                            val next = !dsForceStacked
                            dsForceStacked = next
                            store.putSetting(
                                DsLayout.FORCE_STACKED_KEY,
                                next.toString()
                            )
                        },
                        trailing = {
                            Switch(
                                checked = dsForceStacked,
                                onCheckedChange = { next ->
                                    dsForceStacked = next
                                    store.putSetting(
                                        DsLayout.FORCE_STACKED_KEY,
                                        next.toString()
                                    )
                                }
                            )
                        }
                    )
                }) else null
            )
        }
        if (showDiscPicker) {
            // N3: disc picker, kit dialog material like
            // the pause menu. The game stays paused
            // while this is open (showDiscPicker is
            // part of `paused`); a failed swap leaves
            // the picker open so the user can retry,
            // and dismissing returns to the pause menu.
            Dialog(
                onDismissRequest = {
                    showDiscPicker = false
                    showPauseMenu = true
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
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
                            "Swap disc",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(
                                horizontal = 20.dp, vertical = 8.dp
                            )
                        )
                        discLabels.forEachIndexed { i, label ->
                            PauseRow(
                                Icons.Filled.Album, label,
                                onClick = {
                                    if (RetroNative.nativeDiskSwap(i)) {
                                        Toast.makeText(
                                            context,
                                            "Switched to $label",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showDiscPicker = false
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Couldn't swap discs",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                trailing = if (i == discIndex) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                        PauseRow(
                            Icons.Filled.Close, "Cancel",
                            onClick = {
                                showDiscPicker = false
                                showPauseMenu = true
                            }
                        )
                    }
                }
            }
        }
        if (showEditor) {
            // The editor sits over the paused game;
            // Done (or back) returns to the pause
            // menu, and the game stays paused throughout.
            GameLayoutEditor(
                store = store,
                console = consoleKey,
                consoleLabel = systemLabel(consoleKey),
                onDone = {
                    showEditor = false
                    showPauseMenu = true
                },
                restoreStatusBar = false
            )
        }
    }
}

@Composable
private fun GameControls(
    titleVisible: Boolean,
    topInsetDp: Dp,
    romName: String,
    layout: List<ControlSpec>,
    controlsAlpha: Float,
    padConnected: Boolean,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    onAnalog: (Int, Float, Float) -> Unit,
    onStates: () -> Unit,
    onMenu: () -> Unit,
    chromeExpanded: Boolean,
    onToggleChrome: () -> Unit,
    ffEnabled: Boolean,
    ffOn: Boolean,
    onToggleFf: () -> Unit,
    dsSwap: (() -> Unit)? = null,
    dsLayoutLabel: String? = null,
    onDsLayout: (() -> Unit)? = null,
    dsLidPass: Boolean = false,
    onDsLidDown: (() -> Unit)? = null,
    onDsLidUp: (() -> Unit)? = null
) {
    // Collapsed chrome (restored, beta.32; Robert,
    // beta.16): while playing, only the expand pill
    // shows; it opens the save / fast-forward /
    // menu pills, and the same pill closes them
    // again. Exit lives in the pause menu, not the
    // top row. The row renders before the
    // padConnected early-return below, so it stays
    // reachable with a gamepad connected. The FPS
    // number and mic dot are NOT pills here: they
    // live in beta.31's always-on status overlay.
    Box(modifier = Modifier.fillMaxSize()) {
        // Title + expand, top edge, player-capsule style.
        Row(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(
                    start = 14.dp, end = 14.dp,
                    top = 14.dp + topInsetDp,
                    bottom = 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp)
                    .graphicsLayer { this.alpha = controlsAlpha }
                    .frostBar(CircleShape)
                    .clickable { onToggleChrome() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (chromeExpanded) Icons.Filled.ChevronLeft
                    else Icons.Filled.ChevronRight,
                    contentDescription = if (chromeExpanded) {
                        "Hide game buttons"
                    } else {
                        "Show game buttons"
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (titleVisible) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .frostBar(RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        romName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (chromeExpanded) {
                Spacer(Modifier.width(10.dp))
                // States, first in the expanded row,
                // with a floppy-disk icon.
                Box(
                    modifier = Modifier.size(48.dp)
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
                // Fast-forward: only when enabled for
                // this console; lit in the accent colour
                // while it runs.
                if (ffEnabled) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .frostBar(RoundedCornerShape(50))
                            .clickable { onToggleFf() }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
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
                    modifier = Modifier.size(48.dp)
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
                if (dsSwap != null) {
                    Spacer(Modifier.width(10.dp))
                    // Power dot: green while the game runs.
                    // The chrome only shows while playing,
                    // so the amber paused state never needs
                    // to render here.
                    Box(
                        modifier = Modifier.size(12.dp)
                            .align(Alignment.CenterVertically)
                            .background(
                                Color(0xFF2B8A3E), CircleShape
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .frostBar(RoundedCornerShape(50))
                            .clickable { dsSwap() }
                            .padding(
                                horizontal = 14.dp, vertical = 14.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Swap",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (dsLayoutLabel != null) {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .frostBar(RoundedCornerShape(50))
                                .clickable { onDsLayout?.invoke() }
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 14.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dsLayoutLabel,
                                style = MaterialTheme.typography
                                    .titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (dsLidPass) {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .frostBar(RoundedCornerShape(50))
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(
                                            requireUnconsumed = false
                                        )
                                        onPoke()
                                        onDsLidDown?.invoke()
                                        try {
                                            while (true) {
                                                val event =
                                                    awaitPointerEvent()
                                                if (!event.changes.any {
                                                    it.pressed
                                                }) break
                                            }
                                        } finally {
                                            onDsLidUp?.invoke()
                                        }
                                    }
                                }
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 14.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Lid",
                                style = MaterialTheme.typography
                                    .titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
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
                // Axis pivot (dpad fix): the pad's
                // vertical axis is the up/down centre
                // line and its horizontal axis is the
                // left/right row - never the mean of
                // all four centres, which skewed the
                // pad whenever a layout was asymmetric.
                val upC = centres.firstOrNull {
                    it.first.id == "up"
                }?.second
                val downC = centres.firstOrNull {
                    it.first.id == "down"
                }?.second
                val leftC = centres.firstOrNull {
                    it.first.id == "left"
                }?.second
                val rightC = centres.firstOrNull {
                    it.first.id == "right"
                }?.second
                val pivot = (
                    if (upC != null && downC != null) {
                        (upC.first + downC.first) / 2
                    } else {
                        centres.fold(0.dp) {
                            a, p -> a + p.second.first
                        } / 4
                    }
                    ) to (
                    if (leftC != null && rightC != null) {
                        (leftC.second + rightC.second) / 2
                    } else {
                        centres.fold(0.dp) {
                            a, p -> a + p.second.second
                        } / 4
                    }
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
                if (spec.analog >= 0) {
                    StickSurface(
                        spec = spec,
                        onAnalog = onAnalog,
                        onPoke = onPoke,
                        onTick = onTick,
                        modifier = controlModifier
                    )
                    continue
                }
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

/** An analogue stick (beta.17, PS1): a frost base
 * with a nub that follows the finger, clamped to
 * the base radius. The reported vector is the nub
 * offset over the radius (-1..1 per axis, up is
 * negative Y); releasing the stick recentres it
 * and reports zero. One pointer per stick, so both
 * sticks and the buttons work at once. */
@Composable
private fun StickSurface(
    spec: ControlSpec,
    onAnalog: (Int, Float, Float) -> Unit,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nub by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(spec.sizeDp.dp)
            .pointerInput(spec) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPoke()
                    onTick()
                    val radius = size.width / 2f
                    val centre = Offset(radius, radius)
                    fun apply(pos: Offset) {
                        val d = pos - centre
                        val dist = sqrt(d.x * d.x + d.y * d.y)
                        val cl = if (dist > radius) {
                            d * (radius / dist)
                        } else {
                            d
                        }
                        nub = cl
                        onAnalog(
                            spec.analog,
                            cl.x / radius,
                            cl.y / radius
                        )
                    }
                    apply(down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break
                            if (!change.pressed) break
                            apply(change.position)
                        }
                    } finally {
                        nub = Offset.Zero
                        onAnalog(spec.analog, 0f, 0f)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .frostBar(CircleShape)
        )
        // The nub travels less than the finger so
        // it stays inside the base at full deflection.
        val nubTravel = nub * 0.45f
        Box(
            modifier = Modifier.align(Alignment.Center)
                .offset {
                    IntOffset(
                        nubTravel.x.toInt(),
                        nubTravel.y.toInt()
                    )
                }
                .size(spec.sizeDp.dp * 0.55f)
                .frostBar(CircleShape)
        )
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
    onEditLayout: () -> Unit,
    onReset: () -> Unit,
    onSwapDisc: (() -> Unit)? = null,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
    romName: String = "",
    dsScreens: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        horizontal = 20.dp, vertical = 8.dp
                    )
                )
                if (romName.isNotBlank()) {
                    Text(
                        romName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = 20.dp
                        )
                    )
                }
                PauseRow(
                    Icons.Filled.PlayArrow, "Resume", onResume
                )
                PauseRow(
                    Icons.Filled.Save, "Save states", onSaveStates
                )
                PauseRow(
                    Icons.Filled.Tune, "Edit touch layout", onEditLayout
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
                if (onSwapDisc != null) {
                    PauseRow(
                        Icons.Filled.Album, "Swap disc", onSwapDisc
                    )
                }
                if (dsScreens != null) {
                    Text(
                        "Screens",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(
                            horizontal = 20.dp
                        ).padding(top = 12.dp, bottom = 4.dp)
                    )
                    dsScreens()
                }
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
 * directions are moved apart in the editor. Dpad fix:
 * the pivot is the axes' intersection, not the mean of
 * the centres; sector angles are measured on axes
 * normalised by the pad's own half-spans, so the sector
 * boundaries fall in the visual gaps between buttons
 * even when the pad is a wide rectangle; and the
 * gesture keys on a stable identity, reading live
 * centres, so a recomposition mid-slide cannot drop
 * it. The connecting cross bars do NOT draw here any
 * more - play shows only the four circles, and the
 * cross lives in the layout editor as a guide.
 */
// DS hinge band: frost material at low alpha with a
// centre seam and two small barrel dots at the ends.
// Non-interactive: touches pass straight through.
@Composable
private fun DsHingeBand(rectPx: DsRect, density: Density) {
    val horizontal = rectPx.width >= rectPx.height
    val w = with(density) { rectPx.width.toDp() }
    val h = with(density) { rectPx.height.toDp() }
    val seamColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val dotColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Box(
        modifier = Modifier.offset(
            x = with(density) { rectPx.left.toDp() },
            y = with(density) { rectPx.top.toDp() }
        )
            .size(w, h)
            .background(Frost.sheetColor().copy(alpha = 0.5f))
    ) {
        if (horizontal) {
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .size(w, 1.dp)
                    .background(seamColor)
            )
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .size(1.dp, h)
                    .background(seamColor)
            )
            Box(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
        }
    }
}

// DS touch surface: exactly one pointer id, so holding
// the D-pad and tapping the screen works, and a second
// finger on the screen is ignored (a real DS has one
// touch point). Reports the pointer to the core via
// the full-buffer encoding, clamped to the quad. A
// small frost dot marks the touch point while it lasts.
@Composable
private fun DsTouchSurface(
    rectPx: DsRect,
    density: Density,
    onPoke: () -> Unit
) {
    var dot by remember { mutableStateOf<Offset?>(null) }
    Box(
        modifier = Modifier.offset(
            x = with(density) { rectPx.left.toDp() },
            y = with(density) { rectPx.top.toDp() }
        )
            .size(
                with(density) { rectPx.width.toDp() },
                with(density) { rectPx.height.toDp() }
            )
            .pointerInput(rectPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false
                    )
                    onPoke()
                    val u0 =
                        (down.position.x / size.width).coerceIn(0f, 1f)
                    val v0 =
                        (down.position.y / size.height).coerceIn(0f, 1f)
                    val (px0, py0) = DsLayout.pointerFor(u0, v0)
                    RetroNative.nativeSetPointer(true, px0, py0)
                    dot = down.position
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes
                                .firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            val u = (change.position.x / size.width)
                                .coerceIn(0f, 1f)
                            val v = (change.position.y / size.height)
                                .coerceIn(0f, 1f)
                            val (px, py) = DsLayout.pointerFor(u, v)
                            RetroNative.nativeSetPointer(true, px, py)
                            dot = change.position
                        }
                    } finally {
                        RetroNative.nativeSetPointer(false, 0, 0)
                        dot = null
                    }
                }
            }
    ) {
        dot?.let { d ->
            Box(
                modifier = Modifier.offset(
                    x = with(density) { d.x.toDp() } - 12.dp,
                    y = with(density) { d.y.toDp() } - 12.dp
                )
                    .size(24.dp)
                    .background(
                        Color.White.copy(alpha = 0.6f), CircleShape
                    )
            )
        }
    }
}

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
    // Stable gesture identity (dpad fix): the block
    // never restarts on a recomposition, and reads
    // the live pivot and centres, so a slide cannot
    // be dropped by a layout recomposition mid-drag.
    val currentPivotX by rememberUpdatedState(pivotX)
    val currentPivotY by rememberUpdatedState(pivotY)
    val currentVisuals by rememberUpdatedState(visuals)
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .pointerInput(Unit) {
                val pivot =
                    Offset(currentPivotX.toPx(), currentPivotY.toPx())
                fun bitsFor(pos: Offset): Int {
                    val dx = pos.x - pivot.x
                    val dy = pos.y - pivot.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < 14.dp.toPx()) return 0
                    // Elliptical correction (dpad
                    // fix): normalise by the pad's own
                    // half-spans, so a wide rectangular
                    // pad's sectors match its buttons.
                    val upV = currentVisuals.firstOrNull {
                        it.spec.id == "up"
                    }
                    val downV = currentVisuals.firstOrNull {
                        it.spec.id == "down"
                    }
                    val leftV = currentVisuals.firstOrNull {
                        it.spec.id == "left"
                    }
                    val rightV = currentVisuals.firstOrNull {
                        it.spec.id == "right"
                    }
                    val spanX = if (leftV != null &&
                        rightV != null
                    ) {
                        (rightV.centreX - leftV.centreX)
                            .toPx() / 2f
                    } else {
                        0f
                    }
                    val spanY = if (upV != null &&
                        downV != null
                    ) {
                        (downV.centreY - upV.centreY)
                            .toPx() / 2f
                    } else {
                        0f
                    }
                    val ndx = if (spanX > 0f) dx / spanX else dx
                    val ndy = if (spanY > 0f) dy / spanY else dy
                    val ang = Math.toDegrees(
                        atan2(ndy.toDouble(), ndx.toDouble())
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
        // Dpad fix: no cross bars in play - only the
        // four direction circles draw here. The cross
        // lives in the layout editor as a guide, and
        // the diagonal function is untouched: it is
        // the angle-sector code above, not the bars.
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
