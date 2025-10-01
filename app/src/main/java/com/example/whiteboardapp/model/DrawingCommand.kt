package com.example.whiteboardapp.model

import com.example.whiteboardapp.manager.ObjectManager

sealed class DrawingCommand {
    abstract fun execute(objectManager: ObjectManager)
    abstract fun undo(objectManager: ObjectManager)

    // Determines if this command can be merged with the next one
    abstract fun canCoalesce(other: DrawingCommand): Boolean

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

    data class RemoveObjectCommand(
        val drawingObject: DrawingObject,
        val index: Int
    ) : DrawingCommand() {
        override fun execute(objectManager: ObjectManager) {
            objectManager.removeObjectById(drawingObject.id)
        }

        override fun undo(objectManager: ObjectManager) {
            objectManager.addObjectAt(drawingObject, index)
        }

        override fun canCoalesce(other: DrawingCommand): Boolean = false
    }

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

        // We can coalesce consecutive move commands for the same object
        override fun canCoalesce(other: DrawingCommand): Boolean {
            return other is MoveObjectCommand && other.objectId == this.objectId
        }
    }

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

    data class BatchCommand(
        val commands: List<DrawingCommand>,
        val description: String = "Batch Operation"
    ) : DrawingCommand() {
        override fun execute(objectManager: ObjectManager) {
            commands.forEach { it.execute(objectManager) }
        }

        override fun undo(objectManager: ObjectManager) {
            // Undo in reverse order
            commands.asReversed().forEach { it.undo(objectManager) }
        }

        override fun canCoalesce(other: DrawingCommand): Boolean = false
    }
}