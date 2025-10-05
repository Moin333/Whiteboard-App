package com.example.whiteboardapp

import android.app.Application
import com.example.whiteboardapp.data.db.DrawingObjectRealm
import com.example.whiteboardapp.data.db.WhiteboardSession
import io.realm.kotlin.RealmConfiguration

/**
 * The main Application class for the whiteboard app.
 * Its primary responsibility is to set up the Realm database configuration
 * once when the application starts.
 */
class WhiteboardApplication : Application() {
    companion object {
        // The global RealmConfiguration accessible from anywhere in the app.
        lateinit var config: RealmConfiguration
            private set
    }

    override fun onCreate() {
        super.onCreate()
        // Define the database schema and build the configuration object.
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