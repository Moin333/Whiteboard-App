package com.example.whiteboardapp.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.example.whiteboardapp.model.StrokePoint
import kotlin.math.hypot

/**
 * Renders variable-width strokes from a list of [StrokePoint]s.
 *
 * Two rendering modes:
 *
 *  1. **[buildStrokePath]** — Builds a closed, filled [Path] representing the full stroke
 *     outline. This is the "final quality" path stored in [StylusStrokeObject] and drawn
 *     during [refreshCanvas]. Uses the midpoint-bézier outline algorithm for smooth curves.
 *
 *  2. **[drawLivePreview]** — Draws directly to a [Canvas] using overlapping filled circles
 *     (stamp method). This is called each frame during active drawing for low-latency visual
 *     feedback before the stroke is committed. Much cheaper than rebuilding the full path.
 *
 * ## Outline Algorithm (buildStrokePath)
 *  For each sampled point:
 *    - Compute the tangent direction from neighbouring points.
 *    - Compute the outward normal (perpendicular).
 *    - Offset left and right by `(pressure × baseWidth) / 2`.
 *    - Optionally apply tilt asymmetry for a calligraphic effect.
 *  Walk the left-edge forward + right-edge backward, connecting with quadratic bézier curves
 *  using the midpoint technique (original points become control points, midpoints are anchors).
 *  Add filled circular start/end caps.
 *
 * ## Pressure Smoothing
 *  Raw pressure is smoothed with a bidirectional exponential moving average (forward + backward
 *  pass). This removes digitizer jitter from cheap panels while preserving intentional
 *  pressure changes.
 */
object VariableWidthStrokeRenderer {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Builds a filled, closed [Path] for the given stroke points.
     *
     * @param points        Stroke points in canvas coordinates.
     * @param baseWidth     Width of stroke at pressure = 1.0.
     * @param isTiltEnabled Whether to apply tilt-based width asymmetry.
     */
    fun buildStrokePath(
        points: List<StrokePoint>,
        baseWidth: Float,
        isTiltEnabled: Boolean = true
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path

        // Single point: draw a circle
        if (points.size == 1) {
            val radius = (baseWidth * points[0].pressure.coerceAtLeast(StrokePoint.MIN_PRESSURE)) / 2f
            path.addCircle(points[0].x, points[0].y, radius.coerceAtLeast(1f), Path.Direction.CW)
            return path
        }

        val smoothedPressures = smoothPressures(points.map { it.pressure })

        // Build left and right edge point lists in canvas space
        val leftEdge = ArrayList<PointF>(points.size)
        val rightEdge = ArrayList<PointF>(points.size)

        for (i in points.indices) {
            val p = points[i]
            val pressure = smoothedPressures[i].coerceAtLeast(StrokePoint.MIN_PRESSURE)
            val halfWidth = (baseWidth * pressure) / 2f

            val tangent = getTangentAt(points, i)
            // Left-pointing normal (rotate tangent 90° counter-clockwise)
            val nx = -tangent.y
            val ny = tangent.x

            var leftHalf = halfWidth
            var rightHalf = halfWidth

            // ── Tilt asymmetry (calligraphic effect) ──────────────────────────
            // AXIS_TILT: 0 = vertical pen, π/2 = flat pen.
            // The tilt vector (tiltX, tiltY) describes which direction the pen leans.
            // We project that onto the stroke's normal to compute asymmetric widths.
            if (isTiltEnabled) {
                val tiltMagnitude = hypot(p.tiltX, p.tiltY).coerceIn(0f, 1f)
                if (tiltMagnitude > 0.08f) {
                    val tiltDirX = p.tiltX / tiltMagnitude
                    val tiltDirY = p.tiltY / tiltMagnitude
                    // How much of the tilt is in the left-normal direction?
                    val projection = tiltDirX * nx + tiltDirY * ny
                    // Scale: at full tilt (1.0) & full projection (1.0) → ±40% width change
                    val asymmetry = projection * tiltMagnitude * halfWidth * 0.4f
                    leftHalf = (halfWidth + asymmetry).coerceAtLeast(halfWidth * 0.1f)
                    rightHalf = (halfWidth - asymmetry).coerceAtLeast(halfWidth * 0.1f)
                }
            }

            leftEdge.add(PointF(p.x + nx * leftHalf, p.y + ny * leftHalf))
            rightEdge.add(PointF(p.x - nx * rightHalf, p.y - ny * rightHalf))
        }

        // ── Build closed outline path ─────────────────────────────────────────
        val outlinePath = Path()
        buildSmoothPolyline(outlinePath, leftEdge, moveTo = true)
        buildSmoothPolyline(outlinePath, ArrayList(rightEdge.reversed()), moveTo = false)
        outlinePath.close()
        path.addPath(outlinePath)

        // ── Round caps ────────────────────────────────────────────────────────
        val startRadius = (baseWidth * smoothedPressures.first().coerceAtLeast(StrokePoint.MIN_PRESSURE)) / 2f
        val endRadius = (baseWidth * smoothedPressures.last().coerceAtLeast(StrokePoint.MIN_PRESSURE)) / 2f
        path.addCircle(points.first().x, points.first().y, startRadius.coerceAtLeast(1f), Path.Direction.CW)
        path.addCircle(points.last().x, points.last().y, endRadius.coerceAtLeast(1f), Path.Direction.CW)

        path.fillType = Path.FillType.WINDING
        return path
    }

