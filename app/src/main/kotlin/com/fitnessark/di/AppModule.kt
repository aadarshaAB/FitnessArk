package com.fitnessark.di

import androidx.room.Room
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.repository.BackupRepository
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.data.repository.PreferencesRepository
import com.fitnessark.ui.checkin.CheckinViewModel
import com.fitnessark.ui.dashboard.DashboardViewModel
import com.fitnessark.ui.measurements.MeasurementsViewModel
import com.fitnessark.ui.photos.PhotoTimelineViewModel
import com.fitnessark.ui.settings.SettingsViewModel
import com.fitnessark.util.ImageCompressor
import com.fitnessark.util.WidgetUpdater
import com.fitnessark.util.ZipUtils
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // Database
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addMigrations(*AppDatabase.MIGRATIONS).build()
    }

    // DAOs
    single { get<AppDatabase>().measurementDao() }
    single { get<AppDatabase>().photoDao() }

    // Utilities
    single { ImageCompressor() }
    single { ZipUtils() }
    single { WidgetUpdater(androidContext()) }

    // Repositories
    single { MeasurementRepository(get()) }
    single { PhotoRepository(get(), get(), androidContext()) }
    single { BackupRepository(get(), androidContext()) }
    single { PreferencesRepository(androidContext()) }

    // ViewModels
    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { MeasurementsViewModel(get()) }
    viewModel { PhotoTimelineViewModel(get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get(), get(), get(), get()) }
    viewModel { params ->
        // Q21: get() here resolves to a real SavedStateHandle, not a plain DI binding —
        // koin-androidx-compose's viewmodel factory creates one from the screen's
        // NavBackStackEntry CreationExtras whenever a SavedStateHandle constructor param is
        // requested this way, and that handle is what Android's Navigation component saves and
        // restores across a full process kill, not just a configuration change.
        // params.get() still supplies the runtime `date` arg (see CheckinViewModel kdoc).
        CheckinViewModel(get(), get(), get<PreferencesRepository>().unitSystem, get(), get(), params.get())
    }
}
