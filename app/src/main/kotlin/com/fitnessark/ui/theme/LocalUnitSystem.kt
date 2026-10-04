package com.fitnessark.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.fitnessark.data.model.UnitSystem

/** The units values are shown in; provided once in `MainActivity` from the saved setting. */
val LocalUnitSystem = compositionLocalOf { UnitSystem.METRIC }
