package com.fitnessark.ui

import com.fitnessark.TestSupport
import com.fitnessark.TestSupport.noon
import com.fitnessark.await
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.ui.dashboard.DashboardViewModel
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MeasurementRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = TestSupport.inMemoryDb()
        repo = MeasurementRepository(db.measurementDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = DashboardViewModel(
        repo, PhotoRepository(db.photoDao(), ImageCompressor(), TestSupport.context())
    )

    @Test fun seven_day_change_ignores_entries_with_blank_weight() = runBlocking {
        repo.saveMeasurement(MeasurementEntity(date = noon(5), weight = 80f))
        repo.saveMeasurement(MeasurementEntity(date = noon(3), weight = null))   // check-in without weight
        repo.saveMeasurement(MeasurementEntity(date = noon(0), weight = 78f))

        val state = viewModel().uiState.await { !it.isLoading }

        assertEquals(-2f, state.weightChangeLast7Days!!, 0.001f)   // not -80
        assertEquals(78f, state.todayWeight!!, 0.001f)
    }

    @Test fun no_change_is_reported_with_fewer_than_two_weights() = runBlocking {
        repo.saveMeasurement(MeasurementEntity(date = noon(0), weight = 78f))
        repo.saveMeasurement(MeasurementEntity(date = noon(2), weight = null))

        val state = viewModel().uiState.await { !it.isLoading }

        assertNull(state.weightChangeLast7Days)
    }

    @Test fun dashboard_refreshes_itself_when_data_changes_elsewhere() = runBlocking {
        val vm = viewModel()
        vm.uiState.await { !it.isLoading }

        repo.saveMeasurement(MeasurementEntity(date = noon(0), weight = 80f))   // e.g. a full check-in
        val afterSave = vm.uiState.await { it.todayWeight == 80f }
        assertEquals(1, afterSave.measurementCount)
        assertEquals(1, afterSave.streakDays)

        repo.deleteMeasurement(repo.getMeasurementForDay(noon(0))!!.id)
        val afterDelete = vm.uiState.await { it.todayWeight == null && it.measurementCount == 0 }
        assertEquals(0, afterDelete.streakDays)
    }

    @Test fun logging_weight_twice_in_a_day_updates_todays_entry() = runBlocking {
        val vm = viewModel()
        vm.uiState.await { !it.isLoading }

        vm.updateWeight(70f)
        vm.uiState.await { it.todayWeight == 70f }
        vm.updateWeight(71f)
        vm.uiState.await { it.todayWeight == 71f }

        assertEquals(1, repo.getMeasurementCount())
    }

    @Test fun weekly_average_covers_the_last_seven_days_and_skips_blank_weights() = runBlocking {
        repo.saveMeasurement(MeasurementEntity(date = noon(8), weight = 100f))   // too old
        repo.saveMeasurement(MeasurementEntity(date = noon(4), weight = 80f))
        repo.saveMeasurement(MeasurementEntity(date = noon(2), weight = null))
        repo.saveMeasurement(MeasurementEntity(date = noon(0), weight = 78f))

        val state = viewModel().uiState.await { !it.isLoading }

        assertEquals(79f, state.weeklyAverageWeight!!, 0.001f)
    }

    @Test fun weekly_average_is_null_without_a_recent_weight() = runBlocking {
        repo.saveMeasurement(MeasurementEntity(date = noon(20), weight = 80f))

        assertNull(viewModel().uiState.await { !it.isLoading }.weeklyAverageWeight)
    }
}
