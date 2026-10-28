package net.notscape.browser.emu

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Nintendo DS dual-screen geometry (DS build): pure
 * functions over pixel rectangles, so the layout math
 * is JVM-testable without a device. The core always
 * emits one combined 256x384 framebuffer (top screen
 * rows 0-191, bottom screen rows 192-383, gap forced
 * to 0 - verified against the pinned melonDS core),
 * and the frontend owns every arrangement from
 * there: two quads sampled from one texture, the
 * hinge gap as dead space between them, swap as a
 * UV swap, focus as one large quad plus one small
 * quad from the same texture.
 *
 * All inputs and outputs are in surface pixels; the
 * caller converts dp budgets (gap, margins, PiP
 * sizes, minimum band/screen) to pixels at its own
 * density. At the design's reference density (2.625)
 * the defaults reproduce the design's budgets
 * exactly: 16dp gap, 84dp side margins, 384x288
 * portrait PiP, 480x360 landscape PiP, 240px minimum
 * controls band, 480px minimum screen width.
 *
 * Swap in focus modes is not a flag: swapping there
 * flips which screen is main (FocusTop <->
 * FocusBottom), so [swapped] is ignored for focus
 * arrangements and the caller flips the arrangement
 * instead.
 */
enum class DsContent { TOP, BOTTOM }

enum class DsArrangement {
    STACKED,
    SIDE_BY_SIDE,
    STACKED_COLUMN,
    FOCUS_TOP,
    FOCUS_BOTTOM
}

enum class DsPipCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/** A rectangle in surface pixels: left/top are the
 * smaller coordinates, right/bottom the larger. */
data class DsRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** One displayed screen: its rectangle plus which
 * DS screen's content it shows. */
data class DsQuad(val rect: DsRect, val content: DsContent)

/** A complete arrangement: exactly two quads, the
 * hinge band (null in focus modes, where the two
 * screens do not share a hinge), the touchable
 * rectangle (the quad showing BOTTOM content, null
 * in focus-top where the touch screen is the PiP),
 * the PiP rectangle (focus modes only), and whether
 * the narrow-window rule fell back to focus. */
data class DsGeometry(
    val quads: List<DsQuad>,
    val hinge: DsRect?,
    val touchRect: DsRect?,
    val pipRect: DsRect?,
    val focusFallback: Boolean
)

object DsLayout {
    // Setting keys (same key family as layouts).
    fun arrangementKey(landscape: Boolean) =
        "gameScreens:ds:${if (landscape) "l" else "p"}"

    fun swapKey(landscape: Boolean) =
        "gameDsSwap:ds:${if (landscape) "l" else "p"}"

    const val PIP_CORNER_KEY = "gameDsPipCorner:ds"
    const val UPPER_TAP_KEY = "gameDsUpperTap:ds"
    const val LID_KEY = "gameDsLid:ds"
    const val MIC_KEY = "gameDsMic:ds"
    const val FORCE_STACKED_KEY = "gameDsForceStacked:ds"

    /** The libretro joypad bits the mic and lid
     * actions hold: the pinned core feeds microphone
     * noise while the L2 bit is held and closes the
     * lid while the L3 bit is held. DS has no L2/L3
     * buttons of its own, so these bits are free. */
    const val MIC_BIT = 12
    const val LID_BIT = 14

    fun arrangementFor(
        lookup: (String, String) -> String,
        landscape: Boolean
    ): DsArrangement {
        val raw = lookup(arrangementKey(landscape), "")
        val parsed = runCatching {
            DsArrangement.valueOf(raw)
        }.getOrNull()
        if (parsed != null && parsed in arrangementsFor(landscape)) {
            return parsed
        }
        return if (landscape) {
            DsArrangement.SIDE_BY_SIDE
        } else {
            DsArrangement.STACKED
        }
    }

