package com.fitnessark.ui

import com.fitnessark.TestSupport
import com.fitnessark.TestSupport.noon
import com.fitnessark.await
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.ui.checkin.CheckinViewModel
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CheckinViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var measurements: MeasurementRepository
    private val context = TestSupport.context()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = TestSupport.inMemoryDb(context)
        measurements = MeasurementRepository(db.measurementDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(date: Long) = CheckinViewModel(
        measurements,
        PhotoRepository(db.photoDao(), ImageCompressor(), context),
        date
    )

    @Test fun saving_the_same_day_twice_keeps_one_row() = runBlocking {
        val date = noon(0)

        viewModel(date).apply {
            update("weight", "70")
            save(context)
            uiState.await { it.saved }
        }
        viewModel(date).apply {
            uiState.await { it.weight == "70.0" }          // pre-filled from the first save
            update("weight", "71")
            save(context)
            uiState.await { it.saved }
        }

        assertEquals(1, measurements.getMeasurementCount())
        assertEquals(71f, measurements.getMeasurementForDay(date)!!.weight, 0.001f)
    }

    @Test fun past_date_is_prefilled_with_that_days_values_not_the_latest() = runBlocking {
        val past = noon(5)
        measurements.saveMeasurement(MeasurementEntity(date = past, weight = 80f, waist = 90f, notes = "then"))
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 75f))

        val state = viewModel(past).uiState.await { it.weight.isNotEmpty() }

        assertEquals("80.0", state.weight)
        assertEquals("90.0", state.waist)
        assertEquals("then", state.notes)
    }

    @Test fun saving_a_past_date_does_not_touch_other_days() = runBlocking {
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 75f))

        viewModel(noon(4)).apply {
            update("weight", "82")
            save(context)
            uiState.await { it.saved }
        }

        assertEquals(2, measurements.getMeasurementCount())
        assertEquals(75f, measurements.getMeasurementForDay(noon(0))!!.weight, 0.001f)
        assertEquals(82f, measurements.getMeasurementForDay(noon(4))!!.weight, 0.001f)
    }

    @Test fun comma_decimal_is_saved_correctly() = runBlocking {
        viewModel(noon(0)).apply {
            update("weight", "72,5")
            save(context)
            uiState.await { it.saved }
        }
        assertEquals(72.5f, measurements.getMeasurementForDay(noon(0))!!.weight, 0.001f)
    }

    @Test fun invalid_input_blocks_the_save_and_shows_an_error() = runBlocking {
        val vm = viewModel(noon(0))
        vm.update("weight", "5")          // below the 20 kg minimum
        vm.save(context)

        val state = vm.uiState.await { it.errorMessage != null }
        assertEquals(false, state.saved)
        assertNotNull(vm.fieldError("weight", state))
        assertNull(vm.fieldError("chest", state))
        assertEquals(0, measurements.getMeasurementCount())
    }

    @Test fun blank_fields_are_stored_as_zero() = runBlocking {
        viewModel(noon(0)).apply {
            update("weight", "70")
            save(context)
            uiState.await { it.saved }
        }
        val saved = measurements.getMeasurementForDay(noon(0))!!
        assertEquals(0f, saved.chest, 0f)
        assertEquals(0f, saved.thighs, 0f)
    }
}
