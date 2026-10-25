package net.notscape.browser.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Long-press-drag reorder (beta.6), hand-rolled: no
 * new dependency. One state per reorderable surface
 * (one per console group on the home, one per
 * console page). The dragged item lifts and follows
 * the finger; the other items stay put until drop,
 * when the dragged id moves to the drop target's
 * slot (before it, or after it when the finger is
 * past the target's centre). A long-press released
 * without movement is not a drop: the caller's
 * onLongPress runs instead (the tile menu), and the
 * tap guard stops the same gesture launching the
 * game. Reordering works within the visible window
 * only; long lists are reordered in stages (drop,
 * scroll, drag again) - there is no auto-scroll.
 */
class ReorderState {
    var dragId: String? by mutableStateOf(null)
        private set
    var dropTargetId: String? by mutableStateOf(null)
        private set
    var dropAfter: Boolean = false
        private set
    var dragDelta: IntOffset by mutableStateOf(IntOffset.Zero)
        private set
    val bounds = mutableMapOf<String, Rect>()
    private var grabRect: Rect? = null
    private var grabOffset: Offset = Offset.Zero
    private var moved = false
    private var tapGuardId: String? = null

    fun startDrag(id: String, grab: Offset) {
        dragId = id
        grabOffset = grab
        grabRect = bounds[id]
        moved = false
        dragDelta = IntOffset.Zero
        dropTargetId = null
        dropAfter = false
        tapGuardId = id
    }

    fun move(amount: Offset) {
        val id = dragId ?: return
        moved = true
        dragDelta = IntOffset(
            dragDelta.x + amount.x.roundToInt(),
            dragDelta.y + amount.y.roundToInt()
        )
        val rect = grabRect ?: return
        val finger = Offset(
            rect.left + grabOffset.x + dragDelta.x,
            rect.top + grabOffset.y + dragDelta.y
        )
        val nearest = bounds.entries
            .filter { it.key != id }
            .minByOrNull { (_, r) ->
                (r.center - finger).getDistance()
            } ?: return
        dropTargetId = nearest.key
        val c = nearest.value.center
        dropAfter =
            if (kotlin.math.abs(finger.y - c.y) >
                kotlin.math.abs(finger.x - c.x)
            ) finger.y > c.y else finger.x > c.x
    }

    /** True when this tap is the tail of a drag (or a
     * plain long-press) and must be swallowed. */
    fun takeTapGuard(id: String): Boolean {
        if (tapGuardId != id) return false
        tapGuardId = null
        return true
    }

    fun clearDrag() {
        dragId = null
        dropTargetId = null
        dropAfter = false
        dragDelta = IntOffset.Zero
        grabRect = null
        moved = false
    }

    fun wasMoved(): Boolean = moved
}

/**
 * Makes one card or tile draggable within its
 * surface. [onDrop] runs on a real drop;
 * [onLongPress] runs on a long-press released
 * without movement.
 */
fun Modifier.reorderableItem(
    state: ReorderState,
    id: String,
    onDrop: (dragId: String, targetId: String, after: Boolean) -> Unit,
    onLongPress: (() -> Unit)? = null
): Modifier {
    val dragging = state.dragId == id
    return this
        .onGloballyPositioned { coords ->
            state.bounds[id] = coords.boundsInRoot()
        }
        .offset {
            if (dragging) state.dragDelta else IntOffset.Zero
        }
        .zIndex(if (dragging) 1f else 0f)
        .pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    state.startDrag(id, offset)
                },
                onDragEnd = {
                    val drag = state.dragId
                    val target = state.dropTargetId
                    val after = state.dropAfter
                    val wasMoved = state.wasMoved()
                    state.clearDrag()
                    if (drag != null) {
                        if (wasMoved && target != null &&
                            target != drag
                        ) {
                            onDrop(drag, target, after)
                        } else if (!wasMoved) {
                            onLongPress?.invoke()
                        }
                    }
                },
                onDragCancel = { state.clearDrag() },
                onDrag = { change, amount ->
                    change.consume()
                    state.move(amount)
                }
            )
        }
}

 /**
  * Plain-drag move mode (tile menu "Move"): the
  * caller has already chosen this item, so no
  * long-press is needed - a normal drag moves it.
  * [onEnd] runs on every drag end or cancel, so
  * the caller can leave move mode; a drop runs
  * [onDrop] first.
  */
fun Modifier.dragMoveItem(
    state: ReorderState,
    id: String,
    onDrop: (dragId: String, targetId: String, after: Boolean) -> Unit,
    onEnd: () -> Unit
): Modifier {
    val dragging = state.dragId == id
    return this
        .onGloballyPositioned { coords ->
            state.bounds[id] = coords.boundsInRoot()
        }
        .offset {
            if (dragging) state.dragDelta else IntOffset.Zero
        }
        .zIndex(if (dragging) 1f else 0f)
        .pointerInput(id) {
            detectDragGestures(
                onDragStart = { offset ->
                    state.startDrag(id, offset)
                },
                onDragEnd = {
                    val drag = state.dragId
                    val target = state.dropTargetId
                    val after = state.dropAfter
                    val wasMoved = state.wasMoved()
                    state.clearDrag()
                    if (drag != null && wasMoved &&
                        target != null && target != drag
                    ) {
                        onDrop(drag, target, after)
                    }
                    onEnd()
                },
                onDragCancel = {
                    state.clearDrag()
                    onEnd()
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.move(amount)
                }
            )
        }
}
