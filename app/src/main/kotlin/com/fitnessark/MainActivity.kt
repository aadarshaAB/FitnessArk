package com.fitnessark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.data.repository.PreferencesRepository
import com.fitnessark.data.repository.ThemeMode
import com.fitnessark.ui.navigation.FitnessArkNavHost
import com.fitnessark.ui.theme.FitnessArkTheme
import com.fitnessark.ui.theme.LocalUnitSystem
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferencesRepo = koinInject<PreferencesRepository>()
            val themeMode by preferencesRepo.themeMode.collectAsState(initial = ThemeMode.DARK)
            val isDarkTheme = when (themeMode) {
                ThemeMode.DARK   -> true
                ThemeMode.LIGHT  -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val unitSystem by preferencesRepo.unitSystem.collectAsState(initial = UnitSystem.METRIC)
            FitnessArkTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalUnitSystem provides unitSystem) {
                    FitnessArkNavHost()
                }
            }
        }
    }
}
