package com.stashapp.android

import android.app.Application
import java.util.concurrent.atomic.AtomicBoolean
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.stashapp.shared.data.SqlDelightInventoryRepository
import com.stashapp.shared.data.SqlDelightRecipeRepository
import com.stashapp.shared.db.StashDatabase

class StashApp : Application() {
    
    // Singleton repository to be shared across Activity and Workers
    private val sqliteDriver: AndroidSqliteDriver by lazy {
        AndroidSqliteDriver(
            schema = StashDatabase.Schema,
            context = applicationContext,
            name = "stashapp.db",
            callback = object : AndroidSqliteDriver.Callback(StashDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.setForeignKeyConstraintsEnabled(true)
                    db.enableWriteAheadLogging()
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    db.query("PRAGMA busy_timeout = 5000").close()
                }
            }
        )
    }

    private val database: StashDatabase by lazy {
        StashDatabase(sqliteDriver)
    }

    val repository: SqlDelightInventoryRepository by lazy {
        SqlDelightInventoryRepository(database, sqliteDriver)
    }

    val recipeRepository: SqlDelightRecipeRepository by lazy {
        SqlDelightRecipeRepository(database)
    }

    // Shared state to coordinate background work vs heavy import
    val isImporting = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
    }
}
