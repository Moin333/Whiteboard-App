package com.example.whiteboardapp.data.db

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class WhiteboardSession : RealmObject {
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var name: String = "Untitled Session"
    var createdAt: Long = System.currentTimeMillis()
    var lastModified: Long = System.currentTimeMillis()
    var thumbnail: ByteArray? = null
    var objects: RealmList<DrawingObjectRealm> = realmListOf()
}

class DrawingObjectRealm : RealmObject {
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var type: String = ""
    var data: String = ""
    var order: Int = 0
}