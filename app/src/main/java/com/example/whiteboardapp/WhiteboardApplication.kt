package com.example.whiteboardapp

import android.app.Application
import com.example.whiteboardapp.data.db.DrawingObjectRealm
import com.example.whiteboardapp.data.db.WhiteboardSession
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.migration.AutomaticSchemaMigration

/**
 * The main Application class for the whiteboard app.
 * Its primary responsibility is to set up the Realm database configuration
 * once when the application starts.
 *
 * ## Schema versioning
 * [SCHEMA_VERSION] must be incremented any time a field is added, removed,
 * or renamed in [WhiteboardSession] or [DrawingObjectRealm]. A corresponding
 * migration branch must be added inside the [AutomaticSchemaMigration] block.
 *
 * Migration history:
 *   v1 — Baseline. Establishes version tracking on all existing databases.
 *        No structural changes.
 */
class WhiteboardApplication : Application() {

    companion object {
        /**
         * Current Realm schema version.
         * Increment this constant (and add a migration branch below) whenever
         * the schema of [WhiteboardSession] or [DrawingObjectRealm] changes.
         */
        const val SCHEMA_VERSION = 1L

        /** The global RealmConfiguration accessible from anywhere in the app. */
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
            .schemaVersion(SCHEMA_VERSION)
            .migration(AutomaticSchemaMigration { context ->
                // AutomaticSchemaMigration handles the most common cases without
                // manual code: adding nullable fields, adding fields that have
                // Realm-supported defaults, and removing fields.
                //
                // Add explicit migration logic here only when automatic migration
                // is insufficient (e.g. data transformation, renaming a field).
                //
                // Template for future migrations:
                //
                // val oldVersion = context.oldRealm.schemaVersion()
                // if (oldVersion < 2L) {
                //     // v1 → v2: example — backfill a new "isLocked" field to false
                //     context.enumerate("WhiteboardSession") { oldObj, newObj ->
                //         newObj?.set("isLocked", false)
                //     }
                // }
                // if (oldVersion < 3L) {
                //     // v2 → v3: next migration goes here
                // }
                //
                // v1: Baseline — no structural changes, no action required.
            })
            .build()
    }
}