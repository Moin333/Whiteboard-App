package com.example.whiteboardapp.manager

import android.graphics.PathMeasure
import android.graphics.RectF
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.TextObject
import kotlin.math.hypot

/**
 * A helper class dedicated to handling the logic of erasing objects.
 * It provides more precise collision detection than simple bounding box checks,
 * especially for complex Path objects.
 */
class ErasureHandler {

    /**
     * Checks if a given object should be erased based on the eraser's position and size.
     *
     * @param obj The [DrawingObject] to check.
     * @param eraserX The center x-coordinate of the eraser.
     * @param eraserY The center y-coordinate of the eraser.
     * @param eraserRadius The radius of the eraser.
     * @return True if the object intersects with the eraser's area, false otherwise.
     */
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

        // Step 1: Perform a quick, low-cost bounding box intersection test first.
        if (!RectF.intersects(eraserBounds, obj.bounds)) {
            return false
        }

        // Step 2: If the bounds intersect, perform a more accurate, object-specific check.
        return when (obj) {
            is DrawingObject.PathObject -> {
                // For paths, we need to check points along the path itself.
                isPathInEraserRange(obj, eraserX, eraserY, eraserRadius)
            }
            is DrawingObject.ShapeObject, is TextObject -> {
                // For solid shapes and text, the bounds check is sufficient.
                true
            }
        }
    }

    /**
     * Performs a more accurate intersection check for Path objects by sampling points
     * along the path's length and checking their distance from the eraser's center.
     *
     * @return True if any sampled point on the path is within the eraser's range.
     */
    private fun isPathInEraserRange(
        pathObject: DrawingObject.PathObject,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Boolean {
        val pathMeasure = PathMeasure(pathObject.path, false)
        if (pathMeasure.length <= 0) return false

        val coords = FloatArray(2)
        val totalLength = pathMeasure.length

        // Use adaptive sampling - more samples for longer paths
        val minSamples = 10
        val maxSamples = 50
        val samplesPerUnit = 0.5f // samples per pixel
        // Adaptively sample the path to balance performance and accuracy.
        val sampleCount = (totalLength * samplesPerUnit).toInt().coerceIn(minSamples, maxSamples)
        val stepSize = totalLength / sampleCount

        // Check start point
        pathMeasure.getPosTan(0f, coords, null)
        if (isPointInEraserRange(coords[0], coords[1], centerX, centerY, radius, pathObject.paint.strokeWidth)) {
            return true
        }

        // Check sampled points along the path
        for (i in 1..sampleCount) {
            val distance = (stepSize * i).coerceAtMost(totalLength)
            pathMeasure.getPosTan(distance, coords, null)
            if (isPointInEraserRange(coords[0], coords[1], centerX, centerY, radius, pathObject.paint.strokeWidth)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a single point is within the circular area of the eraser,
     * taking the object's stroke width into account.
     */
    private fun isPointInEraserRange(
        pointX: Float,
        pointY: Float,
        eraserX: Float,
        eraserY: Float,
        eraserRadius: Float,
        strokeWidth: Float
    ): Boolean {
        val distance = hypot(pointX - eraserX, pointY - eraserY)
        // The effective radius includes half the stroke width.
        return distance <= eraserRadius + (strokeWidth / 2f)
    }
}