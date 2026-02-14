package com.example.whiteboardapp.manager

import android.view.MotionEvent
import com.example.whiteboardapp.model.StrokePoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Processes raw [MotionEvent]s for stylus-aware drawing.
 *
 * Responsibilities:
 *  1. **Tool type discrimination** — Classifies each pointer as STYLUS, ERASER, FINGER,
 *     or MOUSE using [MotionEvent.getToolType].
 *  2. **Palm rejection** — Maintains a state machine: once a STYLUS (or ERASER) pointer
 *     goes down, all FINGER pointers that arrive afterwards are flagged as palm and
 *     their events are consumed but not forwarded to drawing logic.
 *  3. **Historical point extraction** — Android batches [MotionEvent.ACTION_MOVE] events
 *     between Choreographer frames. [MotionEvent.historySize] can be 8–16 at 120 Hz input.
 *     Not draining historical data produces choppy, under-sampled strokes. We extract every
 *     historical sample alongside the current frame's sample.
 *  4. **Pressure normalization** — Some digitizers report max pressure < 1.0 (e.g. 0.6,
 *     or even > 1.0 in rare firmware bugs). We dynamically track the maximum observed
 *     pressure and divide by it so the range stays [0, 1].
 *  5. **Tilt decomposition** — Converts [MotionEvent.AXIS_TILT] (angle from vertical, radians)
 *     and [MotionEvent.AXIS_ORIENTATION] (pen rotation around own axis, radians) into a
 *     2D tilt vector (tiltX, tiltY) suitable for the renderer's calligraphy algorithm.
 *
 * Usage:
 * ```kotlin
 * val processor = StylusInputProcessor()
 *
 * override fun onTouchEvent(event: MotionEvent): Boolean {
 *     val input = processor.processEvent(event)
 *     if (input.isPalmRejected) return true   // consume silently
 *     if (input.source == ERASER) { ... }
 *     drawWith(input.points)
 * }
 *
 * override fun onHoverEvent(event: MotionEvent): Boolean {
 *     val hover = processor.processHoverEvent(event)
 *     showCursorAt(hover.points.first())
 *     return true
 * }
 * ```
 */
class StylusInputProcessor {

    // ── Enumerations ─────────────────────────────────────────────────────────

    enum class InputSource {
        STYLUS,   // Active stylus / S Pen tip
        ERASER,   // Back eraser end of stylus (TOOL_TYPE_ERASER)
        FINGER,   // Direct finger touch
        MOUSE,    // External mouse / trackpad
        UNKNOWN   // Unrecognized tool type
    }

    /**
     * The result of processing one [MotionEvent].
     *
     * @property source          What kind of input produced this event.
     * @property points          All extracted [StrokePoint]s (historical + current), in
     *                           **screen coordinates**. Caller must transform to canvas space.
     * @property isPalmRejected  True if this finger event arrived while a stylus is active.
     *                           The caller should consume the event without acting on it.
     */
    data class ProcessedInput(
        val source: InputSource,
        val points: List<StrokePoint>,
        val isPalmRejected: Boolean
    )

    // ── State ─────────────────────────────────────────────────────────────────

    /** True while at least one STYLUS or ERASER pointer is pressed. */
    private var isStylusActive = false

    /** Pointer ID of the active stylus. Used to detect stylus lift. */
    private var activeStylusPointerId = MotionEvent.INVALID_POINTER_ID

    /**
     * Tracks the maximum pressure observed across all events.
     * Used for per-session normalization without requiring a calibration step.
     */
    private var maxObservedPressure = 1f

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main entry point: processes a touch [MotionEvent] and returns structured input data.
     *
     * Must be called for every event, including those from scale/gesture detectors,
     * so that the palm-rejection state machine stays consistent.
     */
    fun processEvent(event: MotionEvent): ProcessedInput {
        // Identify the active pointer for this event
        val pointerIndex = event.actionIndex
        val source = getSourceForPointer(event, pointerIndex)

        // Update palm-rejection state machine
        updateStylusState(event, source, pointerIndex)

        val isPalmRejected = source == InputSource.FINGER && isStylusActive

        val points = if (!isPalmRejected) {
            // Find the most appropriate pointer to extract data from
            val primaryIndex = findPrimaryPointerIndex(event)
            extractAllPoints(event, primaryIndex)
        } else {
            emptyList()
        }

        return ProcessedInput(
            source = source,
            points = points,
            isPalmRejected = isPalmRejected
        )
    }

    /**
     * Processes a hover [MotionEvent] (ACTION_HOVER_MOVE / ACTION_HOVER_ENTER / EXIT).
     * Returns a single-point [ProcessedInput] at the hover position with pressure = 0.
     */
    fun processHoverEvent(event: MotionEvent): ProcessedInput {
        val point = StrokePoint(
            x = event.x,
            y = event.y,
            pressure = 0f,
            tiltX = computeTiltX(
                event.getAxisValue(MotionEvent.AXIS_TILT),
                event.getAxisValue(MotionEvent.AXIS_ORIENTATION)
            ),
            tiltY = computeTiltY(
                event.getAxisValue(MotionEvent.AXIS_TILT),
                event.getAxisValue(MotionEvent.AXIS_ORIENTATION)
            ),
            timestamp = event.eventTime
        )
        return ProcessedInput(
            source = InputSource.STYLUS,
            points = listOf(point),
            isPalmRejected = false
        )
    }

