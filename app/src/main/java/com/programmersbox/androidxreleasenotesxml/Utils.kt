package com.programmersbox.androidxreleasenotesxml

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("androidx")

val LAST_UPDATE = longPreferencesKey("last_update")
val Context.lastUpdate get() = dataStore.data.map { it[LAST_UPDATE] ?: 0 }

suspend fun <T> Context.updatePref(key: Preferences.Key<T>, value: T) = dataStore.edit { it[key] = value }