    fun arrangementsFor(landscape: Boolean): List<DsArrangement> =
        if (landscape) {
            listOf(
                DsArrangement.SIDE_BY_SIDE,
                DsArrangement.STACKED_COLUMN,
                DsArrangement.FOCUS_TOP,
                DsArrangement.FOCUS_BOTTOM
            )
        } else {
            listOf(
                DsArrangement.STACKED,
                DsArrangement.FOCUS_TOP,
                DsArrangement.FOCUS_BOTTOM
            )
        }

    /** The chrome Layout pill cycles the current
     * orientation's arrangements in order. */
    fun nextArrangement(
        current: DsArrangement,
        landscape: Boolean
    ): DsArrangement {
        val list = arrangementsFor(landscape)
        val i = list.indexOf(current)
        return list[(if (i < 0) 0 else i + 1) % list.size]
    }

    fun arrangementLabel(a: DsArrangement): String = when (a) {
        DsArrangement.STACKED -> "Stacked"
        DsArrangement.SIDE_BY_SIDE -> "Side by side"
        DsArrangement.STACKED_COLUMN -> "Stacked column"
        DsArrangement.FOCUS_TOP -> "Focus: top"
        DsArrangement.FOCUS_BOTTOM -> "Focus: bottom"
    }

    fun pipCornerFor(
        lookup: (String, String) -> String
    ): DsPipCorner = runCatching {
        DsPipCorner.valueOf(lookup(PIP_CORNER_KEY, ""))
    }.getOrDefault(DsPipCorner.BOTTOM_RIGHT)

    fun pipCornerLabel(c: DsPipCorner): String = when (c) {
        DsPipCorner.TOP_LEFT -> "Top left"
        DsPipCorner.TOP_RIGHT -> "Top right"
        DsPipCorner.BOTTOM_LEFT -> "Bottom left"
        DsPipCorner.BOTTOM_RIGHT -> "Bottom right"
    }

    fun upperTapFor(lookup: (String, String) -> String): Boolean =
        lookup(UPPER_TAP_KEY, "false") == "true"

    /** Lid behaviour: "Pause" (default) or "Pass". */
    fun lidFor(lookup: (String, String) -> String): String {
        val raw = lookup(LID_KEY, "Pause")
        return if (raw == "Pass") "Pass" else "Pause"
    }

    fun micFor(lookup: (String, String) -> String): Boolean =
        lookup(MIC_KEY, "false") == "true"

    fun forceStackedFor(
        lookup: (String, String) -> String
    ): Boolean = lookup(FORCE_STACKED_KEY, "false") == "true"

    /** The full arrangement for a canvas. [cutoutTop]
     * is the portrait top inset in pixels (0 in
     * landscape - the caller passes it through).
     * [gap], [margin], [pipW]/[pipH], [minBand] and
     * [minScreenW] arrive in pixels, converted from
     * the design's dp budgets at the caller's
     * density. [integer] sizes each screen to a
     * whole multiple of native (256x192). */
    fun geometry(
        canvasW: Float,
        canvasH: Float,
        landscape: Boolean,
        arrangement: DsArrangement,
        swapped: Boolean,
        pipCorner: DsPipCorner,
        integer: Boolean,
        cutoutTop: Float,
        gap: Float,
        margin: Float,
        pipW: Float,
        pipH: Float,
        minBand: Float,
        minScreenW: Float,
        forceStacked: Boolean
    ): DsGeometry {
        return if (landscape) {
            landscapeGeometry(
                canvasW, canvasH, arrangement, swapped,
                pipCorner, integer, gap, margin,
                pipW, pipH
            )
        } else {
            portraitGeometry(
                canvasW, canvasH, arrangement, swapped,
                pipCorner, integer, cutoutTop, gap,
                pipW, pipH, minBand, minScreenW,
                forceStacked
            )
        }
    }

