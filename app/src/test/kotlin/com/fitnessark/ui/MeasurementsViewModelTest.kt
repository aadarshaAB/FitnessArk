package com.fitnessark.ui

import com.fitnessark.TestSupport
import com.fitnessark.TestSupport.noon
import com.fitnessark.await
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.ui.measurements.Metric
import com.fitnessark.ui.measurements.MeasurementsViewModel
import com.fitnessark.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MeasurementsViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MeasurementRepository
    private val originalZone = TimeZone.getDefault()

    @Before fun setUp() {
        // Chart x-offsets are whole days between entries; pin a DST-free zone so tests are stable.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = TestSupport.inMemoryDb()
        repo = MeasurementRepository(db.measurementDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(originalZone)
    }

    private fun viewModel() = MeasurementsViewModel(repo)

    @Test fun chart_labels_follow_real_dates_when_days_are_skipped() = runBlocking {
        // Entries 10, 9 and 5 days ago -> x offsets 0, 1 and 5 (a 4-day gap before the last).
        val dates = listOf(noon(10), noon(9), noon(5))
        dates.forEach { repo.saveMeasurement(MeasurementEntity(date = it, weight = 70f)) }
        val vm = viewModel()
        vm.uiState.await { it.measurements.size == 3 }

        val xs = vm.getChartData().map { it.x }
        val labels = vm.getChartLabels()

        assertEquals(listOf(0f, 1f, 5f), xs)
        assertEquals(DateUtils.formatDateShort(dates[0]), labels[0f])
        assertEquals(DateUtils.formatDateShort(dates[1]), labels[1f])
        assertEquals(DateUtils.formatDateShort(dates[2]), labels[5f])
        xs.forEach { x -> assertTrue("no label at x=$x", labels.containsKey(x)) }
    }

    @Test fun chart_ignores_entries_where_the_metric_was_left_blank() = runBlocking {
        repo.saveMeasurement(MeasurementEntity(date = noon(3), weight = 70f, waist = 0f))
        repo.saveMeasurement(MeasurementEntity(date = noon(2), weight = 69f, waist = 80f))
        val vm = viewModel()
        vm.uiState.await { it.measurements.size == 2 }

        assertEquals(2, vm.getChartData().size)                 // weight: both
        vm.changeMetric(Metric.WAIST)
        assertEquals(1, vm.getChartData().size)                 // waist: only the logged one
    }

    @Test fun deleting_shows_a_message_and_undo_restores_the_entry() = runBlocking {
        val entry = MeasurementEntity(date = noon(1), weight = 70f, notes = "keep me")
        repo.saveMeasurement(entry)
        val vm = viewModel()
        vm.uiState.await { it.measurements.size == 1 }

        vm.deleteMeasurement(entry.id)
        val afterDelete = vm.uiState.await { it.recentlyDeleted != null }
        assertEquals(entry, afterDelete.recentlyDeleted)
        assertEquals("Measurement deleted", afterDelete.snackbarMessage)
        assertEquals(0, repo.getMeasurementCount())

        vm.undoDelete()
        vm.uiState.await { it.measurements.size == 1 && it.recentlyDeleted == null }
        assertEquals(entry, repo.getLatestMeasurement())
    }

    @Test fun undo_does_nothing_once_the_snackbar_is_cleared() = runBlocking {
        val entry = MeasurementEntity(date = noon(1), weight = 70f)
        repo.saveMeasurement(entry)
        val vm = viewModel()
        vm.uiState.await { it.measurements.size == 1 }
        vm.deleteMeasurement(entry.id)
        vm.uiState.await { it.recentlyDeleted != null }

        vm.clearSnackbar()
        vm.undoDelete()

        assertNull(vm.uiState.value.recentlyDeleted)
        assertEquals(0, repo.getMeasurementCount())
    }
}