    /** Resets all state (call when the active session/canvas changes). */
    fun reset() {
        isStylusActive = false
        activeStylusPointerId = MotionEvent.INVALID_POINTER_ID
        // Note: maxObservedPressure intentionally not reset — it represents device calibration.
    }

    // ── Palm rejection state machine ─────────────────────────────────────────

    private fun updateStylusState(event: MotionEvent, source: InputSource, pointerIndex: Int) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (source == InputSource.STYLUS || source == InputSource.ERASER) {
                    isStylusActive = true
                    activeStylusPointerId = event.getPointerId(pointerIndex)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val liftedId = event.getPointerId(pointerIndex)
                if (liftedId == activeStylusPointerId) {
                    isStylusActive = false
                    activeStylusPointerId = MotionEvent.INVALID_POINTER_ID
                }
            }
        }
    }

    // ── Point extraction ──────────────────────────────────────────────────────

    /**
     * Prefers a STYLUS or ERASER pointer if one exists; otherwise falls back to pointer 0.
     * This ensures we track the correct pointer in multi-touch scenarios.
     */
    private fun findPrimaryPointerIndex(event: MotionEvent): Int {
        for (i in 0 until event.pointerCount) {
            val type = event.getToolType(i)
            if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER) {
                return i
            }
        }
        return 0
    }

    /**
     * Extracts all historical + current samples for the given pointer index.
     *
     * Historical points are delivered in chronological order (oldest first),
     * so we append the current sample at the end.
     */
    private fun extractAllPoints(event: MotionEvent, pointerIndex: Int): List<StrokePoint> {
        val capacity = event.historySize + 1
        val points = ArrayList<StrokePoint>(capacity)

        // Historical samples (batched between frames)
        for (h in 0 until event.historySize) {
            points.add(
                StrokePoint(
                    x = event.getHistoricalX(pointerIndex, h),
                    y = event.getHistoricalY(pointerIndex, h),
                    pressure = normalizePressure(event.getHistoricalPressure(pointerIndex, h)),
                    tiltX = computeTiltX(
                        event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, h),
                        event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex, h)
                    ),
                    tiltY = computeTiltY(
                        event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, h),
                        event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex, h)
                    ),
                    timestamp = event.getHistoricalEventTime(h)
                )
            )
        }

        // Current sample
        points.add(
            StrokePoint(
                x = event.getX(pointerIndex),
                y = event.getY(pointerIndex),
                pressure = normalizePressure(event.getPressure(pointerIndex)),
                tiltX = computeTiltX(
                    event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
                    event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)
                ),
                tiltY = computeTiltY(
                    event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
                    event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)
                ),
                timestamp = event.eventTime
            )
        )

        return points
    }

    // ── Pressure normalization ────────────────────────────────────────────────

    /**
     * Clamps and normalizes raw pressure to [0, 1].
     *
     * We track the session maximum and normalize against it. This handles both
     * devices that report max < 1.0 and occasional erroneous spikes > 1.0.
     */
    private fun normalizePressure(raw: Float): Float {
        val clamped = raw.coerceAtLeast(0f)
        if (clamped > maxObservedPressure) {
            maxObservedPressure = clamped
        }
        return if (maxObservedPressure > 0f) (clamped / maxObservedPressure).coerceIn(0f, 1f) else 1f
    }

    // ── Tilt decomposition ────────────────────────────────────────────────────

    /**
     * Projects the stylus tilt onto the X axis of the canvas plane.
     *
     * [MotionEvent.AXIS_TILT] is the angle between the stylus and vertical (0 = straight up,
     * π/2 = lying flat). [MotionEvent.AXIS_ORIENTATION] is the azimuth of the pen's shadow
     * on the tablet surface (0 = pointing toward the top, increasing clockwise).
     *
     * sin(tilt) gives the magnitude of the shadow, cos(orientation) gives the X component.
     */
    private fun computeTiltX(tilt: Float, orientation: Float): Float =
        sin(tilt.toDouble()).toFloat() * cos(orientation.toDouble()).toFloat()

    /**
     * Projects the stylus tilt onto the Y axis of the canvas plane.
     * See [computeTiltX] for the full explanation.
     */
    private fun computeTiltY(tilt: Float, orientation: Float): Float =
        sin(tilt.toDouble()).toFloat() * sin(orientation.toDouble()).toFloat()

    // ── Tool type helpers ─────────────────────────────────────────────────────

    private fun getSourceForPointer(event: MotionEvent, pointerIndex: Int): InputSource {
        val safeIndex = pointerIndex.coerceIn(0, event.pointerCount - 1)
        return when (event.getToolType(safeIndex)) {
            MotionEvent.TOOL_TYPE_STYLUS -> InputSource.STYLUS
            MotionEvent.TOOL_TYPE_ERASER -> InputSource.ERASER
            MotionEvent.TOOL_TYPE_FINGER -> InputSource.FINGER
            MotionEvent.TOOL_TYPE_MOUSE  -> InputSource.MOUSE
            else                          -> InputSource.UNKNOWN
        }
    }

    /** True while a stylus is currently pressed (useful for external query). */
    val isStylusCurrentlyActive: Boolean get() = isStylusActive
}