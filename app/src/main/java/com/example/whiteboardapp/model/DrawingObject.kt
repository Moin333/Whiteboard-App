package com.example.whiteboardapp.model

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.withRotation
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A sealed class representing any object that can be drawn on the whiteboard.
 * This defines the common properties and behaviors for all drawable items.
 */
sealed class DrawingObject {
    // A unique identifier for the object.
    abstract val id: String
    // The bounding box of the object.
    abstract val bounds: RectF
    // The rotation angle of the object in degrees.
    open var rotation: Float = 0f

    // Renders the object on the given canvas.
    abstract fun draw(canvas: Canvas)
    // Checks if a given coordinate (x, y) is inside the object's bounds.
    abstract fun contains(x: Float, y: Float): Boolean
    // Moves the object by a given delta (dx, dy).
    abstract fun move(dx: Float, dy: Float)
    // Creates a deep copy of the object.
    abstract fun clone(): DrawingObject

    /**
     * Helper function to calculate the new coordinates of a point rotated around a center.
     * @param x The original x-coordinate of the point.
     * @param y The original y-coordinate of the point.
     * @param cx The center x-coordinate of rotation.
     * @param cy The center y-coordinate of rotation.
     * @param angle The rotation angle in degrees.
     * @return A [PointF] with the new coordinates.
     */
    protected fun rotatePoint(x: Float, y: Float, cx: Float, cy: Float, angle: Float): PointF {
        val rad = Math.toRadians(angle.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        val dx = x - cx
        val dy = y - cy
        return PointF(
            cx + dx * cos - dy * sin,
            cy + dx * sin + dy * cos
        )
    }

    // Represents a freehand drawing path.
    data class PathObject(
        override val id: String = UUID.randomUUID().toString(),
        val path: Path,
        val paint: Paint
    ) : DrawingObject() {
        // Bounds are computed once and updated on move.
        override val bounds: RectF = RectF().apply {
            path.computeBounds(this, true)
            // Expand bounds slightly for easier touch selection.
            val expansion = paint.strokeWidth / 2f
            inset(-expansion, -expansion)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun contains(x: Float, y: Float): Boolean {
            // For paths, a simple bounds check is sufficient for selection.
            return bounds.contains(x, y)
        }

        override fun move(dx: Float, dy: Float) {
            path.offset(dx, dy)
            bounds.offset(dx, dy)
        }

        override fun clone(): DrawingObject {
            return this.copy(
                path = Path(this.path),
                paint = Paint(this.paint)
            )
        }
    }

    // Represents a geometric shape like a line, rectangle, or circle.
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
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            // Apply rotation around the shape's center.
            canvas.withRotation(rotation, centerX, centerY) {
                when (shapeType) {
                    ShapeType.LINE -> {
                        drawLine(startX, startY, endX, endY, paint)
                    }
                    ShapeType.RECTANGLE -> {
                        fillPaint?.let { drawRect(bounds, it) }
                        drawRect(bounds, paint)
                    }
                    ShapeType.CIRCLE -> {
                        val cX = (startX + endX) / 2f
                        val cY = (startY + endY) / 2f
                        val radius = hypot(endX - startX, endY - startY) / 2f
                        fillPaint?.let { drawCircle(cX, cY, radius, it) }
                        drawCircle(cX, cY, radius, paint)
                    }
                    ShapeType.POLYGON -> {
                        drawPolygon(this)
                    }
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
            fillPaint?.let { canvas.drawPath(path, it) }
            canvas.drawPath(path, paint)
        }

        override fun clone(): DrawingObject {
            return this.copy(
                paint = Paint(this.paint),
                fillPaint = this.fillPaint?.let { Paint(it) }
            )
        }

        override fun contains(x: Float, y: Float): Boolean {
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            // To check containment in a rotated object, we un-rotate the touch point
            // and check if it falls within the original, un-rotated bounds.
            if (rotation != 0f) {
                val rotatedPoint = rotatePoint(x, y, centerX, centerY, -rotation)
                return bounds.contains(rotatedPoint.x, rotatedPoint.y)
            }
            return bounds.contains(x, y)
        }

        override fun move(dx: Float, dy: Float) {
            startX += dx
            startY += dy
            endX += dx
            endY += dy
        }
    }
}