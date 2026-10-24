package net.notscape.browser.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.notscape.browser.data.AppStore
import net.notscape.browser.emu.ControlSpec
import net.notscape.browser.emu.GameLayout

/**
 * The touch-layout editor (Phase J; J4 form): the game's
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
@Composable
fun GameLayoutEditor(
    store: AppStore,
    console: String,
    consoleLabel: String,
    onDone: () -> Unit
) {
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
        store.setting("gameControlsAlpha", "100").toIntOrNull()
            ?: 100
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            TextButton(onClick = onDone) { Text("Back") }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Edit controls",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    consoleLabel + " - " +
                        if (editedLandscape) {
                            "Landscape layout"
                        } else {
                            "Portrait layout"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Snap",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(6.dp))
            Switch(checked = snapOn, onCheckedChange = { snapOn = it })
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                GameLayout.reset(store, console, editedLandscape)
                specs = GameLayout.defaults(console, !editedLandscape)
                selectedId = null
            }) { Text("Reset") }
            Button(onClick = {
                GameLayout.save(store, console, editedLandscape, specs)
                onDone()
            }) { Text("Done") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Switch(
                checked = grouped,
                onCheckedChange = { grouped = it }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Move clusters together (pad as one, " +
                    "face buttons as one)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (selected != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    horizontal = 16.dp, vertical = 4.dp
                )
            ) {
                Text(
                    selected.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    handleResize(
                        selected.copy(
                            sizeDp = (selected.sizeDp - 1)
                                .coerceIn(
                                    GameLayout.MIN_SIZE_DP,
                                    GameLayout.MAX_SIZE_DP
                                )
                        )
                    )
                }) { Text("-") }
                Text(
                    "${selected.sizeDp}dp",
                    style = MaterialTheme.typography.bodyLarge
                )
                TextButton(onClick = {
                    handleResize(
                        selected.copy(
                            sizeDp = (selected.sizeDp + 1)
                                .coerceIn(
                                    GameLayout.MIN_SIZE_DP,
                                    GameLayout.MAX_SIZE_DP
                                )
                        )
                    )
                }) { Text("+") }
            }
        }
        Text(
            "Drag a control to move it; tap one, then drag " +
                "its corner or use the steppers to size it. " +
                "Rotate the phone to edit the other layout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
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
