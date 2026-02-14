package com.example.whiteboardapp.manager

import android.graphics.RectF
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.HandleType
import com.example.whiteboardapp.model.StrokePoint
import com.example.whiteboardapp.model.StylusStrokeObject
import com.example.whiteboardapp.model.TextObject
import kotlin.math.atan2
import kotlin.math.min

/**
 * Manages the state of an active object transformation (resize or rotate).
 *
 * [StylusStrokeObject] does not support resize (the point list can't easily be
 * re-sampled to a new bounding box), so those events are silently ignored.
 * Rotation is supported for all object types via the [DrawingObject.rotation] field.
 */
class TransformationManager {
    private var activeObject: DrawingObject? = null
    private var activeHandle: HandleType? = null

    private var initialX = 0f
    private var initialY = 0f
    private var initialRotation = 0f
    private var initialBounds = RectF()

    fun startTransform(obj: DrawingObject, handle: HandleType, x: Float, y: Float) {
        activeObject = obj
        activeHandle = handle
        initialX = x
        initialY = y
        initialRotation = obj.rotation
        initialBounds.set(obj.bounds)
    }

    fun updateTransform(obj: DrawingObject, x: Float, y: Float): Boolean {
        if (activeObject?.id != obj.id || activeHandle == null) return false

        when (activeHandle) {
            HandleType.ROTATE -> handleRotation(obj, x, y)
            else -> handleResize(obj, x, y)
        }
        return true
    }

    fun endTransform() {
        activeObject = null
        activeHandle = null
    }

    fun isTransforming() = activeObject != null

    // ── Rotation (all object types) ───────────────────────────────────────────

    private fun handleRotation(obj: DrawingObject, x: Float, y: Float) {
        val centerX = initialBounds.centerX()
        val centerY = initialBounds.centerY()
        val startAngle = atan2(initialY - centerY, initialX - centerX)
        val currentAngle = atan2(y - centerY, x - centerX)
        val angleChange = Math.toDegrees((currentAngle - startAngle).toDouble()).toFloat()
        obj.rotation = (initialRotation + angleChange + 360) % 360
    }

    // ── Resize ────────────────────────────────────────────────────────────────

    private fun handleResize(obj: DrawingObject, x: Float, y: Float) {
        // StylusStrokeObject: resizing is not supported (would require re-sampling the
        // pressure curve, which is non-trivial and distorts intentional pressure data).
        // Rotation is still supported via the ROTATE handle above.
        if (obj is StylusStrokeObject) return

        val dx = x - initialX
        val dy = y - initialY
        val newBounds = RectF(initialBounds)

        when (activeHandle) {
            HandleType.TOP_LEFT     -> { newBounds.left += dx; newBounds.top += dy }
            HandleType.TOP_RIGHT    -> { newBounds.right += dx; newBounds.top += dy }
            HandleType.BOTTOM_LEFT  -> { newBounds.left += dx; newBounds.bottom += dy }
            HandleType.BOTTOM_RIGHT -> { newBounds.right += dx; newBounds.bottom += dy }
            HandleType.TOP          -> newBounds.top += dy
            HandleType.BOTTOM       -> newBounds.bottom += dy
            HandleType.LEFT         -> newBounds.left += dx
            HandleType.RIGHT        -> newBounds.right += dx
            else -> return
        }

        if (newBounds.width() < 20f) newBounds.right = newBounds.left + 20f
        if (newBounds.height() < 20f) newBounds.bottom = newBounds.top + 20f

        when (obj) {
            is DrawingObject.ShapeObject -> {
                obj.startX = newBounds.left; obj.startY = newBounds.top
                obj.endX = newBounds.right;  obj.endY = newBounds.bottom
            }
            is TextObject -> {
                val scaleX = newBounds.width() / initialBounds.width()
                val scaleY = newBounds.height() / initialBounds.height()
                obj.textSize = (obj.textSize * min(scaleX, scaleY)).coerceIn(12f, 500f)
                obj.x = newBounds.left; obj.y = newBounds.top
            }
            is DrawingObject.PathObject -> {
                // Path resizing is complex — left as future work
            }
            is StylusStrokeObject -> { /* handled above */ }
        }
    }
}