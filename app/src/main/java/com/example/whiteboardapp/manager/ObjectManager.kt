package com.example.whiteboardapp.manager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.HandleType

/**
 * Manages the state of all drawing objects on the canvas.
 * This class is responsible for adding, removing, selecting, transforming,
 * and drawing all objects. It acts as the "controller" for the canvas content.
 */
class ObjectManager {
    private val objects = mutableListOf<DrawingObject>()
    private var selectedObject: DrawingObject? = null
    private val erasureHandler = ErasureHandler()
    private val transformHandleManager = TransformHandleManager()
    private val transformationManager = TransformationManager()

    fun addObject(obj: DrawingObject) {
        objects.add(obj)
    }

    // Selects the top-most object at a given coordinate.
    fun selectObjectAt(x: Float, y: Float): DrawingObject? {
        // Priority 1: Check if a transform handle was tapped.
        if (selectedObject != null) {
            val handle = transformHandleManager.getHandleAt(x, y)
            if (handle != null) {
                transformationManager.startTransform(selectedObject!!, handle.type, x, y)
                return selectedObject // Return the same object to indicate a transform has started.
            }
        }

        // Priority 2: Select a new object.
        selectedObject = objects.asReversed().find { it.contains(x, y) }
        transformationManager.endTransform() // End any previous transform

        // Update handles for the newly selected object
        selectedObject?.let {
            transformHandleManager.updateHandles(it.bounds, it.rotation)
        }

        return selectedObject
    }

    fun clearSelection() {
        selectedObject = null
        transformationManager.endTransform()
    }

    fun moveSelected(dx: Float, dy: Float) {
        selectedObject?.move(dx, dy)
        // Update handle positions after moving.
        selectedObject?.let {
            transformHandleManager.updateHandles(it.bounds, it.rotation)
        }
    }

    // Updates an ongoing transformation (resize/rotate) of the selected object.
    fun updateTransform(x: Float, y: Float): Boolean {
        val obj = selectedObject ?: return false
        val transformed = transformationManager.updateTransform(obj, x, y)
        if (transformed) {
            transformHandleManager.updateHandles(obj.bounds, obj.rotation)
        }
        return transformed
    }

    fun endTransform() {
        transformationManager.endTransform()
    }

    fun isTransforming(): Boolean {
        return selectedObject != null && transformationManager.isTransforming()
    }

    // Draws the selection box and transform handles for the selected object.
    fun drawSelection(canvas: Canvas) {
        selectedObject?.let { obj ->
            val selectionPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
            }
            canvas.drawRect(obj.bounds, selectionPaint)
            drawTransformHandles(canvas)
        }
    }

    private fun drawTransformHandles(canvas: Canvas) {
        val handles = transformHandleManager.getAllHandles()
        val handlePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val handleStrokePaint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val rotateHandlePaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val rotateStrokePaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        for (handle in handles) {
            if (handle.type == HandleType.ROTATE) {
                // Draw rotation handle differently
                canvas.drawCircle(handle.position.x, handle.position.y, handle.radius, rotateHandlePaint)
                canvas.drawCircle(handle.position.x, handle.position.y, handle.radius, rotateStrokePaint)
                // Draw line connecting to top edge
                selectedObject?.let { obj ->
                    canvas.drawLine(obj.bounds.centerX(), obj.bounds.top, handle.position.x, handle.position.y, rotateStrokePaint)
                }
            } else {
                // Draw resize handles
                canvas.drawCircle(handle.position.x, handle.position.y, handle.radius, handlePaint)
                canvas.drawCircle(handle.position.x, handle.position.y, handle.radius, handleStrokePaint)
            }
        }
    }

    fun getObjects(): List<DrawingObject> = objects.toList()

    fun getSelectedObject(): DrawingObject? = selectedObject

    fun clear() {
        objects.clear()
        selectedObject = null
    }

    fun getObjectsAt(centerX: Float, centerY: Float, radius: Float): List<Pair<DrawingObject, Int>> {
        val foundObjects = mutableListOf<Pair<DrawingObject, Int>>()

        objects.forEachIndexed { index, obj ->
            if (erasureHandler.canEraseObject(obj, centerX, centerY, radius)) {
                foundObjects.add(Pair(obj, index))
            }
        }
        return foundObjects
    }

    fun getObjectById(id: String): DrawingObject? = objects.find { it.id == id }

    fun getObjectPosition(objectId: String): PointF? {
        return getObjectById(objectId)?.let {
            val bounds = it.bounds
            PointF(bounds.left, bounds.top)
        }
    }

    fun removeObjectById(id: String): DrawingObject? {
        val obj = getObjectById(id)
        if (obj != null) {
            objects.remove(obj)
            if (obj == selectedObject) {
                clearSelection()
            }
        }
        return obj
    }

    fun addObjectAt(obj: DrawingObject, index: Int) {
        if (index in 0..objects.size) {
            objects.add(index, obj)
        } else {
            objects.add(obj) // Add to the end if index is out of bounds
        }
    }

    fun setObjectPosition(id: String, x: Float, y: Float) {
        getObjectById(id)?.let { obj ->
            val currentBounds = obj.bounds
            val dx = x - currentBounds.left
            val dy = y - currentBounds.top
            obj.move(dx, dy)
        }
    }

    fun updateObject(updatedObject: DrawingObject) {
        val index = objects.indexOfFirst { it.id == updatedObject.id }
        if (index != -1) {
            objects[index] = updatedObject
            if (updatedObject == selectedObject) {
                transformHandleManager.updateHandles(updatedObject.bounds, updatedObject.rotation)
            }
        }
    }
}