package com.fitnessark

import android.app.Application
import com.fitnessark.data.repository.PreferencesRepository
import com.fitnessark.di.appModule
import com.fitnessark.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.java.KoinJavaComponent.inject

class FitnessArkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FitnessArkApp)
            modules(appModule)
        }
        // Re-arms the reminder on app start (e.g. after an app update, which can drop work that
        // referenced a worker class since rebuilt) if it was left on; a no-op most of the time.
        CoroutineScope(Dispatchers.Default).launch {
            val preferencesRepo by inject<PreferencesRepository>(PreferencesRepository::class.java)
            val settings = preferencesRepo.reminderSettings.first()
            if (settings.enabled) ReminderScheduler.schedule(this@FitnessArkApp, settings.hour, settings.minute)
        }
    }
}
