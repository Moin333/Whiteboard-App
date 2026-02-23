package com.example.whiteboardapp.viewmodel

import android.content.Context
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
import com.example.whiteboardapp.manager.AutoSaveManager
import com.example.whiteboardapp.manager.CommandManager
import com.example.whiteboardapp.manager.ExportManager
import com.example.whiteboardapp.model.DrawingCommand
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId
import java.io.File

/**
 * The ViewModel for the Whiteboard. It holds the UI state and handles all user interactions
 * by delegating logic to the appropriate managers or repository.
 */
class WhiteboardViewModel : ViewModel() {
    private val repository = WhiteboardRepository()
    val objectManager = ObjectManager()
    private val commandManager = CommandManager()

    // Auto-save manager — survives rotation, cancelled only when ViewModel is cleared
    private val autoSaveManager = AutoSaveManager(
        repository = repository,
        intervalMinutes = 5
    )

    // LiveData for Undo/Redo states from CommandManager
    val canUndo: LiveData<Boolean> = commandManager.canUndo
    val canRedo: LiveData<Boolean> = commandManager.canRedo

    // State for tracking object transformations
    private var preTransformState: DrawingObject? = null
    private var preMovePosition: PointF? = null

    // --- UI State LiveData ---
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
    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    init {
        // Start auto-save in viewModelScope — survives rotation, single instance,
        // cancelled only when user navigates away (ViewModel.onCleared)
        autoSaveManager.startAutoSave(
            scope = viewModelScope,
            getObjects = { objectManager.getObjects() },
            getCurrentSessionId = { _currentSessionId.value }
        )
    }

    // --- Public Methods ---
    suspend fun getSessions(): List<WhiteboardSession> {
        return repository.getAllSessions()
    }

    /**
     * Sets the active drawing tool.
     * @param tool The [DrawingTool] to activate.
     */
    fun selectTool(tool: DrawingTool) {
        _currentTool.value = tool
        // Deselect any object when switching away from the select tool.
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

    /**
     * Adds a new drawing object to the canvas via the CommandManager.
     * @param obj The [DrawingObject] to add.
     */
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

    fun saveCurrentSession(name: String, onResult: (Result<ObjectId>) -> Unit) {
        viewModelScope.launch {
            try {
                val savedId = repository.saveOrUpdateSession(
                    _currentSessionId.value,
                    name,
                    objectManager.getObjects()
                )
                _currentSessionId.value = savedId
                onResult(Result.success(savedId))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun saveAsNewSession(name: String, onResult: (Result<ObjectId>) -> Unit) {
        viewModelScope.launch {
            try {
                val savedId = repository.saveOrUpdateSession(
                    null,
                    name,
                    objectManager.getObjects()
                )
                _currentSessionId.value = savedId
                onResult(Result.success(savedId))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun loadSession(sessionId: ObjectId, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                val objects = repository.loadSession(sessionId)
                objectManager.clear()
                objects.forEach { objectManager.addObject(it) }
                _currentSessionId.value = sessionId
                updateObjectsLiveData()
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
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

    /**
     * Exports the current canvas content to a file.
     *
     * @param context The application context.
     * @param options The export configuration.
     * @param outputFile The file to write to.
     * @param canvasWidth The total width of the canvas content.
     * @param canvasHeight The total height of the canvas content.
     * @param onComplete A callback to report the result.
     */
    fun exportCanvas(
        context: Context,
        options: ExportManager.ExportOptions,
        outputFile: File,
        canvasWidth: Int,
        canvasHeight: Int,
        onComplete: (Result<File>) -> Unit
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val exportManager = ExportManager(context)
                val result = exportManager.exportCanvas(
                    objectManager.getObjects(),
                    canvasWidth,
                    canvasHeight,
                    options,
                    outputFile
                )
                onComplete(result)
            } finally {
                _isExporting.value = false
            }
        }
    }

    /** Pushes the current list of objects from the ObjectManager to the LiveData. */
    private fun updateObjectsLiveData() {
        _drawingObjects.value = objectManager.getObjects()
    }

    override fun onCleared() {
        super.onCleared()
        // Stop auto-save when ViewModel is cleared (user navigates away)
        autoSaveManager.stopAutoSave()
        objectManager.clear()
    }
}