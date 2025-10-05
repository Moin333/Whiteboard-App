package com.example.whiteboardapp.data.db

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

/**
 * Represents a single whiteboard session stored in the Realm database.
 * Each session has a unique ID, a name, timestamps, and a list of all drawing objects.
 */
class WhiteboardSession : RealmObject {
    // The unique identifier for this session.
    @PrimaryKey
    var id: ObjectId = ObjectId()

    // The user-defined name of the session. Defaults to "Untitled Session".
    var name: String = "Untitled Session"

    // Timestamp (in milliseconds) of when the session was first created.
    var createdAt: Long = System.currentTimeMillis()

    // Timestamp (in milliseconds) of the last modification.
    var lastModified: Long = System.currentTimeMillis()

    // A byte array holding a small thumbnail image of the whiteboard content. Can be null.
    var thumbnail: ByteArray? = null

    // A list of [DrawingObjectRealm] instances that make up the content of this session.
    var objects: RealmList<DrawingObjectRealm> = realmListOf()
}

/**
 * Represents a single drawing object within a session, optimized for Realm storage.
 * Since Realm cannot store complex objects like Android's Path or Paint directly,
 * the object's data is serialized into a JSON string.
 */
class DrawingObjectRealm : RealmObject {
    // The unique identifier for this drawing object.
    @PrimaryKey
    var id: ObjectId = ObjectId()

    // A string identifier for the object's type (e.g., "path", "shape", "text").
    var type: String = ""

    // A JSON string containing all the serialized data for this object (e.g., coordinates, colors, text content).
    var data: String = ""

    // The drawing order of the object on the canvas (z-index).
    var order: Int = 0
}