    private fun quadFor(
        firstIsTop: Boolean,
        first: DsRect,
        second: DsRect
    ): List<DsQuad> = if (firstIsTop) {
        listOf(
            DsQuad(first, DsContent.TOP),
            DsQuad(second, DsContent.BOTTOM)
        )
    } else {
        listOf(
            DsQuad(first, DsContent.BOTTOM),
            DsQuad(second, DsContent.TOP)
        )
    }

    private fun finish(
        quads: List<DsQuad>,
        hinge: DsRect?,
        pipRect: DsRect?,
        focusFallback: Boolean = false
    ): DsGeometry {
        // The touch screen is the quad showing
        // BOTTOM content - except when that content
        // is the PiP (focus-top), where taps swap
        // instead of drawing.
        val touch = if (pipRect != null &&
            quads.firstOrNull { it.content == DsContent.BOTTOM }
                ?.rect == pipRect
        ) {
            null
        } else {
            quads.firstOrNull { it.content == DsContent.BOTTOM }?.rect
        }
        return DsGeometry(quads, hinge, touch, pipRect, focusFallback)
    }

    private fun portraitGeometry(
        canvasW: Float,
        canvasH: Float,
        arrangement: DsArrangement,
        swapped: Boolean,
        pipCorner: DsPipCorner,
        integer: Boolean,
        cutoutTop: Float,
        gap: Float,
        pipW: Float,
        pipH: Float,
        minBand: Float,
        minScreenW: Float,
        forceStacked: Boolean
    ): DsGeometry {
        val availH = canvasH - cutoutTop
        when (arrangement) {
            DsArrangement.FOCUS_TOP,
            DsArrangement.FOCUS_BOTTOM -> {
                val mainIsTop =
                    arrangement == DsArrangement.FOCUS_TOP
                val mainW: Float
                val mainH: Float
                if (integer) {
                    val scale = max(
                        1, floor(canvasW / 256f).toInt()
                    )
                    mainW = scale * 256f
                    mainH = scale * 192f
                } else {
                    mainW = canvasW
                    mainH = canvasW * 0.75f
                }
                val mainX = (canvasW - mainW) / 2f
                val main = DsRect(
                    mainX, cutoutTop,
                    mainX + mainW, cutoutTop + mainH
                )
                // The PiP lives in the remaining
                // area below the main screen, in
                // the chosen corner (12px-class
                // margins come from the caller via
                // pipW/pipH sizing; the corner
                // itself is inset by gap).
                val restTop = cutoutTop + mainH
                val pip = cornerRect(
                    pipCorner,
                    DsRect(0f, restTop, canvasW, canvasH),
                    pipW, pipH, gap
                )
                val quads = if (mainIsTop) {
                    listOf(
                        DsQuad(main, DsContent.TOP),
                        DsQuad(pip, DsContent.BOTTOM)
                    )
                } else {
                    listOf(
                        DsQuad(main, DsContent.BOTTOM),
                        DsQuad(pip, DsContent.TOP)
                    )
                }
                return finish(quads, null, pip)
            }
            else -> {
                // Stacked (the only non-focus
                // portrait arrangement).
                var screenW: Float
                var screenH: Float
                if (integer) {
                    val scale = max(
                        1, floor(canvasW / 256f).toInt()
                    )
                    screenW = scale * 256f
                    screenH = scale * 192f
                } else {
                    screenW = canvasW
                    screenH = canvasW * 0.75f
                    val band = availH - (2 * screenH + gap)
                    if (band < minBand) {
                        // Compact stacked: shrink
                        // to guarantee the band.
                        screenH = (availH - gap - minBand) / 2f
                        screenW = screenH * 4f / 3f
                        if (screenW < minScreenW && !forceStacked) {
                            // Focus fallback: the
                            // touch screen goes
                            // main, the top screen
                            // becomes the PiP.
                            return portraitGeometry(
                                canvasW, canvasH,
                                DsArrangement.FOCUS_BOTTOM,
                                swapped, pipCorner, integer,
                                cutoutTop, gap, pipW, pipH,
                                minBand, minScreenW, forceStacked
                            ).copy(focusFallback = true)
                        }
                    }
                }
                val x = (canvasW - screenW) / 2f
                val top = DsRect(
                    x, cutoutTop,
                    x + screenW, cutoutTop + screenH
                )
                val bottom = DsRect(
                    x, cutoutTop + screenH + gap,
                    x + screenW, cutoutTop + 2 * screenH + gap
                )
                val hinge = DsRect(
                    0f, cutoutTop + screenH,
                    canvasW, cutoutTop + screenH + gap
                )
                return finish(
                    quadFor(!swapped, top, bottom),
                    hinge, null
                )
            }
        }
    }

