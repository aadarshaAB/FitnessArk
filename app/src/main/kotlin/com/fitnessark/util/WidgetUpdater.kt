package com.fitnessark.util

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.fitnessark.ui.widget.FitnessArkWidget

/** Refreshes any placed home-screen widget (F6) after a measurement is saved or deleted. */
class WidgetUpdater(private val context: Context) {
    suspend fun refresh() {
        FitnessArkWidget().updateAll(context)
    }
}
