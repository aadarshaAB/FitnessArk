package com.fitnessark.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitnessark.data.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** The daily check-in reminder (F5): whether it's on, and the local time it fires at. */
data class ReminderSettings(val enabled: Boolean, val hour: Int, val minute: Int)

private val Context.dataStore by preferencesDataStore(name = "fitness_ark_prefs")

class PreferencesRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val unitSystemKey = stringPreferencesKey("unit_system")
    private val reminderEnabledKey = booleanPreferencesKey("reminder_enabled")
    private val reminderHourKey = intPreferencesKey("reminder_hour")
    private val reminderMinuteKey = intPreferencesKey("reminder_minute")

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

    /** Defaults to off at 8:00 PM, so turning the app on never starts sending notifications. */
    val reminderSettings: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[reminderEnabledKey] ?: false,
            hour    = prefs[reminderHourKey] ?: 20,
            minute  = prefs[reminderMinuteKey] ?: 0
        )
    }

    suspend fun setReminderSettings(settings: ReminderSettings) {
        context.dataStore.edit { prefs ->
            prefs[reminderEnabledKey] = settings.enabled
            prefs[reminderHourKey] = settings.hour
            prefs[reminderMinuteKey] = settings.minute
        }
    }
}
