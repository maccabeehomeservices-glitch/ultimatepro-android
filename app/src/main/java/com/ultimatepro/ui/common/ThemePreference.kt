package com.ultimatepro.ui.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class ThemePreference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DARK_THEME = booleanPreferencesKey("dark_theme")

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_THEME] ?: false
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = dark }
    }
}
