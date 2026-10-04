package com.fitnessark.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitnessark.data.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val Context.dataStore by preferencesDataStore(name = "fitness_ark_prefs")

class PreferencesRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val unitSystemKey = stringPreferencesKey("unit_system")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
    }

    /** Which units values are shown and typed in. Stored data is always metric. */
    val unitSystem: Flow<UnitSystem> = context.dataStore.data.map { prefs ->
        prefs[unitSystemKey]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
            ?: UnitSystem.METRIC
    }

    suspend fun setUnitSystem(system: UnitSystem) {
        context.dataStore.edit { prefs -> prefs[unitSystemKey] = system.name }
    }
}
