package com.example.whiteboardapp.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.whiteboardapp.model.DrawingCommand
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.TextObject

class CommandManager(private val maxHistorySize: Int = 50) {

    private val undoStack = mutableListOf<DrawingCommand>()
    private val redoStack = mutableListOf<DrawingCommand>()

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> = _canRedo

    private val _historyChanged = MutableLiveData<Pair<Int, Int>>()
    val historyChanged: LiveData<Pair<Int, Int>> = _historyChanged

    fun executeCommand(command: DrawingCommand, objectManager: ObjectManager) {
        // Coalescing logic: Check if the new command can be merged with the last one
        if (undoStack.isNotEmpty()) {
            val lastCommand = undoStack.last()
            if (lastCommand.canCoalesce(command)) {
                // If so, replace the last command with the merged version
                undoStack[undoStack.lastIndex] = coalesceCommands(lastCommand, command)
                // Execute only the new part of the command
                command.execute(objectManager)
                updateState()
                return
            }
        }

        command.execute(objectManager)
        undoStack.add(command)

        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }

        redoStack.clear()
        updateState()
    }

    fun undo(objectManager: ObjectManager) {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.removeAt(undoStack.lastIndex)
            command.undo(objectManager)
            redoStack.add(command)
            updateState()
        }
    }

    fun redo(objectManager: ObjectManager) {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.removeAt(redoStack.lastIndex)
            command.execute(objectManager)
            undoStack.add(command)
            updateState()
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    private fun coalesceCommands(existing: DrawingCommand, new: DrawingCommand): DrawingCommand {
        return when {
            existing is DrawingCommand.MoveObjectCommand && new is DrawingCommand.MoveObjectCommand -> {
                // Merge two move commands into one that covers the total distance
                DrawingCommand.MoveObjectCommand(
                    objectId = existing.objectId,
                    newX = new.newX, // Final position is from the new command
                    newY = new.newY,
                    oldX = existing.oldX, // Initial position is from the original command
                    oldY = existing.oldY
                )
            }
            // Add other coalescing rules here if needed
            else -> new // Default to the new command if no rule matches
        }
    }

    private fun updateState() {
        _canUndo.postValue(undoStack.isNotEmpty())
        _canRedo.postValue(redoStack.isNotEmpty())
        _historyChanged.postValue(Pair(undoStack.size, redoStack.size))
    }
}