    private fun landscapeGeometry(
        canvasW: Float,
        canvasH: Float,
        arrangement: DsArrangement,
        swapped: Boolean,
        pipCorner: DsPipCorner,
        integer: Boolean,
        gap: Float,
        margin: Float,
        pipW: Float,
        pipH: Float
    ): DsGeometry {
        when (arrangement) {
            DsArrangement.FOCUS_TOP,
            DsArrangement.FOCUS_BOTTOM -> {
                val mainIsTop =
                    arrangement == DsArrangement.FOCUS_TOP
                // The right column keeps the
                // design's 960px-class width (the
                // caller passes it via pipW/pipH
                // sizing only for the PiP; the
                // column itself is a fixed share:
                // main is height-limited, the
                // column takes the rest).
                val mainH = canvasH
                val mainW = canvasH * 4f / 3f
                val colLeft = min(mainW, canvasW)
                val main: DsRect
                val col: DsRect
                if (integer) {
                    val scale = max(
                        1,
                        min(
                            floor(colLeft / 256f),
                            floor(canvasH / 192f)
                        ).toInt()
                    )
                    val w = scale * 256f
                    val h = scale * 192f
                    main = DsRect(
                        (colLeft - w) / 2f,
                        (canvasH - h) / 2f,
                        (colLeft - w) / 2f + w,
                        (canvasH - h) / 2f + h
                    )
                    col = DsRect(
                        colLeft, 0f, canvasW, canvasH
                    )
                } else {
                    main = DsRect(0f, 0f, mainW, mainH)
                    col = DsRect(colLeft, 0f, canvasW, canvasH)
                }
                val pip = cornerRect(
                    pipCorner, col, pipW, pipH, gap
                )
                val quads = if (mainIsTop) {
                    listOf(
                        DsQuad(main, DsContent.TOP),
                        DsQuad(pip, DsContent.BOTTOM)
                    )
                } else {
                    listOf(
                        DsQuad(main, DsContent.BOTTOM),
                        DsQuad(pip, DsContent.TOP)
                    )
                }
                return finish(quads, null, pip)
            }
            DsArrangement.STACKED_COLUMN -> {
                var screenW: Float
                var screenH: Float
                if (integer) {
                    val scale = max(
                        1,
                        min(
                            floor((canvasH - gap) / 2f / 192f),
                            floor(canvasW / 256f)
                        ).toInt()
                    )
                    screenW = scale * 256f
                    screenH = scale * 192f
                } else {
                    screenH = (canvasH - gap) / 2f
                    screenW = screenH * 4f / 3f
                }
                val x = (canvasW - screenW) / 2f
                val top = DsRect(
                    x, 0f, x + screenW, screenH
                )
                val bottom = DsRect(
                    x, screenH + gap,
                    x + screenW, 2 * screenH + gap
                )
                val hinge = DsRect(
                    0f, screenH, canvasW, screenH + gap
                )
                return finish(
                    quadFor(!swapped, top, bottom),
                    hinge, null
                )
            }
            else -> {
                // Side-by-side (the default).
                var screenW: Float
                var screenH: Float
                if (integer) {
                    val per = (canvasW - 2 * margin - gap) / 2f
                    val scale = max(
                        1,
                        min(
                            floor(per / 256f),
                            floor(canvasH / 192f)
                        ).toInt()
                    )
                    screenW = scale * 256f
                    screenH = scale * 192f
                } else {
                    screenW = (canvasW - 2 * margin - gap) / 2f
                    screenH = screenW * 0.75f
                    if (screenH > canvasH) {
                        screenH = canvasH
                        screenW = screenH * 4f / 3f
                    }
                }
                val regionW = 2 * screenW + gap
                val x0 = (canvasW - regionW) / 2f
                val y = (canvasH - screenH) / 2f
                val left = DsRect(
                    x0, y, x0 + screenW, y + screenH
                )
                val right = DsRect(
                    x0 + screenW + gap, y,
                    x0 + 2 * screenW + gap, y + screenH
                )
                val hinge = DsRect(
                    x0 + screenW, y,
                    x0 + screenW + gap, y + screenH
                )
                return finish(
                    quadFor(!swapped, left, right),
                    hinge, null
                )
            }
        }
    }

