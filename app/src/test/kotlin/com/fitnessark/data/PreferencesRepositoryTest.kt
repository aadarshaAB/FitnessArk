package com.fitnessark.data

import com.fitnessark.TestSupport
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.data.repository.PreferencesRepository
import com.fitnessark.data.repository.ReminderSettings
import com.fitnessark.data.repository.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesRepositoryTest {

    private val repo = PreferencesRepository(TestSupport.context())

    @Test fun theme_defaults_to_dark_and_is_saved() = runBlocking {
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
    }

    @Test fun units_default_to_metric_and_are_saved() = runBlocking {
        assertEquals(UnitSystem.METRIC, repo.unitSystem.first())
        repo.setUnitSystem(UnitSystem.IMPERIAL)
        assertEquals(UnitSystem.IMPERIAL, repo.unitSystem.first())
    }

    @Test fun reminder_defaults_to_off_at_8pm_and_is_saved() = runBlocking {
        val defaults = repo.reminderSettings.first()
        assertEquals(false, defaults.enabled)
        assertEquals(20, defaults.hour)
        assertEquals(0, defaults.minute)

        repo.setReminderSettings(ReminderSettings(enabled = true, hour = 7, minute = 30))

        val saved = repo.reminderSettings.first()
        assertEquals(true, saved.enabled)
        assertEquals(7, saved.hour)
        assertEquals(30, saved.minute)
    }
}
