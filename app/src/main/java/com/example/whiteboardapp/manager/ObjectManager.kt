package com.example.whiteboardapp.manager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.HandleType
import com.example.whiteboardapp.model.TransformHandleManager

class ObjectManager {
    private val objects = mutableListOf<DrawingObject>()
    private var selectedObject: DrawingObject? = null
    private val erasureHandler = ErasureHandler()
    private val transformHandleManager = TransformHandleManager()
    private val transformationManager = TransformationManager()

    fun addObject(obj: DrawingObject) {
        objects.add(obj)
    }

    fun undo(): DrawingObject? {
        if (objects.isNotEmpty()) {
            val removedObject = objects.removeAt(objects.lastIndex)
            // If the removed object was selected, deselect it
            if (removedObject == selectedObject) {
                selectedObject = null
            }
            return removedObject
        }
        return null
    }

    fun selectObjectAt(x: Float, y: Float): DrawingObject? {
        // First, check if a handle of an already selected object is tapped
        if (selectedObject != null) {
            val handle = transformHandleManager.getHandleAt(x, y)
            if (handle != null) {
                transformationManager.startTransform(selectedObject!!, handle.type, x, y)
                return selectedObject
            }
        }

        // If no handle is tapped, try to select a new object
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
        selectedObject?.let {
            transformHandleManager.updateHandles(it.bounds, it.rotation)
        }
    }

    fun updateTransform(x: Float, y: Float): Boolean {
        val obj = selectedObject ?: return false
        val transformed = transformationManager.updateTransform(obj, x, y)
        if (transformed) {
            // Update handles after transformation
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

    fun drawAll(canvas: Canvas) {
        objects.forEach { it.draw(canvas) }
    }

    fun drawSelection(canvas: Canvas) {
        selectedObject?.let { obj ->
            // Draw selection bounds
            val selectionPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
            }
            canvas.drawRect(obj.bounds, selectionPaint)

            // Draw transform handles
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

    /**
     * Enhanced eraser that uses sophisticated path intersection for PathObjects
     * and simple bounds checking for ShapeObjects
     */
    fun eraseObjectsAt(centerX: Float, centerY: Float, radius: Float): List<DrawingObject> {
        val erasedObjects = mutableListOf<DrawingObject>()

        // Use an iterator to safely remove elements while iterating
        val iterator = objects.iterator()
        while (iterator.hasNext()) {
            val obj = iterator.next()

            if (erasureHandler.canEraseObject(obj, centerX, centerY, radius)) {
                erasedObjects.add(obj)

                // If the erased object was selected, deselect it
                if (obj == selectedObject) {
                    selectedObject = null
                }

                iterator.remove()
            }
        }

        return erasedObjects
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

    fun getObjectCount(): Int = objects.size

    fun hasObjects(): Boolean = objects.isNotEmpty()
}