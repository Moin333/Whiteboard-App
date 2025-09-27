package com.example.whiteboardapp.manager

import android.graphics.PathMeasure
import android.graphics.RectF
import com.example.whiteboardapp.model.DrawingObject
import kotlin.math.hypot

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

        // First, do a quick bounds check
        if (!RectF.intersects(eraserBounds, obj.bounds)) {
            return false
        }

        // Now perform object-specific intersection logic
        return when (obj) {
            is DrawingObject.PathObject -> {
                isPathInEraserRange(obj, eraserX, eraserY, eraserRadius)
            }
            is DrawingObject.ShapeObject -> {
                // For shapes, the bounds check is sufficient
                // since shapes are solid geometric primitives
                true
            }
        }
    }

    private fun isPathInEraserRange(
        pathObject: DrawingObject.PathObject,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Boolean {
        val pathMeasure = PathMeasure(pathObject.path, false)

        // Handle empty paths
        if (pathMeasure.length <= 0) return false

        val coords = FloatArray(2)
        val totalLength = pathMeasure.length

        // Use adaptive sampling - more samples for longer paths
        val minSamples = 10
        val maxSamples = 50
        val samplesPerUnit = 0.5f // samples per pixel
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

    private fun isPointInEraserRange(
        pointX: Float,
        pointY: Float,
        eraserX: Float,
        eraserY: Float,
        eraserRadius: Float,
        strokeWidth: Float
    ): Boolean {
        val distance = hypot(pointX - eraserX, pointY - eraserY)
        return distance <= eraserRadius + (strokeWidth / 2f)
    }
}