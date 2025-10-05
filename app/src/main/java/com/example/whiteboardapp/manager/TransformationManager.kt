package com.example.whiteboardapp.manager

import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.HandleType
import com.example.whiteboardapp.model.TextObject
import kotlin.math.atan2
import kotlin.math.min

/**
 * Manages the state of an active object transformation (resize or rotate).
 * It saves the object's initial state when a transformation begins and calculates
 * the new state based on the user's drag gesture.
 */
class TransformationManager {
    private var activeObject: DrawingObject? = null
    private var activeHandle: HandleType? = null

    // The state of the object and touch position when the transformation started.
    private var initialX = 0f
    private var initialY = 0f
    private var initialRotation = 0f
    private var initialBounds = android.graphics.RectF()

    /**
     * Begins a transformation on an object.
     * This should be called on ACTION_DOWN when a user grabs a transform handle.
     *
     * @param obj The object to be transformed.
     * @param handle The [HandleType] that was grabbed.
     * @param x The initial touch x-coordinate.
     * @param y The initial touch y-coordinate.
     */
    fun startTransform(obj: DrawingObject, handle: HandleType, x: Float, y: Float) {
        activeObject = obj
        activeHandle = handle
        initialX = x
        initialY = y
        initialRotation = obj.rotation
        initialBounds.set(obj.bounds)
    }

    /**
     * Updates the object's properties based on the current touch position.
     * This should be called on ACTION_MOVE.
     *
     * @return True if a transformation was active and updated, otherwise false.
     */
    fun updateTransform(obj: DrawingObject, x: Float, y: Float): Boolean {
        if (activeObject?.id != obj.id || activeHandle == null) return false

        when (activeHandle) {
            HandleType.ROTATE -> handleRotation(obj, x, y)
            else -> handleResize(obj, x, y)
        }
        return true
    }

    // Ends the current transformation. Should be called on ACTION_UP.
    fun endTransform() {
        activeObject = null
        activeHandle = null
    }

    // Calculates and applies the new rotation to the object.
    private fun handleRotation(obj: DrawingObject, x: Float, y: Float) {
        val centerX = initialBounds.centerX()
        val centerY = initialBounds.centerY()

        // Calculate angle from the center to the initial and current touch points
        val startAngle = atan2(initialY - centerY, initialX - centerX)
        val currentAngle = atan2(y - centerY, x - centerX)

        // Calculate the change in angle and convert to degrees
        val angleChange = Math.toDegrees((currentAngle - startAngle).toDouble()).toFloat()
        obj.rotation = (initialRotation + angleChange + 360) % 360
    }

    // Calculates and applies the new size/bounds to the object.
    private fun handleResize(obj: DrawingObject, x: Float, y: Float) {
        val dx = x - initialX
        val dy = y - initialY
        val newBounds = android.graphics.RectF(initialBounds)

        // Apply changes based on which handle is being dragged
        when (activeHandle) {
            HandleType.TOP_LEFT -> {
                newBounds.left += dx
                newBounds.top += dy
            }
            HandleType.TOP_RIGHT -> {
                newBounds.right += dx
                newBounds.top += dy
            }
            HandleType.BOTTOM_LEFT -> {
                newBounds.left += dx
                newBounds.bottom += dy
            }
            HandleType.BOTTOM_RIGHT -> {
                newBounds.right += dx
                newBounds.bottom += dy
            }
            HandleType.TOP -> newBounds.top += dy
            HandleType.BOTTOM -> newBounds.bottom += dy
            HandleType.LEFT -> newBounds.left += dx
            HandleType.RIGHT -> newBounds.right += dx
            else -> return // Should not happen
        }

        // Enforce minimum size to prevent inverting the object
        if (newBounds.width() < 20f) newBounds.right = newBounds.left + 20f
        if (newBounds.height() < 20f) newBounds.bottom = newBounds.top + 20f


        // Apply the new bounds to the specific object type
        when (obj) {
            is DrawingObject.ShapeObject -> {
                obj.startX = newBounds.left
                obj.startY = newBounds.top
                obj.endX = newBounds.right
                obj.endY = newBounds.bottom
            }
            is TextObject -> {
                // For text, we can scale the text size instead of the bounds
                val scaleX = newBounds.width() / initialBounds.width()
                val scaleY = newBounds.height() / initialBounds.height()
                // Use the minimum of the two scales to maintain aspect ratio
                val scale = min(scaleX, scaleY)
                obj.textSize = (obj.textSize * scale).coerceIn(12f, 500f)
                // Reposition the object based on the resized bounds
                obj.x = newBounds.left
                obj.y = newBounds.top
            }
            is DrawingObject.PathObject -> {
                // Path resizing is complex and requires matrix transformation.
               // TODO: Implement path scaling with transformation matrix
            }
        }
    }

    // Checks if a transformation is currently in progress.
    fun isTransforming(): Boolean {
        return activeObject != null
    }
}