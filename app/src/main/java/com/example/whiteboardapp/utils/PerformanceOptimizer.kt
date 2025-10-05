package com.example.whiteboardapp.utils

import android.graphics.RectF
import com.example.whiteboardapp.model.DrawingObject

/**
 * A utility object containing methods to improve rendering performance.
 */
object PerformanceOptimizer {
    /**
     * Filters a list of all objects to return only those that are currently visible within the viewport.
     * This prevents the app from spending time drawing objects that are off-screen.
     *
     * @param allObjects The complete list of objects on the canvas.
     * @param viewportBounds The rectangle defining the currently visible area of the canvas.
     * @param margin An extra margin to include objects that are just outside the viewport,
     * ensuring they appear immediately when the user pans slightly.
     * @return A filtered list of visible objects.
     */
    fun getVisibleObjects(
        allObjects: List<DrawingObject>,
        viewportBounds: RectF,
        margin: Float = 50f
    ): List<DrawingObject> {
        val expandedBounds = RectF(viewportBounds).apply {
            inset(-margin, -margin)
        }

        return allObjects.filter { obj ->
            RectF.intersects(obj.bounds, expandedBounds)
        }
    }
}