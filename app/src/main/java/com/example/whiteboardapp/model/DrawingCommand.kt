package com.example.whiteboardapp.model

import com.example.whiteboardapp.manager.ObjectManager

/**
 * A sealed class representing an action that can be performed and undone.
 * This is the core of the undo/redo system, based on the Command pattern.
 */
sealed class DrawingCommand {
    // Executes the command on the [ObjectManager].
    abstract fun execute(objectManager: ObjectManager)
    // Reverts the command's effects on the [ObjectManager].
    abstract fun undo(objectManager: ObjectManager)

    /**
     * Determines if this command can be merged with a subsequent command.
     * This is an optimization to prevent the undo stack from flooding with tiny, repetitive actions.
     * For example, multiple small `Move` commands can be coalesced into a single one.
     *
     * @param other The next command to potentially merge with.
     * @return True if the commands can be merged, false otherwise.
     */
    abstract fun canCoalesce(other: DrawingCommand): Boolean

    // A command to add a new object to the canvas.
    data class AddObjectCommand(
        val drawingObject: DrawingObject
    ) : DrawingCommand() {
        override fun execute(objectManager: ObjectManager) {
            objectManager.addObject(drawingObject)
        }

        override fun undo(objectManager: ObjectManager) {
            objectManager.removeObjectById(drawingObject.id)
        }

        override fun canCoalesce(other: DrawingCommand): Boolean = false
    }

    // A command to remove an object from the canvas.
    data class RemoveObjectCommand(
        val drawingObject: DrawingObject,
        val index: Int // Store the original index to restore it correctly.
    ) : DrawingCommand() {
        override fun execute(objectManager: ObjectManager) {
            objectManager.removeObjectById(drawingObject.id)
        }

        override fun undo(objectManager: ObjectManager) {
            objectManager.addObjectAt(drawingObject, index)
        }

        override fun canCoalesce(other: DrawingCommand): Boolean = false
    }

    // A command to move an object.
    data class MoveObjectCommand(
        val objectId: String,
        val newX: Float,
        val newY: Float,
        val oldX: Float,
        val oldY: Float
    ) : DrawingCommand() {
        override fun execute(objectManager: ObjectManager) {
            objectManager.setObjectPosition(objectId, newX, newY)
        }

        override fun undo(objectManager: ObjectManager) {
            objectManager.setObjectPosition(objectId, oldX, oldY)
        }

        // We can merge consecutive move commands for the same object.
        override fun canCoalesce(other: DrawingCommand): Boolean {
            return other is MoveObjectCommand && other.objectId == this.objectId
        }
    }

    // A command that modifies an object's properties (e.g., resizing, rotating).
    data class ModifyObjectCommand(
        val oldState: DrawingObject,
        val newState: DrawingObject
    ) : DrawingCommand() {
        override fun execute(objectManager: ObjectManager) {
            objectManager.updateObject(newState)
        }

        override fun undo(objectManager: ObjectManager) {
            objectManager.updateObject(oldState)
        }

        override fun canCoalesce(other: DrawingCommand): Boolean = false
    }

    // A composite command that groups multiple commands into a single undoable action.
    data class BatchCommand(
        val commands: List<DrawingCommand>,
        val description: String = "Batch Operation"
    ) : DrawingCommand() {
        override fun execute(objectManager: ObjectManager) {
            commands.forEach { it.execute(objectManager) }
        }

        override fun undo(objectManager: ObjectManager) {
            // Undo must be performed in the reverse order of execution.
            commands.asReversed().forEach { it.undo(objectManager) }
        }

        override fun canCoalesce(other: DrawingCommand): Boolean = false
    }
}