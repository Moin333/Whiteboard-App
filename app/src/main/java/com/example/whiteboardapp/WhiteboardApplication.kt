package com.example.whiteboardapp

import android.app.Application
import com.example.whiteboardapp.data.db.DrawingPathRealmObject
import com.example.whiteboardapp.data.db.DrawingPoint
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

class WhiteboardApplication : Application() {
    companion object {
        lateinit var realm: Realm
    }

    override fun onCreate() {
        super.onCreate()
        val config = RealmConfiguration.create(
            schema = setOf(
                DrawingPathRealmObject::class,
                DrawingPoint::class
            )
        )
        realm = Realm.open(config)
    }
}