    /** A PiP rectangle inside [region] at [corner],
     * inset from the region's edges by [inset]. */
    private fun cornerRect(
        corner: DsPipCorner,
        region: DsRect,
        w: Float,
        h: Float,
        inset: Float
    ): DsRect {
        val x = when (corner) {
            DsPipCorner.TOP_LEFT,
            DsPipCorner.BOTTOM_LEFT -> region.left + inset
            DsPipCorner.TOP_RIGHT,
            DsPipCorner.BOTTOM_RIGHT -> region.right - inset - w
        }
        val y = when (corner) {
            DsPipCorner.TOP_LEFT,
            DsPipCorner.TOP_RIGHT -> region.top + inset
            DsPipCorner.BOTTOM_LEFT,
            DsPipCorner.BOTTOM_RIGHT -> region.bottom - inset - h
        }
        return DsRect(x, y, x + w, y + h)
    }

    /** A touch at fractions (u, v) across/down the
     * touch quad maps to the libretro pointer
     * device, whose coordinates the pinned core
     * reads against the FULL 256x384 combined
     * buffer (verified against the pinned source):
     * bufferX = u * 256, bufferY = 192 + v * 192,
     * pointer = round(buffer * 65536 / bufferSize)
     * - 32768, clamped to int16 (the far edge
     * would otherwise round to 32768). */
    fun pointerFor(u: Float, v: Float): Pair<Int, Int> {
        val cu = u.coerceIn(0f, 1f)
        val cv = v.coerceIn(0f, 1f)
        val bx = cu * 256f
        val by = 192f + cv * 192f
        val px = (bx * 65536f / 256f).roundToInt() - 32768
        val py = (by * 65536f / 384f).roundToInt() - 32768
        return px.coerceIn(-32768, 32767) to
            py.coerceIn(-32768, 32767)
    }

    /** A pixel rectangle as an NDC quad for the
     * native presenter: left, bottom, right, top
     * (GL y is up, pixel y is down), plus the UV
     * sub-rect for the quad's content (top screen
     * v 0..0.5, bottom screen v 0.5..1.0 - the
     * texture's row 0 is the image top). */
    fun ndcQuad(
        rect: DsRect,
        content: DsContent,
        canvasW: Float,
        canvasH: Float
    ): FloatArray {
        val left = rect.left / canvasW * 2f - 1f
        val right = rect.right / canvasW * 2f - 1f
        val top = 1f - rect.top / canvasH * 2f
        val bottom = 1f - rect.bottom / canvasH * 2f
        val (v0, v1) = if (content == DsContent.TOP) {
            0f to 0.5f
        } else {
            0.5f to 1f
        }
        return floatArrayOf(
            left, bottom, right, top, 0f, v0, 1f, v1
        )
    }
}
