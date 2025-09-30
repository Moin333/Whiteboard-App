package com.example.whiteboardapp

import android.app.Application
import com.example.whiteboardapp.data.db.DrawingObjectRealm
import com.example.whiteboardapp.data.db.WhiteboardSession
import io.realm.kotlin.RealmConfiguration

class WhiteboardApplication : Application() {
    companion object {
        lateinit var config: RealmConfiguration
            private set
    }

    override fun onCreate() {
        super.onCreate()
        config = RealmConfiguration.Builder(
            schema = setOf(
                WhiteboardSession::class,
                DrawingObjectRealm::class
            )
        )
            .name("whiteboard.realm")
            .directory(filesDir.path)
            .build()
    }
}