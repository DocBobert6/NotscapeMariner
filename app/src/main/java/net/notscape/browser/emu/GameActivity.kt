package net.notscape.browser.emu

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlinx.coroutines.delay
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.NotscapeTheme
import net.notscape.browser.ui.frostBar

// libretro joypad ids (the shim's input bitmask).
private const val BTN_B = 0
private const val BTN_SELECT = 2
private const val BTN_START = 3
private const val BTN_UP = 4
private const val BTN_DOWN = 5
private const val BTN_LEFT = 6
private const val BTN_RIGHT = 7
private const val BTN_A = 8

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

    companion object {
        const val EXTRA_ROM_PATH = "notscape.emu.ROM_PATH"
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
        // Themed exactly like the player (Robert's rule:
        // the controls match the player's UI, theme
        // included) - NotscapeTheme over the shared store.
        val store = AppStore.get(this)
        setContent {
            NotscapeTheme(store) {
                GameScreen(romPath = romPath, onExit = { finish() })
            }
        }
    }
}

@Composable
private fun GameScreen(romPath: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val romName = remember(romPath) {
        File(romPath).name.substringBeforeLast('.')
            .ifBlank { "Game" }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
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
                                val corePath = CoreStore.mgbaCorePath(ctx)
                                if (corePath == null) {
                                    error = "The emulator core is " +
                                        "missing from this build."
                                    return
                                }
                                val err = RetroNative.nativeCreate(
                                    corePath, romPath,
                                    CoreStore.systemDir(ctx).absolutePath,
                                    CoreStore.saveDir(ctx).absolutePath
                                )
                                if (err != null) {
                                    error = err
                                    return
                                }
                            }
                            RetroNative.nativeSetSurface(holder.surface)
                            RetroNative.nativeStart()
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
                romName = romName,
                inputMask = inputMask,
                onPoke = { poke() },
                onTick = { tick() },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun GameControls(
    romName: String,
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit,
    onExit: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Title + exit, top edge, player-capsule style.
        Row(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(14.dp),
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

        // D-pad, bottom-left.
        Box(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(18.dp)
                .size(178.dp)
        ) {
            PadButton(
                "▲", BTN_UP, 58.dp, inputMask, onPoke, onTick,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            PadButton(
                "▼", BTN_DOWN, 58.dp, inputMask, onPoke, onTick,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            PadButton(
                "◀", BTN_LEFT, 58.dp, inputMask, onPoke, onTick,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            PadButton(
                "▶", BTN_RIGHT, 58.dp, inputMask, onPoke, onTick,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // A / B, bottom-right, Game Boy arrangement.
        Box(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(18.dp)
                .size(178.dp)
        ) {
            PadButton(
                "A", BTN_A, 66.dp, inputMask, onPoke, onTick,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            PadButton(
                "B", BTN_B, 66.dp, inputMask, onPoke, onTick,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // Select / Start pills, bottom-centre.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PadPill("SELECT", BTN_SELECT, inputMask, onPoke, onTick)
            Spacer(Modifier.width(12.dp))
            PadPill("START", BTN_START, inputMask, onPoke, onTick)
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
    inputMask: MutableState<Int>,
    onPoke: () -> Unit,
    onTick: () -> Unit
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
        modifier = Modifier.width(74.dp).height(34.dp)
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
