package com.example.whiteboardapp.viewmodel

import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.whiteboardapp.manager.ObjectManager
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.DrawingTool

class WhiteboardViewModel : ViewModel() {

    val objectManager = ObjectManager()

    // --- LiveData ---
    private val _currentTool = MutableLiveData<DrawingTool>(DrawingTool.Pen)
    val currentTool: LiveData<DrawingTool> = _currentTool

    private val _strokeWidth = MutableLiveData(5f)
    val strokeWidth: LiveData<Float> = _strokeWidth

    private val _strokeColor = MutableLiveData(Color.BLACK)
    val strokeColor: LiveData<Int> = _strokeColor

    private val _isFillEnabled = MutableLiveData(false)
    val isFillEnabled: LiveData<Boolean> = _isFillEnabled

    private val _drawingObjects = MutableLiveData<List<DrawingObject>>(emptyList())
    val drawingObjects: LiveData<List<DrawingObject>> = _drawingObjects

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _eraserRadius = MutableLiveData(20f)
    val eraserRadius: LiveData<Float> = _eraserRadius

    // --- Public Methods ---
    fun selectTool(tool: DrawingTool) {
        _currentTool.value = tool
        // Clear selection when switching tools (except for Select tool)
        if (tool !is DrawingTool.Select) {
            objectManager.clearSelection()
        }
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

    fun setStrokeColor(color: Int) {
        _strokeColor.value = color
    }

    fun toggleFill(isEnabled: Boolean) {
        _isFillEnabled.value = isEnabled
    }

    fun addObject(obj: DrawingObject) {
        objectManager.addObject(obj)
        updateObjectsLiveData()
    }

    fun undo() {
        objectManager.undo()
        updateObjectsLiveData()
    }

    fun selectObjectAt(x: Float, y: Float): DrawingObject? {
        val selectedObject = objectManager.selectObjectAt(x, y)
        // Trigger a view refresh to show selection
        updateObjectsLiveData()
        return selectedObject
    }

    fun moveSelectedObject(dx: Float, dy: Float) {
        objectManager.moveSelected(dx, dy)
        updateObjectsLiveData()
    }

    fun clearCanvas() {
        objectManager.clear()
        updateObjectsLiveData()
    }

    fun setEraserRadius(radius: Float) {
        _eraserRadius.value = radius
    }

    fun eraseObjectsAt(x: Float, y: Float) {
        val radius = _eraserRadius.value ?: 20f
        val erasedObjects = objectManager.eraseObjectsAt(x, y, radius)

        // Only update if objects were actually erased
        if (erasedObjects.isNotEmpty()) {
            updateObjectsLiveData()
        }
    }

    fun updateObject(obj: DrawingObject) {
        objectManager.updateObject(obj)
        updateObjectsLiveData()
    }

    // --- Private Helper ---
    private fun updateObjectsLiveData() {
        val currentObjects = objectManager.getObjects()
        _drawingObjects.value = currentObjects
        _canUndo.value = currentObjects.isNotEmpty()
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources if needed
        objectManager.clear()
    }
}