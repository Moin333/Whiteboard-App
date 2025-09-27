package com.example.whiteboardapp.manager

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.ShapeType

class ShapeDrawingHandler(private val view: View) {

    private var currentShape: DrawingObject.ShapeObject? = null

    fun handleShapeDrawing(
        event: MotionEvent,
        shapeType: ShapeType,
        strokePaint: Paint,
        fillPaint: Paint? = null
    ): DrawingObject.ShapeObject? {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentShape = DrawingObject.ShapeObject(
                    shapeType = shapeType,
                    startX = event.x,
                    startY = event.y,
                    endX = event.x,
                    endY = event.y,
                    paint = Paint(strokePaint),
                    fillPaint = fillPaint?.let { Paint(it) }
                )
            }

            MotionEvent.ACTION_MOVE -> {
                currentShape?.apply {
                    endX = event.x
                    endY = event.y
                    view.invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                val shape = currentShape
                currentShape = null
                view.invalidate()
                return shape
            }
        }
        return null
    }

    fun drawPreview(canvas: Canvas) {
        currentShape?.let { shape ->
            // Create preview paints with dashed style and transparency
            val previewStrokePaint = Paint(shape.paint).apply {
                pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
                alpha = 128
            }

            val previewFillPaint = shape.fillPaint?.let { fillPaint ->
                Paint(fillPaint).apply {
                    alpha = 64 // More transparent for fill preview
                }
            }

            // Temporarily update the shape's paints for preview
            val originalStrokePaint = shape.paint
            val originalFillPaint = shape.fillPaint

            // Replace with preview paints
            val tempShape = shape.copy(
                paint = previewStrokePaint,
                fillPaint = previewFillPaint
            )

            tempShape.draw(canvas)
        }
    }
}