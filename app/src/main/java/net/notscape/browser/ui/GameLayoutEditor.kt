package net.notscape.browser.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.notscape.browser.data.AppStore
import net.notscape.browser.emu.ControlSpec
import net.notscape.browser.emu.GameLayout

/**
 * The touch-layout editor (Phase J, J2): the game's
 * controls on a dark canvas, in the player's frost
 * material. Drag a control to move it (the 8dp snap grid
 * is on by default, per Robert's spec), drag a selected
 * control's corner handle to resize it. Layouts persist
 * per console; Reset restores the shipping arrangement.
 */
@Composable
fun GameLayoutEditor(
    store: AppStore,
    console: String,
    onDone: () -> Unit
) {
    var specs by remember {
        mutableStateOf(GameLayout.load(store, console))
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var snapOn by remember { mutableStateOf(true) }
    val density = LocalDensity.current.density

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            TextButton(onClick = onDone) { Text("Back") }
            Text(
                "Edit controls",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Snap",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(6.dp))
            Switch(checked = snapOn, onCheckedChange = { snapOn = it })
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                specs = GameLayout.defaults(console)
                selectedId = null
            }) { Text("Reset") }
            Button(onClick = {
                GameLayout.save(store, console, specs)
                onDone()
            }) { Text("Done") }
        }
        Text(
            "Drag a control to move it. Tap one, then drag " +
                "its corner handle to resize.",
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
                    onSelect = { selectedId = spec.id },
                    onMove = { moved ->
                        specs = specs.map {
                            if (it.id == moved.id) moved else it
                        }
                    }
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
    onSelect: () -> Unit,
    onMove: (ControlSpec) -> Unit
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
            .pointerInput(spec.id, snapOn, canvasWDp, canvasHDp) {
                // The live spec comes through
                // rememberUpdatedState below - a captured
                // spec would freeze at its first value and
                // the control would fight the drag.
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
                                onMove(
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
