package com.example.whiteboardapp.model

import android.graphics.PointF
import android.graphics.RectF

enum class HandleType {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT,
    ROTATE
}

data class TransformHandle(
    val type: HandleType,
    val position: PointF,
    val radius: Float = 20f
) {
    fun contains(x: Float, y: Float): Boolean {
        val dx = x - position.x
        val dy = y - position.y
        return (dx * dx + dy * dy) <= (radius * radius)
    }
}

 // Manages transformation handles for a selected object
class TransformHandleManager {
    private val handles = mutableListOf<TransformHandle>()
    private val handleRadius = 20f
    private val rotationHandleOffset = 60f

    fun updateHandles(bounds: RectF, rotation: Float = 0f) {
        handles.clear()

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // Corner handles
        handles.add(TransformHandle(HandleType.TOP_LEFT, PointF(bounds.left, bounds.top), handleRadius))
        handles.add(TransformHandle(HandleType.TOP_RIGHT, PointF(bounds.right, bounds.top), handleRadius))
        handles.add(TransformHandle(HandleType.BOTTOM_LEFT, PointF(bounds.left, bounds.bottom), handleRadius))
        handles.add(TransformHandle(HandleType.BOTTOM_RIGHT, PointF(bounds.right, bounds.bottom), handleRadius))

        // Edge handles (midpoints)
        handles.add(TransformHandle(HandleType.TOP, PointF(centerX, bounds.top), handleRadius))
        handles.add(TransformHandle(HandleType.BOTTOM, PointF(centerX, bounds.bottom), handleRadius))
        handles.add(TransformHandle(HandleType.LEFT, PointF(bounds.left, centerY), handleRadius))
        handles.add(TransformHandle(HandleType.RIGHT, PointF(bounds.right, centerY), handleRadius))

        // Rotation handle (above the top edge)
        handles.add(TransformHandle(
            HandleType.ROTATE,
            PointF(centerX, bounds.top - rotationHandleOffset),
            handleRadius
        ))
    }

    fun getHandleAt(x: Float, y: Float): TransformHandle? {
        return handles.firstOrNull { it.contains(x, y) }
    }

    fun getAllHandles(): List<TransformHandle> = handles.toList()
}