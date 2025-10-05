package com.example.whiteboardapp.data

import com.example.whiteboardapp.WhiteboardApplication
import com.example.whiteboardapp.data.db.DrawingObjectRealm
import com.example.whiteboardapp.data.db.WhiteboardSession
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.TextObject
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import org.mongodb.kbson.ObjectId

/**
 * Repository for handling all data operations related to whiteboard sessions.
 * It abstracts the data source (Realm) from the ViewModel and other parts of the app.
 * All functions are `suspend` to ensure they run on a background thread.
 */
class WhiteboardRepository {
    /**
     * Saves a new session or updates an existing one in the database.
     *
     * @param sessionId The ID of the session to update. If null, a new session is created.
     * @param name The name of the session.
     * @param objects The list of [DrawingObject] models to save.
     * @return The [ObjectId] of the saved session.
     */
    suspend fun saveOrUpdateSession(sessionId: ObjectId?, name: String, objects: List<DrawingObject>): ObjectId {
        if (objects.isEmpty()) {
            // Avoid saving an empty session, just return the ID.
            return sessionId ?: ObjectId()
        }

        val realm = Realm.open(WhiteboardApplication.config)
        lateinit var savedSessionId: ObjectId

        // Convert in-memory DrawingObjects to realm-compatible DrawingObjectRealm instances.
        val realmObjects = objects.mapIndexed { index, obj ->
            DrawingObjectRealm().apply {
                this.type = getObjectType(obj)
                this.data = DrawingObjectSerializer.serialize(obj)
                this.order = index
            }
        }.toRealmList()

        try {
            realm.write {
                // Find the existing session or create a new one.
                val sessionToSave = if (sessionId == null) {
                    WhiteboardSession().apply { this.name = name }
                } else {
                    query<WhiteboardSession>("id == $0", sessionId).first().find()
                        ?: WhiteboardSession().apply { this.id = sessionId }
                }
                sessionToSave.name = name

                // Update timestamps and objects.
                sessionToSave.lastModified = System.currentTimeMillis()
                sessionToSave.objects.clear()
                sessionToSave.objects.addAll(realmObjects)

                // Use copyToRealm with UpdatePolicy.ALL to insert or update.
                val managedSession = copyToRealm(sessionToSave, UpdatePolicy.ALL)
                savedSessionId = managedSession.id
            }
        } finally {
            realm.close()
        }
        return savedSessionId
    }

    /**
     * Loads a specific session from the database by its ID.
     *
     * @param sessionId The ID of the session to load.
     * @return A list of deserialized [DrawingObject] models.
     */
    suspend fun loadSession(sessionId: ObjectId): List<DrawingObject> {
        val realm = Realm.open(WhiteboardApplication.config) // Open a fresh connection
        var objects: List<DrawingObject> = emptyList()
        try {
            realm.write { // Using write to ensure transactionally consistent data read.
                val session = query<WhiteboardSession>("id == $0", sessionId).first().find()
                objects = session?.objects
                    ?.sortedBy { it.order }
                    ?.mapNotNull { DrawingObjectSerializer.deserialize(it) }
                    ?: emptyList()
            }
        } finally {
            realm.close() // ALWAYS close the connection
        }
        return objects
    }

    /**
     * Retrieves all saved sessions from the database.
     *
     * @return A list of [WhiteboardSession] objects.
     */
    suspend fun getAllSessions(): List<WhiteboardSession> {
        val realm = Realm.open(WhiteboardApplication.config)
        var sessions: List<WhiteboardSession>
        try {
            val managedSessions = realm.query<WhiteboardSession>().find()
            // Deep copy is required because the original objects are tied to the Realm instance, which will be closed.
            sessions = realm.copyFromRealm(managedSessions)
        } finally {
            realm.close()
        }
        return sessions
    }

    /**
     * Helper function to determine the string type of a [DrawingObject].
     * @param obj The object whose type is to be determined.
     * @return A string identifier like "path", "shape", or "text".
     */
    private fun getObjectType(obj: DrawingObject): String {
        return when (obj) {
            is DrawingObject.PathObject -> "path"
            is DrawingObject.ShapeObject -> "shape"
            is TextObject -> "text"
        }
    }
}