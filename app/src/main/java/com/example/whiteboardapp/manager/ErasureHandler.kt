package com.example.whiteboardapp.manager

import android.graphics.PathMeasure
import android.graphics.RectF
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.StrokePoint
import com.example.whiteboardapp.model.StylusStrokeObject
import com.example.whiteboardapp.model.TextObject
import kotlin.math.hypot

/**
 * Handles the logic of erasing objects from the canvas.
 *
 * For [StylusStrokeObject], erasure checks sample points along the stored [StrokePoint] list
 * (similar to the [DrawingObject.PathObject] check) but uses the point list directly
 * rather than [PathMeasure], which avoids the need to re-build the rendered path.
 */
class ErasureHandler {

    fun canEraseObject(
        obj: DrawingObject,
        eraserX: Float,
        eraserY: Float,
        eraserRadius: Float
    ): Boolean {
        val eraserBounds = RectF(
            eraserX - eraserRadius,
            eraserY - eraserRadius,
            eraserX + eraserRadius,
            eraserY + eraserRadius
        )

        // Quick bounding-box rejection
        if (!RectF.intersects(eraserBounds, obj.bounds)) return false

        // Type-specific precision check
        return when (obj) {
            is DrawingObject.PathObject -> isPathInEraserRange(obj, eraserX, eraserY, eraserRadius)
            is StylusStrokeObject       -> isStylusStrokeInEraserRange(obj, eraserX, eraserY, eraserRadius)
            is DrawingObject.ShapeObject,
            is TextObject               -> true  // Bounds check is sufficient for solid shapes/text
        }
    }

    // ── PathObject (finger strokes) ────────────────────────────────────────

    private fun isPathInEraserRange(
        pathObject: DrawingObject.PathObject,
        centerX: Float, centerY: Float, radius: Float
    ): Boolean {
        val pathMeasure = PathMeasure(pathObject.path, false)
        if (pathMeasure.length <= 0) return false

        val coords = FloatArray(2)
        val totalLength = pathMeasure.length
        val sampleCount = (totalLength * 0.5f).toInt().coerceIn(10, 50)
        val stepSize = totalLength / sampleCount

        pathMeasure.getPosTan(0f, coords, null)
        if (isPointInEraserRange(coords[0], coords[1], centerX, centerY, radius, pathObject.paint.strokeWidth)) {
            return true
        }
        for (i in 1..sampleCount) {
            val distance = (stepSize * i).coerceAtMost(totalLength)
            pathMeasure.getPosTan(distance, coords, null)
            if (isPointInEraserRange(coords[0], coords[1], centerX, centerY, radius, pathObject.paint.strokeWidth)) {
                return true
            }
        }
        return false
    }

    // ── StylusStrokeObject (stylus strokes) ────────────────────────────────

    /**
     * For stylus strokes we already have the point list; no PathMeasure needed.
     * Sample every Nth point for performance (at most 50 checks).
     */
    private fun isStylusStrokeInEraserRange(
        obj: StylusStrokeObject,
        centerX: Float, centerY: Float, radius: Float
    ): Boolean {
        val pts = obj.points
        if (pts.isEmpty()) return false

        val step = maxOf(1, pts.size / 50)
        var i = 0
        while (i < pts.size) {
            val p = pts[i]
            val effectiveRadius = (obj.baseWidth * p.pressure.coerceAtLeast(StrokePoint.MIN_PRESSURE)) / 2f
            if (isPointInEraserRange(p.x, p.y, centerX, centerY, radius, effectiveRadius * 2f)) {
                return true
            }
            i += step
        }
        // Always check the last point
        val last = pts.last()
        val effectiveRadius = (obj.baseWidth * last.pressure.coerceAtLeast(StrokePoint.MIN_PRESSURE)) / 2f
        return isPointInEraserRange(last.x, last.y, centerX, centerY, radius, effectiveRadius * 2f)
    }

    private fun isPointInEraserRange(
        pointX: Float, pointY: Float,
        eraserX: Float, eraserY: Float,
        eraserRadius: Float, strokeWidth: Float
    ): Boolean {
        val distance = hypot(pointX - eraserX, pointY - eraserY)
        return distance <= eraserRadius + (strokeWidth / 2f)
    }
}