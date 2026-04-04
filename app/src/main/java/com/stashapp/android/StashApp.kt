package com.stashapp.android

import android.app.Application
import java.util.concurrent.atomic.AtomicBoolean
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
                    // Some Android versions require using query() for pragmas that return a result
                    db.query("PRAGMA busy_timeout = 5000").close()
                }
            }
        )
        SqlDelightInventoryRepository(StashDatabase(driver), driver)
    }

    // Shared state to coordinate background work vs heavy import
    val isImporting = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
    }
}
