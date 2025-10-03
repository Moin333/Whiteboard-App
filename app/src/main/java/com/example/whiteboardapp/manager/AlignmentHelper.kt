package com.example.whiteboardapp.manager

import android.graphics.*
import com.example.whiteboardapp.model.DrawingObject
import kotlin.math.abs

class AlignmentHelper {

    private val guidePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        alpha = 180
    }

    data class AlignmentGuide(
        val position: Float,
        val orientation: Orientation,
        val sourceObject: DrawingObject? = null
    ) {
        enum class Orientation { HORIZONTAL, VERTICAL }
    }

    fun findAlignmentGuides(
        movingObject: DrawingObject,
        allObjects: List<DrawingObject>,
        threshold: Float = 10f
    ): List<AlignmentGuide> {
        val guides = mutableListOf<AlignmentGuide>()
        val movingBounds = movingObject.bounds

        allObjects.filter { it.id != movingObject.id }.forEach { obj ->
            val objBounds = obj.bounds

            // Check horizontal alignment
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

            // Check vertical alignment
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