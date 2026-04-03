package com.stashapp.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val GLOBAL_LEAD_DAYS = intPreferencesKey("global_lead_days")
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
}
