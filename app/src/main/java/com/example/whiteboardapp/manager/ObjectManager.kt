package com.example.whiteboardapp.manager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.example.whiteboardapp.model.DrawingObject

class ObjectManager {
    private val objects = mutableListOf<DrawingObject>()
    private var selectedObject: DrawingObject? = null
    private val erasureHandler = ErasureHandler()

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
        // Iterate in reverse to select the topmost object
        selectedObject = objects.asReversed().find { it.contains(x, y) }
        return selectedObject
    }

    fun clearSelection() {
        selectedObject = null
    }

    fun moveSelected(dx: Float, dy: Float) {
        selectedObject?.move(dx, dy)
    }

    fun drawAll(canvas: Canvas) {
        objects.forEach { it.draw(canvas) }
    }

    fun drawSelection(canvas: Canvas) {
        selectedObject?.let { obj ->
            val paint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
            }
            val bounds = obj.bounds
            canvas.drawRect(bounds, paint)
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

    fun getObjectCount(): Int = objects.size

    fun hasObjects(): Boolean = objects.isNotEmpty()
}