package com.example.whiteboardapp.manager

import android.graphics.*
import com.example.whiteboardapp.model.DrawingObject
import kotlin.math.abs

/**
 * A helper class that provides "smart guides" for aligning drawing objects.
 * It detects when a moving object's edges or center are aligned with other objects
 * on the canvas and provides visual feedback (alignment lines).
 */
class AlignmentHelper {

    // Paint used for drawing the dashed red alignment guide lines.
    private val guidePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        alpha = 180
    }

    /**
     * Represents a single alignment guide (either horizontal or vertical).
     *
     * @property position The coordinate of the guide line (y for horizontal, x for vertical).
     * @property orientation Whether the guide is HORIZONTAL or VERTICAL.
     * @property sourceObject The object that this guide is snapping to.
     */
    data class AlignmentGuide(
        val position: Float,
        val orientation: Orientation,
        val sourceObject: DrawingObject? = null
    ) {
        enum class Orientation { HORIZONTAL, VERTICAL }
    }

    /**
     * Finds potential alignment guides for a moving object relative to a list of static objects.
     * It checks for alignment of top, center, and bottom edges (horizontal) and
     * left, center, and right edges (vertical).
     *
     * @param movingObject The object currently being dragged by the user.
     * @param allObjects The list of all other objects on the canvas to check against.
     * @param threshold The maximum distance in pixels to consider two edges "aligned".
     * @return A list of [AlignmentGuide]s that the moving object can snap to.
     */
    fun findAlignmentGuides(
        movingObject: DrawingObject,
        allObjects: List<DrawingObject>,
        threshold: Float = 10f
    ): List<AlignmentGuide> {
        val guides = mutableListOf<AlignmentGuide>()
        val movingBounds = movingObject.bounds

        allObjects.filter { it.id != movingObject.id }.forEach { obj ->
            val objBounds = obj.bounds

            // Check horizontal alignment (top, middle, bottom)
            listOf(
                movingBounds.top to objBounds.top,
                movingBounds.centerY() to objBounds.centerY(),
                movingBounds.bottom to objBounds.bottom
            ).forEach { (movingPos, targetPos) ->
                if (abs(movingPos - targetPos) < threshold) {
                    guides.add(
                        AlignmentGuide(
                            targetPos,
                            AlignmentGuide.Orientation.HORIZONTAL,
                            obj
                        )
                    )
                }
            }

            // Check vertical alignment (left, center, right)
            listOf(
                movingBounds.left to objBounds.left,
                movingBounds.centerX() to objBounds.centerX(),
                movingBounds.right to objBounds.right
            ).forEach { (movingPos, targetPos) ->
                if (abs(movingPos - targetPos) < threshold) {
                    guides.add(
                        AlignmentGuide(
                            targetPos,
                            AlignmentGuide.Orientation.VERTICAL,
                            obj
                        )
                    )
                }
            }
        }
        return guides
    }

    /**
     * Adjusts a point's coordinates to snap to the nearest guide if it's within a given distance.
     *
     * @param position The current position of the object being moved.
     * @param guides The list of active alignment guides.
     * @param snapDistance The maximum distance to a guide for snapping to occur.
     * @return A new [PointF] with the snapped coordinates.
     */
    fun snapToGuides(
        position: PointF,
        guides: List<AlignmentGuide>,
        snapDistance: Float = 10f
    ): PointF {
        var snappedX = position.x
        var snappedY = position.y

        guides.forEach { guide ->
            when (guide.orientation) {
                AlignmentGuide.Orientation.HORIZONTAL -> {
                    if (abs(position.y - guide.position) < snapDistance) {
                        snappedY = guide.position
                    }
                }
                AlignmentGuide.Orientation.VERTICAL -> {
                    if (abs(position.x - guide.position) < snapDistance) {
                        snappedX = guide.position
                    }
                }
            }
        }
        return PointF(snappedX, snappedY)
    }

    /**
     * Draws the visual representation of the alignment guides on the canvas.
     *
     * @param canvas The canvas to draw on.
     * @param guides The list of guides to draw.
     * @param viewBounds The visible area of the canvas, to draw lines from edge to edge.
     */
    fun drawGuides(canvas: Canvas, guides: List<AlignmentGuide>, viewBounds: RectF) {
        guides.forEach { guide ->
            when (guide.orientation) {
                AlignmentGuide.Orientation.HORIZONTAL -> {
                    canvas.drawLine(
                        viewBounds.left,
                        guide.position,
                        viewBounds.right,
                        guide.position,
                        guidePaint
                    )
                }
                AlignmentGuide.Orientation.VERTICAL -> {
                    canvas.drawLine(
                        guide.position,
                        viewBounds.top,
                        guide.position,
                        viewBounds.bottom,
                        guidePaint
                    )
                }
            }
        }
    }
}