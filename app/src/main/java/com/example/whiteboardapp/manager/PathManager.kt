package com.example.whiteboardapp.manager

import android.graphics.PathMeasure
import android.graphics.RectF
import com.example.whiteboardapp.model.DrawingPath
import kotlin.math.hypot

class PathManager {
    private val paths = mutableListOf<DrawingPath>()
    private val pathBounds = mutableMapOf<String, RectF>()

    fun addPath(drawingPath: DrawingPath) {
        paths.add(drawingPath)
        calculateBounds(drawingPath)
    }

    fun undo(): DrawingPath? {
        if (paths.isNotEmpty()) {
            val lastPath = paths.removeAt(paths.lastIndex)
            pathBounds.remove(lastPath.id)
            return lastPath
        }
        return null
    }

    private fun calculateBounds(drawingPath: DrawingPath) {
        val bounds = RectF()
        drawingPath.path.computeBounds(bounds, true)
        // Expand bounds by stroke width for accurate intersection
        val expansion = drawingPath.strokeWidth / 2f
        bounds.inset(-expansion, -expansion)
        pathBounds[drawingPath.id] = bounds
    }

    fun eraseAt(x: Float, y: Float, radius: Float): List<DrawingPath> {
        val eraserBounds = RectF(x - radius, y - radius, x + radius, y + radius)
        val affectedPaths = mutableListOf<DrawingPath>()

        // Use an iterator to safely remove elements while iterating
        val iterator = paths.iterator()
        while (iterator.hasNext()) {
            val path = iterator.next()
            val bounds = pathBounds[path.id]

            if (bounds != null && RectF.intersects(eraserBounds, bounds)) {
                // Perform precise intersection check
                if (isPathInEraserRange(path, x, y, radius)) {
                    affectedPaths.add(path)
                    pathBounds.remove(path.id)
                    iterator.remove() // Safely remove the path
                }
            }
        }
        return affectedPaths
    }

    private fun isPathInEraserRange(
        drawingPath: DrawingPath,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Boolean {
        val pathMeasure = PathMeasure(drawingPath.path, false)

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
        if (isPointInEraserRange(coords[0], coords[1], centerX, centerY, radius, drawingPath.strokeWidth)) {
            return true
        }

        // Check sampled points along the path
        for (i in 1..sampleCount) {
            val distance = (stepSize * i).coerceAtMost(totalLength)
            pathMeasure.getPosTan(distance, coords, null)

            if (isPointInEraserRange(coords[0], coords[1], centerX, centerY, radius, drawingPath.strokeWidth)) {
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

    fun getAllPaths(): List<DrawingPath> = paths.toList()

    fun clear() {
        paths.clear()
        pathBounds.clear()
    }

    fun getPathCount(): Int = paths.size
}