package com.example.whiteboardapp.manager

import kotlinx.coroutines.*
import com.example.whiteboardapp.data.WhiteboardRepository
import com.example.whiteboardapp.model.DrawingObject
import org.mongodb.kbson.ObjectId

/**
 * Manages the auto-save functionality for the whiteboard.
 * It periodically saves the current state of the canvas to the database in the background.
 *
 * @property repository The [WhiteboardRepository] used for saving the session.
 * @property intervalMinutes The time interval in minutes between auto-saves.
 */
class AutoSaveManager(
    private val repository: WhiteboardRepository,
    private val intervalMinutes: Long = 5
) {
    private var autoSaveJob: Job? = null
    private var lastAutoSaveId: ObjectId? = null

    /**
     * Starts the auto-save coroutine. If one is already running, it's cancelled and a new one is started.
     *
     * @param scope The [CoroutineScope] in which to run the auto-save job (typically viewModelScope).
     * @param getObjects A lambda function that provides the current list of [DrawingObject]s to be saved.
     * @param getCurrentSessionId A lambda function that provides the ID of the currently active session.
     */
    fun startAutoSave(
        scope: CoroutineScope,
        getObjects: () -> List<DrawingObject>,
        getCurrentSessionId: () -> ObjectId? = { null }
    ) {
        autoSaveJob?.cancel() // Cancel any existing job.

        autoSaveJob = scope.launch {
            while (isActive) {
                delay(intervalMinutes * 60 * 1000)
                try {
                    val objects = getObjects()
                    if (objects.isNotEmpty()) {
                        // If a session is open, update it. Otherwise, create a new auto-save entry.
                        val sessionId = getCurrentSessionId() ?: lastAutoSaveId

                        lastAutoSaveId = repository.saveOrUpdateSession(
                            sessionId = sessionId,
                            name = "Auto-save ${System.currentTimeMillis()}",
                            objects = objects
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Stops the auto-save job. This should be called when the ViewModel is cleared.
    fun stopAutoSave() {
        autoSaveJob?.cancel()
    }
}