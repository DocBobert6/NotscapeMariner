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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import net.notscape.browser.emu.DsLayout
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
            // DS: the two screens, dimmed, so a layout
            // is judged against the real screen geometry
            // while it is edited. No pointer input, so
            // editor drags pass straight through.
            if (console == "ds") {
                val dsArrangement = DsLayout.arrangementFor(
                    { key, default -> store.setting(key, default) },
                    landscape
                )
                val dsSwapped = store.setting(
                    DsLayout.swapKey(landscape), "false"
                ) == "true"
                val dsPipCorner = DsLayout.pipCornerFor(
                    { key, default -> store.setting(key, default) }
                )
                val dsInteger = store.setting(
                    "gameIntegerScale:ds", "false"
                ) == "true"
                val dsForceStacked = DsLayout.forceStackedFor(
                    { key, default -> store.setting(key, default) }
                )
                val dsCutoutPx = WindowInsets.displayCutout
                    .getTop(LocalDensity.current) +
                    with(LocalDensity.current) { 8.dp.toPx() }
                val dsGeo = DsLayout.geometry(
                    canvasW * density, canvasH * density, landscape,
                    dsArrangement, dsSwapped, dsPipCorner, dsInteger,
                    if (landscape) 0f else dsCutoutPx,
                    with(LocalDensity.current) { 16.dp.toPx() },
                    with(LocalDensity.current) { 84.dp.toPx() },
                    with(LocalDensity.current) {
                        if (landscape) 183.dp.toPx()
                        else 146.dp.toPx()
                    },
                    with(LocalDensity.current) {
                        if (landscape) 137.dp.toPx()
                        else 110.dp.toPx()
                    },
                    with(LocalDensity.current) { 91.dp.toPx() },
                    with(LocalDensity.current) { 183.dp.toPx() },
                    dsForceStacked
                )
                for (quad in dsGeo.quads) {
                    val r = quad.rect
                    Box(
                        modifier = Modifier.offset(
                            x = (r.left / density).dp,
                            y = (r.top / density).dp
                        )
                            .size(
                                (r.width / density).dp,
                                (r.height / density).dp
                            )
                            .background(
                                Color(0xFF1A1A1A).copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.25f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
                dsGeo.hinge?.let { hinge ->
                    Box(
                        modifier = Modifier.offset(
                            x = (hinge.left / density).dp,
                            y = (hinge.top / density).dp
                        )
                            .size(
                                (hinge.width / density).dp,
                                (hinge.height / density).dp
                            )
                            .background(
                                Color.Gray.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            // Connected-cross guide (dpad fix): the
            // cross that used to draw in play now
            // draws ONLY here, behind the controls,
            // as a non-interactive guide - no pointer
            // input, so editor drags pass straight
            // through it to the controls above. The
            // bars join the direction centres on
            // their true axes, so the guide shows
            // the pad's shape while it is edited.
            val padSpecs = specs.filter {
                GameLayout.groupOf(it.id) == "pad"
            }
            if (padSpecs.size == 4) {
                val up = padSpecs.firstOrNull { it.id == "up" }
                val down = padSpecs.firstOrNull { it.id == "down" }
                val left = padSpecs.firstOrNull { it.id == "left" }
                val right = padSpecs.firstOrNull { it.id == "right" }
                if (up != null && down != null &&
                    left != null && right != null
                ) {
                    val vW = up.sizeDp
                    Box(
                        modifier = Modifier.offset(
                            x = (up.cx * canvasW - vW / 2f).dp,
                            y = (up.cy * canvasH - up.sizeDp / 2f).dp
                        ).size(
                            vW.dp,
                            (down.cy * canvasH - up.cy * canvasH +
                                down.sizeDp).dp
                        ).frostBar(RoundedCornerShape(50))
                            .graphicsLayer { this.alpha = controlsAlpha }
                    )
                    val hH = left.sizeDp
                    Box(
                        modifier = Modifier.offset(
                            x = (left.cx * canvasW - left.sizeDp / 2f).dp,
                            y = (left.cy * canvasH - hH / 2f).dp
                        ).size(
                            (right.cx * canvasW - left.cx * canvasW +
                                right.sizeDp).dp,
                            hH.dp
                        ).frostBar(RoundedCornerShape(50))
                            .graphicsLayer { this.alpha = controlsAlpha }
                    )
                }
            }
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
                        console, !editedLandscape,
                        GameLayout.controllerVariant(store, console)
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
                // A full 48dp row (audit): the old 32dp
                // strip was below the touch-target size,
                // so the +/- targets were too short.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.frostBar(
                        RoundedCornerShape(50)
                    ).height(48.dp)
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
            .padding(horizontal = 12.dp, vertical = 14.dp),
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
            .padding(horizontal = 12.dp, vertical = 14.dp),
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
            // Corner resize handle. The touch area matches
            // the visible grip (28dp, not 48dp): the old
            // 48dp box covered most of a small control's
            // corner, so grabbing the control to drag it
            // resized it instead. The 20dp visual is
            // unchanged; grabs elsewhere move the control.
            Box(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .size(28.dp)
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
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier.size(20.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp)
                        ),
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
}
