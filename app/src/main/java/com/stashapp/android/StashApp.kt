package com.stashapp.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.stashapp.shared.data.SqlDelightInventoryRepository
import com.stashapp.shared.db.StashDatabase

class StashApp : Application() {
    
    // Singleton repository to be shared across Activity and Workers
    val repository: SqlDelightInventoryRepository by lazy {
        val driver = AndroidSqliteDriver(
            schema = StashDatabase.Schema,
            context = applicationContext,
            name = "stashapp.db",
            callback = object : AndroidSqliteDriver.Callback(StashDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.setForeignKeyConstraintsEnabled(true)
                    db.enableWriteAheadLogging()
                    // Turbo settings for faster writes (Synchronous = NORMAL is enough)
                    db.execSQL("PRAGMA synchronous = NORMAL")
                }
            }
        )
        SqlDelightInventoryRepository(StashDatabase(driver))
    }

    // Shared state to coordinate background work vs heavy import
    var isImporting by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
    }
}
