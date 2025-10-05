package com.example.whiteboardapp.manager

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.ShapeType

/**
 * A stateful handler that manages the interactive drawing of shapes.
 * It tracks the shape from the initial touch-down event, through the drag (move) event,
 * until the final touch-up event.
 *
 * @param view The [View] that this handler will invalidate to trigger redraws.
 */
class ShapeDrawingHandler(private val view: View) {

    // The shape currently being drawn. It's null when no drawing action is in progress.
    private var currentShape: DrawingObject.ShapeObject? = null

    /**
     * Processes touch events to draw a shape.
     *
     * @param event The [MotionEvent] from the view.
     * @param shapeType The [ShapeType] to be drawn.
     * @param strokePaint The paint for the shape's outline.
     * @param fillPaint The paint for the shape's fill, if any.
     * @return The completed [DrawingObject.ShapeObject] when the gesture is finished (on ACTION_UP), otherwise null.
     */
    fun handleShapeDrawing(
        event: MotionEvent,
        shapeType: ShapeType,
        strokePaint: Paint,
        fillPaint: Paint? = null
    ): DrawingObject.ShapeObject? {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Create a new shape at the starting point.
                currentShape = DrawingObject.ShapeObject(
                    shapeType = shapeType,
                    startX = event.x,
                    startY = event.y,
                    endX = event.x, // Initially, start and end are the same.
                    endY = event.y,
                    paint = Paint(strokePaint),
                    fillPaint = fillPaint?.let { Paint(it) }
                )
            }

            MotionEvent.ACTION_MOVE -> {
                // Update the end coordinates as the user drags their finger.
                currentShape?.apply {
                    endX = event.x
                    endY = event.y
                    view.invalidate() // Trigger a redraw to show the preview.
                }
            }

            MotionEvent.ACTION_UP -> {
                // The shape is complete. Return it and reset the state.
                val shape = currentShape
                currentShape = null
                view.invalidate()
                return shape
            }
        }
        return null
    }

    /**
     * Draws a temporary, dashed preview of the shape while it's being drawn.
     * @param canvas The canvas to draw the preview on.
     */
    fun drawPreview(canvas: Canvas) {
        currentShape?.let { shape ->
            val previewStrokePaint = Paint(shape.paint).apply {
                pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
                alpha = 128
            }

            val previewFillPaint = shape.fillPaint?.let { fillPaint ->
                Paint(fillPaint).apply {
                    alpha = 64 // More transparent for fill preview
                }
            }

            // Create a temporary copy with the preview paints to draw.
            val tempShape = shape.copy(
                paint = previewStrokePaint,
                fillPaint = previewFillPaint
            )
            tempShape.draw(canvas)
        }
    }
}