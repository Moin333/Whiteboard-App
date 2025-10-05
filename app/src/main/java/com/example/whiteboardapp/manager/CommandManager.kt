package com.example.whiteboardapp.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.whiteboardapp.model.DrawingCommand

/**
 * Manages the undo and redo history of the whiteboard.
 * It holds two stacks: one for undoable commands and one for redoable commands.
 */
class CommandManager(private val maxHistorySize: Int = 50) {

    private val undoStack = mutableListOf<DrawingCommand>()
    private val redoStack = mutableListOf<DrawingCommand>()

    // LiveData to enable/disable UI buttons.
    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo
    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> = _canRedo
    private val _historyChanged = MutableLiveData<Pair<Int, Int>>()
    val historyChanged: LiveData<Pair<Int, Int>> = _historyChanged

    /**
     * Executes a command, adds it to the undo stack, and clears the redo stack.
     * Implements command coalescing to merge similar, consecutive actions.
     *
     * @param command The command to execute.
     * @param objectManager The [ObjectManager] on which to execute the command.
     */
    fun executeCommand(command: DrawingCommand, objectManager: ObjectManager) {
        // Coalescing logic: If the new command can be merged with the last one...
        if (undoStack.isNotEmpty()) {
            val lastCommand = undoStack.last()
            if (lastCommand.canCoalesce(command)) {
                // ...replace the last command with the merged version.
                undoStack[undoStack.lastIndex] = coalesceCommands(lastCommand, command)
                command.execute(objectManager) // Execute the action.
                updateState()
                return
            }
        }

        command.execute(objectManager)
        undoStack.add(command)

        // Maintain max history size.
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }

        redoStack.clear() // Any new action clears the redo history.
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

    // Merges two coalesce-able commands into a single command.
    private fun coalesceCommands(existing: DrawingCommand, new: DrawingCommand): DrawingCommand {
        return when {
            existing is DrawingCommand.MoveObjectCommand && new is DrawingCommand.MoveObjectCommand -> {
                // A move from A->B followed by B->C becomes a single move from A->C.
                DrawingCommand.MoveObjectCommand(
                    objectId = existing.objectId,
                    newX = new.newX, // Final position is from the new command
                    newY = new.newY,
                    oldX = existing.oldX, // Initial position is from the original command
                    oldY = existing.oldY
                )
            }
            else -> new
        }
    }

    private fun updateState() {
        _canUndo.postValue(undoStack.isNotEmpty())
        _canRedo.postValue(redoStack.isNotEmpty())
        _historyChanged.postValue(Pair(undoStack.size, redoStack.size))
    }
}