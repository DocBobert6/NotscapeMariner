package net.notscape.browser.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.notscape.browser.data.AppStore
import net.notscape.browser.emu.ControlSpec
import net.notscape.browser.emu.GameLayout

/**
 * The touch-layout editor (Phase J; J7 pill chrome): the game's
 * controls on a dark canvas, in the player's frost
 * material. Portrait and landscape are SEPARATE layouts
 * (Robert): the editor works on the side the phone is
 * currently held in, says which at the top, and rotating
 * mid-edit saves that side and loads the other. Clusters
 * move together by default - the D-pad as one, the face
 * buttons as one - with a switch to move buttons
 * separately. A selected control shows its exact size in
 * dp inside itself, and the stepper row changes it 1dp
 * at a time, so sizes can be matched precisely.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameLayoutEditor(
    store: AppStore,
    console: String,
    consoleLabel: String,
    onDone: () -> Unit,
    // In-game the bars are already hidden by the
    // game activity, so there is nothing to
    // restore there; on a normal screen the status
    // bar comes back when the editor closes.
    restoreStatusBar: Boolean = true
) {
    // A full-screen editor: the device status bar
    // is in the way of the top pill row, so it
    // hides while the editor is open (a swipe
    // still reveals it briefly) and is restored on
    // exit where the host screen wants it back.
    val editorContext = LocalContext.current
    BackHandler { onDone() }
    DisposableEffect(Unit) {
        val activity = editorContext as? Activity
        val window = activity?.window
        val controller = window?.let {
            WindowInsetsControllerCompat(it, it.decorView)
        }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            if (restoreStatusBar) {
                controller?.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    val configuration = LocalConfiguration.current
    val landscape =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var editedLandscape by remember { mutableStateOf(landscape) }
    var specs by remember {
        mutableStateOf(GameLayout.load(store, console, landscape))
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var snapOn by remember { mutableStateOf(true) }
    var grouped by remember { mutableStateOf(true) }
    val density = LocalDensity.current.density
    val controlsAlpha = (
        store.setting(
            "gameControlsAlpha:$console",
            store.setting("gameControlsAlpha", "100")
        ).toIntOrNull() ?: 100
        ) / 100f

    LaunchedEffect(landscape) {
        if (landscape != editedLandscape) {
            GameLayout.save(store, console, editedLandscape, specs)
            specs = GameLayout.load(store, console, landscape)
            editedLandscape = landscape
            selectedId = null
        }
    }

    fun handleMove(newSpec: ControlSpec) {
        val old = specs.firstOrNull { it.id == newSpec.id } ?: return
        val dx = newSpec.cx - old.cx
        val dy = newSpec.cy - old.cy
        val grp = if (grouped) GameLayout.groupOf(newSpec.id) else null
        specs = specs.map { s ->
            when {
                s.id == newSpec.id -> newSpec
                grp != null && GameLayout.groupOf(s.id) == grp ->
                    s.copy(
                        cx = (s.cx + dx).coerceIn(0.03f, 0.97f),
                        cy = (s.cy + dy).coerceIn(0.03f, 0.97f)
                    )
                else -> s
            }
        }
    }
    fun handleResize(newSpec: ControlSpec) {
        val grp = if (grouped) GameLayout.groupOf(newSpec.id) else null
        specs = specs.map { s ->
            when {
                s.id == newSpec.id -> newSpec
                grp != null && GameLayout.groupOf(s.id) == grp ->
                    s.copy(sizeDp = newSpec.sizeDp)
                else -> s
            }
        }
    }
    val selected = specs.firstOrNull { it.id == selectedId }

    // J7: floating frost pills over a truly full-screen
    // canvas. The old reserved rows took half the screen
    // in landscape and controls could not be dragged
    // under them; now only the small pills at the very
    // top block touches, and the stepper pill appears
    // only while a control is selected.
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black)
        ) {
            val canvasW = maxWidth.value
            val canvasH = maxHeight.value
            for (spec in specs) {
                EditorControl(
                    spec = spec,
                    selected = spec.id == selectedId,
                    canvasWDp = canvasW,
                    canvasHDp = canvasH,
                    density = density,
                    snapOn = snapOn,
                    alpha = controlsAlpha,
                    onSelect = { selectedId = spec.id },
                    onMove = { handleMove(it) },
                    onResize = { handleResize(it) }
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PillButton("Back") { onDone() }
                PillLabel(
                    consoleLabel + " - " +
                        if (editedLandscape) {
                            "Landscape layout"
                        } else {
                            "Portrait layout"
                        }
                )
                PillToggle("Snap", snapOn) { snapOn = it }
                PillToggle("Together", grouped) { grouped = it }
                PillButton("Reset") {
                    GameLayout.reset(store, console, editedLandscape)
                    specs = GameLayout.defaults(
                        console, !editedLandscape
                    )
                    selectedId = null
                }
                PillButton("Done") {
                    GameLayout.save(
                        store, console, editedLandscape, specs
                    )
                    onDone()
                }
            }
            if (selected != null) {
                Spacer(Modifier.height(6.dp))
                // Shorter and wider than a stock button row
                // (Robert, 2026-10-24): a fixed 32dp height
                // with wide tap targets, so the pill reads
                // as a slim strip, not a blob.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.frostBar(
                        RoundedCornerShape(50)
                    ).height(32.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight()
                            .clickable {
                                handleResize(
                                    selected.copy(
                                        sizeDp = (selected.sizeDp - 1)
                                            .coerceIn(
                                                GameLayout.MIN_SIZE_DP,
                                                GameLayout.MAX_SIZE_DP
                                            )
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "-",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        "${selected.sizeDp}dp",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(
                        modifier = Modifier.fillMaxHeight()
                            .clickable {
                                handleResize(
                                    selected.copy(
                                        sizeDp = (selected.sizeDp + 1)
                                            .coerceIn(
                                                GameLayout.MIN_SIZE_DP,
                                                GameLayout.MAX_SIZE_DP
                                            )
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.frostBar(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PillLabel(label: String) {
    Box(
        modifier = Modifier.frostBar(RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PillToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier.frostBar(RoundedCornerShape(50))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun EditorControl(
    spec: ControlSpec,
    selected: Boolean,
    canvasWDp: Float,
    canvasHDp: Float,
    density: Float,
    snapOn: Boolean,
    alpha: Float,
    onSelect: () -> Unit,
    onMove: (ControlSpec) -> Unit,
    onResize: (ControlSpec) -> Unit
) {
    val currentSpec by rememberUpdatedState(spec)
    val wDp = spec.sizeDp
    val hDp = if (spec.pill) (spec.sizeDp * 0.46f).toInt() else spec.sizeDp
    val x = (spec.cx * canvasWDp - wDp / 2f).dp
    val y = (spec.cy * canvasHDp - hDp / 2f).dp
    Box(
        modifier = Modifier.offset(x = x, y = y)
            .size(wDp.dp, hDp.dp)
            .then(
                if (spec.pill) {
                    Modifier.frostBar(RoundedCornerShape(50))
                } else {
                    Modifier.frostBar(CircleShape)
                }
            )
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        if (spec.pill) {
                            RoundedCornerShape(50)
                        } else {
                            CircleShape
                        }
                    )
                } else {
                    Modifier
                }
            )
            .graphicsLayer { this.alpha = alpha }
            .pointerInput(spec.id, snapOn, canvasWDp, canvasHDp) {
                var baseCxDp = 0f
                var baseCyDp = 0f
                var accX = 0f
                var accY = 0f
                detectDragGestures(
                    onDragStart = {
                        onSelect()
                        baseCxDp = currentSpec.cx * canvasWDp
                        baseCyDp = currentSpec.cy * canvasHDp
                        accX = 0f
                        accY = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        accX += drag.x / density
                        accY += drag.y / density
                        val cxDp = GameLayout.snap(
                            baseCxDp + accX, 8, snapOn
                        )
                        val cyDp = GameLayout.snap(
                            baseCyDp + accY, 8, snapOn
                        )
                        onMove(
                            currentSpec.copy(
                                cx = (cxDp / canvasWDp)
                                    .coerceIn(0.03f, 0.97f),
                                cy = (cyDp / canvasHDp)
                                    .coerceIn(0.03f, 0.97f)
                            )
                        )
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (spec.pill && selected) {
            Text(
                "${spec.sizeDp}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    spec.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = if (spec.pill) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    }
                )
                if (selected) {
                    Text(
                        "${spec.sizeDp}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (selected) {
            // Corner resize handle.
            Box(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(4.dp)
                    )
                    .pointerInput(spec.id) {
                        var baseSize = 0
                        var accX = 0f
                        detectDragGestures(
                            onDragStart = {
                                baseSize = currentSpec.sizeDp
                                accX = 0f
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                accX += drag.x / density
                                onResize(
                                    currentSpec.copy(
                                        sizeDp =
                                            (baseSize + accX.toInt())
                                                .coerceIn(
                                                    GameLayout.MIN_SIZE_DP,
                                                    GameLayout.MAX_SIZE_DP
                                                )
                                    )
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↘",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
