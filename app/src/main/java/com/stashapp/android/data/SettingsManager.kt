package com.stashapp.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val GLOBAL_LEAD_DAYS = intPreferencesKey("global_lead_days")
        val IS_CATALOG_IMPORTED = booleanPreferencesKey("is_catalog_imported")
        private val ACTIVE_LOCATION_ID = androidx.datastore.preferences.core.stringPreferencesKey("active_location_id")
    }

    // Default to 2 days before expiration
    val globalLeadDays: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GLOBAL_LEAD_DAYS] ?: 2
    }

    suspend fun setGlobalLeadDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[GLOBAL_LEAD_DAYS] = days
        }
    }

    val isCatalogImported: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_CATALOG_IMPORTED] ?: false
    }

    suspend fun setCatalogImported(imported: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_CATALOG_IMPORTED] = imported
        }
    }

    val activeLocationId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_LOCATION_ID]
    }

    suspend fun setActiveLocationId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id == null) {
                preferences.remove(ACTIVE_LOCATION_ID)
            } else {
                preferences[ACTIVE_LOCATION_ID] = id
            }
        }
    }
}
