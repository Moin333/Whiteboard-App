package com.example.whiteboardapp.model

import android.graphics.PointF

// An enum representing the different types of transformation handles on a selection box.
enum class HandleType {
    // Corner handles for resizing
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    // Edge handles for resizing
    TOP, BOTTOM, LEFT, RIGHT,
    // Handle for rotation
    ROTATE
}

/**
 * Represents a single transformation handle (the circle/square you can drag).
 * @property type The type of handle (e.g., TOP_LEFT, ROTATE).
 * @property position The center coordinates of the handle.
 * @property radius The touchable radius of the handle.
 */
data class TransformHandle(
    val type: HandleType,
    val position: PointF,
    val radius: Float = 20f
) {
    // Checks if a given point (x, y) is inside this handle's touch area.
    fun contains(x: Float, y: Float): Boolean {
        val dx = x - position.x
        val dy = y - position.y
        return (dx * dx + dy * dy) <= (radius * radius)
    }
}