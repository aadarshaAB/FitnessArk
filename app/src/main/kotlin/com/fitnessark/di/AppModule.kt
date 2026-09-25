package com.fitnessark.di

import androidx.room.Room
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.data.repository.PreferencesRepository
import com.fitnessark.ui.checkin.CheckinViewModel
import com.fitnessark.ui.dashboard.DashboardViewModel
import com.fitnessark.ui.measurements.MeasurementsViewModel
import com.fitnessark.ui.photos.PhotoTimelineViewModel
import com.fitnessark.ui.settings.SettingsViewModel
import com.fitnessark.util.ImageCompressor
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
        ).fallbackToDestructiveMigration().build()
    }

    // DAOs
    single { get<AppDatabase>().measurementDao() }
    single { get<AppDatabase>().photoDao() }

    // Utilities
    single { ImageCompressor() }
    single { ZipUtils() }

    // Repositories
    single { MeasurementRepository(get()) }
    single { PhotoRepository(get(), get(), androidContext()) }
    single { PreferencesRepository(androidContext()) }

    // ViewModels
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { MeasurementsViewModel(get()) }
    viewModel { PhotoTimelineViewModel(get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get(), get(), get()) }
    viewModel { params -> CheckinViewModel(get(), get(), params.get()) }
}
