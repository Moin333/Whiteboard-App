package com.example.whiteboardapp.viewmodel

import android.graphics.Color
import android.graphics.PointF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.whiteboardapp.manager.ObjectManager
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.DrawingTool
import androidx.lifecycle.viewModelScope
import com.example.whiteboardapp.data.WhiteboardRepository
import com.example.whiteboardapp.data.db.WhiteboardSession
import com.example.whiteboardapp.manager.CommandManager
import com.example.whiteboardapp.model.DrawingCommand
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId

class WhiteboardViewModel : ViewModel() {
    private val repository = WhiteboardRepository()
    val objectManager = ObjectManager()
    private val commandManager = CommandManager()


    // LiveData for Undo/Redo states from CommandManager
    val canUndo: LiveData<Boolean> = commandManager.canUndo
    val canRedo: LiveData<Boolean> = commandManager.canRedo
    val historyChanged: LiveData<Pair<Int, Int>> = commandManager.historyChanged

    // State for tracking object transformations
    private var preTransformState: DrawingObject? = null
    private var preMovePosition: PointF? = null

    // LiveData for the UI
    private val _currentSessionId = MutableLiveData<ObjectId?>(null)
    val currentSessionId: LiveData<ObjectId?> = _currentSessionId
    private val _drawingObjects = MutableLiveData<List<DrawingObject>>(emptyList())
    val drawingObjects: LiveData<List<DrawingObject>> = _drawingObjects
    private val _currentTool = MutableLiveData<DrawingTool>(DrawingTool.Pen)
    val currentTool: LiveData<DrawingTool> = _currentTool

    private val _strokeWidth = MutableLiveData(5f)
    val strokeWidth: LiveData<Float> = _strokeWidth

    private val _strokeColor = MutableLiveData(Color.BLACK)
    val strokeColor: LiveData<Int> = _strokeColor

    private val _isFillEnabled = MutableLiveData(false)
    val isFillEnabled: LiveData<Boolean> = _isFillEnabled

    private val _eraserRadius = MutableLiveData(20f)
    val eraserRadius: LiveData<Float> = _eraserRadius

    // --- Public Methods ---
    suspend fun getSessions(): List<WhiteboardSession> {
        return repository.getAllSessions()
    }
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
        val command = DrawingCommand.AddObjectCommand(obj)
        commandManager.executeCommand(command, objectManager)
        updateObjectsLiveData()
    }

    fun updateObject(oldState: DrawingObject, newState: DrawingObject) {
        val command = DrawingCommand.ModifyObjectCommand(oldState, newState)
        commandManager.executeCommand(command, objectManager)
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
        commandManager.clear()
        updateObjectsLiveData()
    }

    fun setEraserRadius(radius: Float) {
        _eraserRadius.value = radius
    }

    fun eraseObjectsAt(x: Float, y: Float) {
        val radius = _eraserRadius.value ?: 20f
        val objectsToErase = objectManager.getObjectsAt(x, y, radius)

        if (objectsToErase.isNotEmpty()) {
            val commands = objectsToErase.map { (obj, index) ->
                DrawingCommand.RemoveObjectCommand(obj, index)
            }
            val batchCommand = DrawingCommand.BatchCommand(commands, "Erase objects")
            commandManager.executeCommand(batchCommand, objectManager)
            updateObjectsLiveData()
        }
    }

    fun saveCurrentSession(name: String) {
        viewModelScope.launch {
            val savedId = repository.saveOrUpdateSession(
                _currentSessionId.value,
                name,
                objectManager.getObjects()
            )
            _currentSessionId.value = savedId
        }
    }

    fun saveAsNewSession(name: String) {
        viewModelScope.launch {
            // We force sessionId to be null to create a new entry
            val savedId = repository.saveOrUpdateSession(
                null,
                name,
                objectManager.getObjects()
            )
            _currentSessionId.value = savedId // The app is now tracking the new copy
        }
    }

    fun loadSession(sessionId: ObjectId) {
        viewModelScope.launch {
            val objects = repository.loadSession(sessionId)
            objectManager.clear()
            objects.forEach { objectManager.addObject(it) }
            _currentSessionId.value = sessionId
            updateObjectsLiveData() // Refresh the UI
        }
    }

    fun createNewSession() {
        objectManager.clear()
        commandManager.clear()
        _currentSessionId.value = null
        updateObjectsLiveData()
    }

    fun onTransformStart(obj: DrawingObject) {
        preTransformState = obj.clone() // Requires a deep copy method on DrawingObject
    }

    fun onTransformEnd(currentObject: DrawingObject) {
        preTransformState?.let { oldState ->
            val command = DrawingCommand.ModifyObjectCommand(oldState, currentObject.clone())
            commandManager.executeCommand(command, objectManager)
            updateObjectsLiveData()
        }
        preTransformState = null
    }

    fun onMoveStart(obj: DrawingObject) {
        preMovePosition = objectManager.getObjectPosition(obj.id)
    }

    fun onMoveEnd(obj: DrawingObject) {
        preMovePosition?.let { oldPos ->
            val newPos = objectManager.getObjectPosition(obj.id)
            if (newPos != null && (oldPos.x != newPos.x || oldPos.y != newPos.y)) {
                val command = DrawingCommand.MoveObjectCommand(obj.id, newPos.x, newPos.y, oldPos.x, oldPos.y)
                commandManager.executeCommand(command, objectManager)
                updateObjectsLiveData()
            }
        }
        preMovePosition = null
    }

    fun undo() {
        commandManager.undo(objectManager)
        updateObjectsLiveData()
    }

    fun redo() {
        commandManager.redo(objectManager)
        updateObjectsLiveData()
    }

    // --- Private Helper ---
    private fun updateObjectsLiveData() {
        _drawingObjects.value = objectManager.getObjects()
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources if needed
        objectManager.clear()
    }
}