package com.example.whiteboardapp.data

import android.graphics.Path
import android.graphics.PathMeasure

object PathSerializer {
    // Converts a Path object to a string of coordinates
    fun pathToString(path: Path): String {
        val pathData = StringBuilder()
        val pm = PathMeasure(path, false)
        val coords = floatArrayOf(0f, 0f)

        var distance = 0f
        val step = 2f // Sample every 2 pixels for a good balance of accuracy and size
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

    // Converts a string of coordinates back into a Path object
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