    /**
     * Fast live-preview renderer. Draws overlapping filled circles (stamps) directly
     * onto [canvas]. No Path building — O(n) draw calls, one per point.
     *
     * The overlapping circles naturally produce smooth, tapered edges. The method also
     * draws tapered trapezoid segments between consecutive points to fill gaps when the
     * input rate is lower than the canvas refresh rate.
     *
     * @param points    All stroke points collected so far (canvas coordinates).
     * @param color     Stroke color.
     * @param baseWidth Maximum stroke width at full pressure.
     */
    fun drawLivePreview(
        canvas: Canvas,
        points: List<StrokePoint>,
        color: Int,
        baseWidth: Float
    ) {
        if (points.isEmpty()) return

        val fillPaint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val firstRadius = (baseWidth * points[0].pressure.coerceAtLeast(StrokePoint.MIN_PRESSURE)) / 2f
        canvas.drawCircle(points[0].x, points[0].y, firstRadius.coerceAtLeast(1f), fillPaint)

        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]
            val w1 = baseWidth * p1.pressure.coerceAtLeast(StrokePoint.MIN_PRESSURE)
            val w2 = baseWidth * p2.pressure.coerceAtLeast(StrokePoint.MIN_PRESSURE)
            drawTaperedSegment(canvas, p1.x, p1.y, w1, p2.x, p2.y, w2, fillPaint)

