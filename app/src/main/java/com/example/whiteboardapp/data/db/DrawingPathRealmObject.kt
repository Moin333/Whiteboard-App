package com.example.whiteboardapp.data.db

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

// Represents a single point (x, y) for Realm storage
class DrawingPoint : EmbeddedRealmObject {
    var x: Float = 0f
    var y: Float = 0f
}

// Persistable version of a drawing path
class DrawingPathRealmObject : RealmObject {
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var color: Int = 0
    var strokeWidth: Float = 0f
    var points: RealmList<DrawingPoint> = realmListOf()
}