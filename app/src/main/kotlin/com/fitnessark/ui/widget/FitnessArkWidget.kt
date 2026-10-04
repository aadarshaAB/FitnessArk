package com.fitnessark.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fitnessark.MainActivity
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent.inject

/**
 * Home-screen widget (F6): today's weight, the streak, and a "Log weight" button.
 * Reads straight from the repositories (no ViewModel; a widget composes outside any screen) and
 * updates itself whenever the app writes a measurement (see `WidgetUpdater`).
 */
class FitnessArkWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val measurementRepo by inject<MeasurementRepository>(MeasurementRepository::class.java)
        val preferencesRepo by inject<PreferencesRepository>(PreferencesRepository::class.java)

        val today = measurementRepo.getMeasurementForDay(System.currentTimeMillis())
        val streak = measurementRepo.calculateStreak()
        val unitSystem = preferencesRepo.unitSystem.first()

        provideContent {
            WidgetContent(weight = today?.weight, streak = streak, unitSystem = unitSystem)
        }
    }
}

@Composable
private fun WidgetContent(weight: Float?, streak: Int, unitSystem: UnitSystem) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "$streak day streak",
                    style = TextStyle(color = ColorProvider(Color(0xFFBDBDBD)), fontSize = 12.sp)
                )
                Text(
                    weight?.let { Metric.WEIGHT.format(it, unitSystem) } ?: "Not logged",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF2A2A2A))
                .clickable(actionRunCallback<LogWeightAction>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Log weight", style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp))
        }
    }
}

/**
 * The widget's own weight is a placeholder (0, for an untouched slot): the actual logging still
 * needs a number from the user, so this opens the app's "Log Weight Only" dialog rather than
 * saving a guessed value. See `DashboardScreen`'s weight dialog for the save path itself.
 */
class LogWeightAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_WEIGHT_DIALOG, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }
}

class FitnessArkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FitnessArkWidget()
}
