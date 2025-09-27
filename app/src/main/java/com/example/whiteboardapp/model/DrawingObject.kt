package com.example.whiteboardapp.model

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

sealed class DrawingObject {
    abstract val id: String
    abstract val bounds: RectF

    abstract fun draw(canvas: Canvas)
    abstract fun contains(x: Float, y: Float): Boolean
    abstract fun move(dx: Float, dy: Float)

    data class PathObject(
        override val id: String = UUID.randomUUID().toString(),
        val path: Path,
        val paint: Paint
    ) : DrawingObject() {
        override val bounds: RectF = RectF().apply {
            path.computeBounds(this, true)
            // Expand bounds slightly for easier selection
            val expansion = paint.strokeWidth / 2f
            inset(-expansion, -expansion)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun contains(x: Float, y: Float): Boolean {
            return bounds.contains(x, y)
        }

        override fun move(dx: Float, dy: Float) {
            path.offset(dx, dy)
            bounds.offset(dx, dy)
        }
    }

    data class ShapeObject(
        override val id: String = UUID.randomUUID().toString(),
        val shapeType: ShapeType,
        var startX: Float,
        var startY: Float,
        var endX: Float,
        var endY: Float,
        val paint: Paint,
        val fillPaint: Paint? = null
    ) : DrawingObject() {

        override val bounds: RectF
            get() = RectF(
                minOf(startX, endX),
                minOf(startY, endY),
                maxOf(startX, endX),
                maxOf(startY, endY)
            )

        override fun draw(canvas: Canvas) {
            when (shapeType) {
                ShapeType.LINE -> {
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
                ShapeType.RECTANGLE -> {
                    // Draw fill first (if enabled), then stroke
                    fillPaint?.let { canvas.drawRect(bounds, it) }
                    canvas.drawRect(bounds, paint)
                }
                ShapeType.CIRCLE -> {
                    val centerX = (startX + endX) / 2f
                    val centerY = (startY + endY) / 2f
                    val radius = hypot(endX - startX, endY - startY) / 2f
                    // Draw fill first (if enabled), then stroke
                    fillPaint?.let { canvas.drawCircle(centerX, centerY, radius, it) }
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
                ShapeType.POLYGON -> {
                    drawPolygon(canvas)
                }
            }
        }

        private fun drawPolygon(canvas: Canvas) {
            val path = Path().apply {
                val centerX = (startX + endX) / 2f
                val centerY = (startY + endY) / 2f
                val radius = minOf(abs(endX - startX), abs(endY - startY)) / 2f
                val sides = 6 // Hexagon

                for (i in 0..sides) {
                    val angle = 2 * Math.PI * i / sides
                    val x = centerX + radius * cos(angle).toFloat()
                    val y = centerY + radius * sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            // Draw fill first (if enabled), then stroke
            fillPaint?.let { canvas.drawPath(path, it) }
            canvas.drawPath(path, paint)
        }

        override fun contains(x: Float, y: Float): Boolean = bounds.contains(x, y)

        override fun move(dx: Float, dy: Float) {
            startX += dx
            startY += dy
            endX += dx
            endY += dy
        }
    }
}