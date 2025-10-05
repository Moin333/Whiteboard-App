package com.example.whiteboardapp.manager

import android.graphics.PointF
import android.graphics.RectF
import com.example.whiteboardapp.model.HandleType
import com.example.whiteboardapp.model.TransformHandle

// Manages the creation and state of all transformation handles for a selected object.
class TransformHandleManager {
    private val handles = mutableListOf<TransformHandle>()
    private val handleRadius = 20f
    private val rotationHandleOffset = 60f // How far above the object the rotate handle appears.

    /**
     * Recalculates the positions of all handles based on the object's current bounds and rotation.
     * This must be called whenever the selected object is moved, resized, or rotated.
     * @param bounds The current bounding box of the selected object.
     * @param rotation The current rotation of the selected object.
     */
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

    /**
     * Finds which handle, if any, is at a given coordinate.
     * @return The [TransformHandle] at the location, or null if none.
     */
    fun getHandleAt(x: Float, y: Float): TransformHandle? {
        return handles.firstOrNull { it.contains(x, y) }
    }

    // Returns a read-only list of all current handles.
    fun getAllHandles(): List<TransformHandle> = handles.toList()
}