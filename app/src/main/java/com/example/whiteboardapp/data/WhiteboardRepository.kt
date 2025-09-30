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

class WhiteboardRepository {
    suspend fun saveOrUpdateSession(sessionId: ObjectId?, name: String, objects: List<DrawingObject>): ObjectId {
        if (objects.isEmpty()) {
            // Return a new ObjectId or the existing one if objects are empty to avoid errors
            return sessionId ?: ObjectId()
        }

        val realm = Realm.open(WhiteboardApplication.config)
        lateinit var savedSessionId: ObjectId

        val realmObjects = objects.mapIndexed { index, obj ->
            DrawingObjectRealm().apply {
                this.type = getObjectType(obj)
                this.data = DrawingObjectSerializer.serialize(obj)
                this.order = index
            }
        }.toRealmList()

        try {
            realm.write {
                val sessionToSave = if (sessionId == null) {
                    WhiteboardSession().apply { this.name = name }
                } else {
                    query<WhiteboardSession>("id == $0", sessionId).first().find()
                        ?: WhiteboardSession().apply { this.id = sessionId }
                }
                sessionToSave.name = name

                sessionToSave.lastModified = System.currentTimeMillis()
                sessionToSave.objects.clear()
                sessionToSave.objects.addAll(realmObjects)

                val managedSession = copyToRealm(sessionToSave, UpdatePolicy.ALL)
                savedSessionId = managedSession.id // Get the ID of the saved object
            }
        } finally {
            realm.close()
        }
        return savedSessionId
    }

    // Load a specific session
    suspend fun loadSession(sessionId: ObjectId): List<DrawingObject> {
        val realm = Realm.open(WhiteboardApplication.config) // Open a fresh connection
        var objects: List<DrawingObject> = emptyList()
        try {
            realm.write { // Using write to ensure latest data
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

    // Get all sessions as a Flow for reactive updates
    fun getAllSessions(): List<WhiteboardSession> {
        val realm = Realm.open(WhiteboardApplication.config)
        var sessions: List<WhiteboardSession>
        try {
            val managedSessions = realm.query<WhiteboardSession>().find()
            // We need to make deep copies because the realm instance will be closed
            sessions = realm.copyFromRealm(managedSessions)
        } finally {
            realm.close()
        }
        return sessions
    }

    // Helper to get a string type for an object
    private fun getObjectType(obj: DrawingObject): String {
        return when (obj) {
            is DrawingObject.PathObject -> "path"
            is DrawingObject.ShapeObject -> "shape"
            is TextObject -> "text"
        }
    }
}