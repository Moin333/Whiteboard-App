package com.example.whiteboardapp.viewmodel

import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.whiteboardapp.model.DrawingPath
import com.example.whiteboardapp.model.DrawingTool

class WhiteboardViewModel : ViewModel() {

    // LiveData for the currently selected tool
    private val _currentTool = MutableLiveData<DrawingTool>(DrawingTool.Pen)
    val currentTool: LiveData<DrawingTool> = _currentTool

    // LiveData for the current stroke width
    private val _strokeWidth = MutableLiveData(5f)
    val strokeWidth: LiveData<Float> = _strokeWidth

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

    fun setStrokeColor(color: Int) {
        _strokeColor.value = color
    }

    fun addPath(path: DrawingPath) {
        val currentPaths = _drawingPaths.value.orEmpty().toMutableList()
        currentPaths.add(path)
        _drawingPaths.value = currentPaths
        _canUndo.value = currentPaths.isNotEmpty()
    }
}