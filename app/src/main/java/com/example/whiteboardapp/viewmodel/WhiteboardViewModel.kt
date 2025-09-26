package com.example.whiteboardapp.viewmodel

import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.whiteboardapp.manager.PathManager
import com.example.whiteboardapp.model.DrawingPath
import com.example.whiteboardapp.model.DrawingTool

class WhiteboardViewModel : ViewModel() {

    private val pathManager = PathManager()

    // LiveData for the currently selected tool
    private val _currentTool = MutableLiveData<DrawingTool>(DrawingTool.Pen)
    val currentTool: LiveData<DrawingTool> = _currentTool

    // LiveData for the current stroke width
    private val _strokeWidth = MutableLiveData(5f)
    val strokeWidth: LiveData<Float> = _strokeWidth

    // LiveData for the eraser radius
    private val _eraserRadius = MutableLiveData(20f)
    val eraserRadius: LiveData<Float> = _eraserRadius

    // LiveData for the current stroke color
    private val _strokeColor = MutableLiveData(Color.BLACK)
    val strokeColor: LiveData<Int> = _strokeColor

    // LiveData for the list of all paths drawn on the canvas
    private val _drawingPaths = MutableLiveData<List<DrawingPath>>(emptyList())
    val drawingPaths: LiveData<List<DrawingPath>> = _drawingPaths

    // LiveData to enable/disable the undo button
    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    fun selectTool(tool: DrawingTool) {
        _currentTool.value = tool
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

    fun setEraserRadius(radius: Float) {
        _eraserRadius.value = radius
    }

    fun setStrokeColor(color: Int) {
        _strokeColor.value = color
    }

    fun addPath(path: DrawingPath) {
        pathManager.addPath(path)
        updatePathsLiveData()
    }

    fun eraseAt(x: Float, y: Float, radius: Float) {
        val erasedPaths = pathManager.eraseAt(x, y, radius)
        if (erasedPaths.isNotEmpty()) {
            updatePathsLiveData()
        }
    }

    fun undo() {
        pathManager.undo()
        updatePathsLiveData()
    }

    private fun updatePathsLiveData() {
        val currentPaths = pathManager.getAllPaths()
        _drawingPaths.value = currentPaths
        _canUndo.value = currentPaths.isNotEmpty()
    }
}