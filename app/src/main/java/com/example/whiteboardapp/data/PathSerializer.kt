package com.example.whiteboardapp.data

import android.graphics.Path
import android.graphics.PathMeasure

/**
 * Provides utility methods to serialize an Android [Path] object into a string
 * and deserialize it back. This is necessary because Path objects cannot be stored
 * directly in a database.
 */
object PathSerializer {
    /**
     * Converts a Path object to a string by sampling points along its length.
     * This method approximates the path as a series of connected line segments.
     *
     * @param path The [Path] to serialize.
     * @return A string representation of the path, e.g., "x1,y1;x2,y2;...".
     */
    fun pathToString(path: Path): String {
        val pathData = StringBuilder()
        val pm = PathMeasure(path, false)
        val coords = floatArrayOf(0f, 0f)

        var distance = 0f
        // Sample points at small intervals for a balance of accuracy and data size.
        val step = 2f
        var isFirstPoint = true

        while (distance < pm.length) {
            pm.getPosTan(distance, coords, null)
            if (!isFirstPoint) pathData.append(";")
            pathData.append("${coords[0]},${coords[1]}")
            isFirstPoint = false
            distance += step
        }
        return pathData.toString()
    }

    /**
     * Converts a string of coordinates back into a [Path] object.
     * It reconstructs the path by using `moveTo` for the first point and `lineTo` for all subsequent points.
     *
     * @param pathData The string representation of the path.
     * @return A reconstructed [Path] object.
     */
    fun stringToPath(pathData: String): Path {
        val path = Path()
        val points = pathData.split(";").filter { it.isNotEmpty() }
        points.forEachIndexed { index, pointStr ->
            val coords = pointStr.split(",").mapNotNull { it.toFloatOrNull() }
            if (coords.size == 2) {
                if (index == 0) {
                    path.moveTo(coords[0], coords[1])
                } else {
                    path.lineTo(coords[0], coords[1])
                }
            }
        }
        return path
    }
}