            val radius = w2 / 2f
            canvas.drawCircle(p2.x, p2.y, radius.coerceAtLeast(1f), fillPaint)
        }
    }

    /**
     * Draws a hover indicator (cursor) when the stylus is near but not touching the screen.
     * Shows a small crosshair circle at the hover position.
     */
    fun drawHoverIndicator(canvas: Canvas, x: Float, y: Float, color: Int, baseWidth: Float) {
        val radius = (baseWidth / 2f).coerceIn(8f, 32f)
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
            alpha = 160
        }
        // Outer circle
        canvas.drawCircle(x, y, radius, paint)
        // Inner crosshair
        val crossSize = radius * 0.4f
        canvas.drawLine(x - crossSize, y, x + crossSize, y, paint)
        canvas.drawLine(x, y - crossSize, x, y + crossSize, paint)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Draws a filled tapered trapezoid between two circle-ends.
     * Used in [drawLivePreview] to fill gaps between stamp circles.
     */
    private fun drawTaperedSegment(
        canvas: Canvas,
        x1: Float, y1: Float, w1: Float,
        x2: Float, y2: Float, w2: Float,
        paint: Paint
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < 0.5f) return

        val nx = -dy / len  // left normal
        val ny = dx / len

        val r1 = w1 / 2f
        val r2 = w2 / 2f

        val trapPath = Path().apply {
            moveTo(x1 + nx * r1, y1 + ny * r1)
            lineTo(x2 + nx * r2, y2 + ny * r2)
            lineTo(x2 - nx * r2, y2 - ny * r2)
            lineTo(x1 - nx * r1, y1 - ny * r1)
            close()
        }
        canvas.drawPath(trapPath, paint)
    }

    /**
     * Appends a smooth curve through [points] to [path] using the midpoint-bézier technique.
     *
     * The midpoint technique: treat each original point as a quadratic bézier *control* point,
     * and the midpoint between adjacent pairs as the *anchor* points. This guarantees the
     * resulting curve passes through all midpoints smoothly, with C1 continuity.
     *
     * @param moveTo If true, starts with [Path.moveTo]; if false, connects to existing path
     *               with [Path.lineTo] to the first midpoint (for the reversed right edge).
     */
    private fun buildSmoothPolyline(path: Path, points: ArrayList<PointF>, moveTo: Boolean) {
        if (points.isEmpty()) return

        if (points.size == 1) {
            if (moveTo) path.moveTo(points[0].x, points[0].y)
            else path.lineTo(points[0].x, points[0].y)
            return
        }

        if (moveTo) {
            // Start at the midpoint between points[0] and points[1]
            val firstMid = midPoint(points[0], points[1])
            path.moveTo(firstMid.x, firstMid.y)
        } else {
            val firstMid = midPoint(points[0], points[1])
            path.lineTo(points[0].x, points[0].y)  // straight line to first point
            path.lineTo(firstMid.x, firstMid.y)
        }

        for (i in 1 until points.size - 1) {
            val mid = midPoint(points[i], points[i + 1])
            path.quadTo(points[i].x, points[i].y, mid.x, mid.y)
        }

        // End at the last point
        path.lineTo(points.last().x, points.last().y)
    }

    /**
     * Computes the unit tangent direction at point index [i].
     * Uses the chord from the previous to the next point (central difference),
     * falling back to the forward/backward difference at the endpoints.
     */
    private fun getTangentAt(points: List<StrokePoint>, i: Int): PointF {
        val (dx, dy) = when {
            points.size == 1 -> Pair(1f, 0f)
            i == 0 -> Pair(points[1].x - points[0].x, points[1].y - points[0].y)
            i == points.size - 1 -> {
                val prev = points[points.size - 2]
                Pair(points.last().x - prev.x, points.last().y - prev.y)
            }
            else -> Pair(points[i + 1].x - points[i - 1].x, points[i + 1].y - points[i - 1].y)
        }
        val len = hypot(dx, dy)
        return if (len < 1e-4f) PointF(1f, 0f) else PointF(dx / len, dy / len)
    }

    /**
     * Bidirectional exponential moving average for pressure smoothing.
     *
     * A forward pass followed by a backward pass eliminates phase lag. The result is
     * a zero-phase low-pass filter that removes jitter from cheap pressure sensors while
     * preserving intentional ramps (e.g., pressing harder mid-stroke).
     *
     * @param pressures Raw pressure values in [0, 1].
     * @return Smoothed pressure values.
     */
    private fun smoothPressures(pressures: List<Float>): List<Float> {
        if (pressures.size <= 2) return pressures

        val alpha = 0.35f  // Smoothing factor: lower = smoother but more lag

        // Forward pass
        val fwd = FloatArray(pressures.size)
        fwd[0] = pressures[0]
        for (i in 1 until pressures.size) {
            fwd[i] = alpha * pressures[i] + (1f - alpha) * fwd[i - 1]
        }

        // Backward pass (eliminates the phase lag introduced by the forward pass)
        val result = FloatArray(pressures.size)
        result[pressures.size - 1] = fwd[pressures.size - 1]
        for (i in pressures.size - 2 downTo 0) {
            result[i] = alpha * fwd[i] + (1f - alpha) * result[i + 1]
        }

        return result.toList()
    }

    private fun midPoint(a: PointF, b: PointF) = PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}