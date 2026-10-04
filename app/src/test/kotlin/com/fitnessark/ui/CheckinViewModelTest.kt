package com.fitnessark.ui

import com.fitnessark.TestSupport
import com.fitnessark.TestSupport.noon
import com.fitnessark.await
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.UnitSystem
import com.fitnessark.data.repository.MeasurementRepository
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.ui.checkin.CheckinViewModel
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

    private fun viewModel(date: Long, units: UnitSystem = UnitSystem.METRIC) = CheckinViewModel(
        measurements,
        PhotoRepository(db.photoDao(), ImageCompressor(), context),
        flowOf(units),
        date
    )

    @Test fun saving_the_same_day_twice_keeps_one_row() = runBlocking {
        val date = noon(0)

        viewModel(date).apply {
            update(Metric.WEIGHT, "70")
            save()
            uiState.await { it.saved }
        }
        viewModel(date).apply {
            uiState.await { it.text(Metric.WEIGHT) == "70.0" }          // pre-filled from the first save
            update(Metric.WEIGHT, "71")
            save()
            uiState.await { it.saved }
        }

        assertEquals(1, measurements.getMeasurementCount())
        assertEquals(71f, measurements.getMeasurementForDay(date)!!.weight!!, 0.001f)
    }

    @Test fun past_date_is_prefilled_with_that_days_values_not_the_latest() = runBlocking {
        val past = noon(5)
        measurements.saveMeasurement(MeasurementEntity(date = past, weight = 80f, waist = 90f, notes = "then"))
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 75f))

        val state = viewModel(past).uiState.await { it.text(Metric.WEIGHT).isNotEmpty() }

        assertEquals("80.0", state.text(Metric.WEIGHT))
        assertEquals("90.0", state.text(Metric.WAIST))
        assertEquals("then", state.notes)
    }

    @Test fun saving_a_past_date_does_not_touch_other_days() = runBlocking {
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 75f))

        viewModel(noon(4)).apply {
            update(Metric.WEIGHT, "82")
            save()
            uiState.await { it.saved }
        }

        assertEquals(2, measurements.getMeasurementCount())
        assertEquals(75f, measurements.getMeasurementForDay(noon(0))!!.weight!!, 0.001f)
        assertEquals(82f, measurements.getMeasurementForDay(noon(4))!!.weight!!, 0.001f)
    }

    @Test fun comma_decimal_is_saved_correctly() = runBlocking {
        viewModel(noon(0)).apply {
            update(Metric.WEIGHT, "72,5")
            save()
            uiState.await { it.saved }
        }
        assertEquals(72.5f, measurements.getMeasurementForDay(noon(0))!!.weight!!, 0.001f)
    }

    @Test fun invalid_input_blocks_the_save_and_shows_an_error() = runBlocking {
        val vm = viewModel(noon(0))
        vm.update(Metric.WEIGHT, "5")          // below the 20 kg minimum
        vm.save()

        val state = vm.uiState.await { it.errorMessage != null }
        assertEquals(false, state.saved)
        assertNotNull(vm.fieldError(Metric.WEIGHT, state))
        assertNull(vm.fieldError(Metric.CHEST, state))
        assertEquals(0, measurements.getMeasurementCount())
    }

    @Test fun blank_fields_are_stored_as_null() = runBlocking {
        viewModel(noon(0)).apply {
            update(Metric.WEIGHT, "70")
            save()
            uiState.await { it.saved }
        }
        val saved = measurements.getMeasurementForDay(noon(0))!!
        assertEquals(70f, saved.weight!!, 0.001f)
        assertNull(saved.chest)
        assertNull(saved.thighs)
    }

    @Test fun clearing_a_field_and_saving_makes_it_not_logged_again() = runBlocking {
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 70f, waist = 80f))

        viewModel(noon(0)).apply {
            uiState.await { it.text(Metric.WAIST) == "80.0" }
            update(Metric.WAIST, "")
            save()
            uiState.await { it.saved }
        }

        assertNull(measurements.getMeasurementForDay(noon(0))!!.waist)
    }

    @Test fun imperial_input_is_stored_as_metric() = runBlocking {
        viewModel(noon(0), UnitSystem.IMPERIAL).apply {
            uiState.await { it.unitSystem == UnitSystem.IMPERIAL }
            update(Metric.WEIGHT, "160")
            update(Metric.WAIST, "32")
            save()
            uiState.await { it.saved }
        }
        val saved = measurements.getMeasurementForDay(noon(0))!!
        assertEquals(72.57f, saved.weight!!, 0.01f)     // 160 lb
        assertEquals(81.28f, saved.waist!!, 0.01f)      // 32 in
    }

    @Test fun imperial_prefill_is_shown_in_pounds_and_inches() = runBlocking {
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 72.5f, waist = 81.28f))

        val state = viewModel(noon(0), UnitSystem.IMPERIAL).uiState.await { it.text(Metric.WEIGHT).isNotEmpty() }

        assertEquals("159.8", state.text(Metric.WEIGHT))
        assertEquals("32.0", state.text(Metric.WAIST))
    }

    @Test fun resaving_an_untouched_field_in_imperial_does_not_change_the_stored_value() = runBlocking {
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 72.5f, waist = 81f))

        viewModel(noon(0), UnitSystem.IMPERIAL).apply {
            uiState.await { it.text(Metric.WEIGHT).isNotEmpty() }
            update(Metric.WAIST, "33")                    // only the waist is edited
            save()
            uiState.await { it.saved }
        }

        val saved = measurements.getMeasurementForDay(noon(0))!!
        assertEquals(72.5f, saved.weight!!, 0.0001f)      // not 72.49 from a 159.8 lb round trip
        assertEquals(83.82f, saved.waist!!, 0.01f)
    }

    @Test fun imperial_range_check_uses_the_converted_limits() = runBlocking {
        val vm = viewModel(noon(0), UnitSystem.IMPERIAL)
        vm.uiState.await { it.unitSystem == UnitSystem.IMPERIAL }
        vm.update(Metric.WEIGHT, "30")                    // 13.6 kg: below the 20 kg minimum
        assertNotNull(vm.fieldError(Metric.WEIGHT, vm.uiState.value))
        vm.update(Metric.WEIGHT, "150")
        assertNull(vm.fieldError(Metric.WEIGHT, vm.uiState.value))
    }

    @Test fun changing_the_date_loads_that_days_entry_and_saves_to_it() = runBlocking {
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 75f))
        measurements.saveMeasurement(MeasurementEntity(date = noon(3), weight = 78f, notes = "earlier"))

        val vm = viewModel(noon(0))
        vm.uiState.await { it.text(Metric.WEIGHT) == "75.0" }

        vm.setDate(noon(3))
        val state = vm.uiState.await { it.text(Metric.WEIGHT) == "78.0" }
        assertEquals("earlier", state.notes)
        assertEquals(noon(3), state.date)

        vm.update(Metric.WEIGHT, "77")
        vm.save()
        vm.uiState.await { it.saved }

        assertEquals(2, measurements.getMeasurementCount())
        assertEquals(77f, measurements.getMeasurementForDay(noon(3))!!.weight!!, 0.001f)
        assertEquals(75f, measurements.getMeasurementForDay(noon(0))!!.weight!!, 0.001f)
    }

    @Test fun changing_to_a_day_with_no_entry_blanks_the_form() = runBlocking {
        measurements.saveMeasurement(MeasurementEntity(date = noon(0), weight = 75f, notes = "today"))

        val vm = viewModel(noon(0))
        vm.uiState.await { it.text(Metric.WEIGHT) == "75.0" }
        vm.setDate(noon(6))

        val state = vm.uiState.await { it.date == noon(6) }
        assertEquals("", state.text(Metric.WEIGHT))
        assertEquals("", state.notes)
    }
}
