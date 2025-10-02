package com.example.whiteboardapp.manager

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class CanvasTransformManager {

    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()

    var currentScale = 1f
        private set
    var currentTranslateX = 0f
        private set
    var currentTranslateY = 0f
        private set

    private val minScale = 0.25f
    private val maxScale = 5f

    private val _transformChanged = MutableLiveData<Matrix>()
    val transformChanged: LiveData<Matrix> = _transformChanged

    fun setScale(scale: Float, focusX: Float, focusY: Float) {
        val newScale = scale.coerceIn(minScale, maxScale)
        val scaleFactor = newScale / currentScale

        transformMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
        currentScale = newScale

        updateInverseMatrix()
        _transformChanged.value = Matrix(transformMatrix)
    }

    fun translate(dx: Float, dy: Float) {
        transformMatrix.postTranslate(dx, dy)
        currentTranslateX += dx
        currentTranslateY += dy

        updateInverseMatrix()
        _transformChanged.value = Matrix(transformMatrix)
    }

    fun getTransformedPoint(x: Float, y: Float): PointF {
        val points = floatArrayOf(x, y)
        inverseMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    fun getTransformedRect(rect: RectF): RectF {
        val transformedRect = RectF(rect)
        transformMatrix.mapRect(transformedRect)
        return transformedRect
    }

    fun resetTransform() {
        transformMatrix.reset()
        inverseMatrix.reset()
        currentScale = 1f
        currentTranslateX = 0f
        currentTranslateY = 0f
        _transformChanged.value = Matrix(transformMatrix)
    }

    fun centerCanvas(viewWidth: Float, viewHeight: Float, canvasWidth: Float, canvasHeight: Float) {
        transformMatrix.reset()
        val translateX = (viewWidth - canvasWidth) / 2f
        val translateY = (viewHeight - canvasHeight) / 2f

        transformMatrix.postTranslate(translateX, translateY)
        currentTranslateX = translateX
        currentTranslateY = translateY

        updateInverseMatrix()
        _transformChanged.value = Matrix(transformMatrix)
    }

    fun fitToScreen(canvasWidth: Float, canvasHeight: Float, viewWidth: Float, viewHeight: Float) {
        val scaleX = viewWidth / canvasWidth
        val scaleY = viewHeight / canvasHeight
        val scale = minOf(scaleX, scaleY) * 0.9f // 90% to add padding

        val translateX = (viewWidth - canvasWidth * scale) / 2f
        val translateY = (viewHeight - canvasHeight * scale) / 2f

        transformMatrix.reset()
        transformMatrix.postScale(scale, scale)
        transformMatrix.postTranslate(translateX, translateY)

        currentScale = scale
        currentTranslateX = translateX
        currentTranslateY = translateY

        updateInverseMatrix()
        _transformChanged.value = Matrix(transformMatrix)
    }

    private fun updateInverseMatrix() {
        transformMatrix.invert(inverseMatrix)
    }

    fun getVisibleBounds(viewWidth: Float, viewHeight: Float): RectF {
        val corners = floatArrayOf(
            0f, 0f,
            viewWidth, 0f,
            viewWidth, viewHeight,
            0f, viewHeight
        )

        inverseMatrix.mapPoints(corners)

        return RectF(
            corners.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: 0f,
            corners.filterIndexed { index, _ -> index % 2 == 1 }.minOrNull() ?: 0f,
            corners.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: viewWidth,
            corners.filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull() ?: viewHeight
        )
    }

    fun getMatrix(): Matrix = Matrix(transformMatrix)
    fun getInverseMatrix(): Matrix = Matrix(inverseMatrix)

    // Limit panning to prevent canvas from going too far off-screen
    fun constrainTranslation(canvasWidth: Float, canvasHeight: Float, viewWidth: Float, viewHeight: Float) {
        val scaledCanvasWidth = canvasWidth * currentScale
        val scaledCanvasHeight = canvasHeight * currentScale

        // Allow canvas to be moved but limit how far
        val maxTranslateX = viewWidth * 0.8f
        val minTranslateX = viewWidth - scaledCanvasWidth - viewWidth * 0.8f
        val maxTranslateY = viewHeight * 0.8f
        val minTranslateY = viewHeight - scaledCanvasHeight - viewHeight * 0.8f

        val constrainedX = currentTranslateX.coerceIn(minTranslateX, maxTranslateX)
        val constrainedY = currentTranslateY.coerceIn(minTranslateY, maxTranslateY)

        if (constrainedX != currentTranslateX || constrainedY != currentTranslateY) {
            val adjustX = constrainedX - currentTranslateX
            val adjustY = constrainedY - currentTranslateY
            translate(adjustX, adjustY)
        }
    }
}