package com.fitnessark.data

import com.fitnessark.TestSupport
import com.fitnessark.TestSupport.noon
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.local.entity.MeasurementEntity
import com.fitnessark.data.repository.MeasurementRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MeasurementRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MeasurementRepository

    @Before fun setUp() {
        db = TestSupport.inMemoryDb()
        repo = MeasurementRepository(db.measurementDao())
    }

    @After fun tearDown() = db.close()

    private fun logOn(vararg daysAgo: Int) = runBlocking {
        daysAgo.forEach { repo.saveMeasurement(MeasurementEntity(date = noon(it), weight = 70f)) }
    }

    private fun streak() = runBlocking { repo.calculateStreak() }

    // -- Streak --------------------------------------------------------------

    @Test fun streak_is_zero_with_no_entries() = assertEquals(0, streak())

    @Test fun streak_counts_today_alone_as_one() { logOn(0); assertEquals(1, streak()) }

    @Test fun streak_counts_consecutive_days_ending_today() {
        logOn(0, 1, 2); assertEquals(3, streak())
    }

    @Test fun streak_survives_not_having_logged_yet_today() {
        // Yesterday and the day before: the streak is still alive until today ends.
        logOn(1, 2); assertEquals(2, streak())
    }

    @Test fun streak_is_zero_when_last_entry_is_two_or_more_days_old() {
        logOn(2, 3, 4); assertEquals(0, streak())
    }

    @Test fun streak_stops_at_the_first_gap() {
        logOn(0, 1, 3, 4, 5); assertEquals(2, streak())
    }

    @Test fun several_entries_on_one_day_count_once() {
        logOn(0, 0, 0, 1); assertEquals(2, streak())
    }

    // -- Day lookup ----------------------------------------------------------

    @Test fun getMeasurementForDay_finds_only_that_calendar_day() = runBlocking {
        repo.saveMeasurement(MeasurementEntity(date = noon(3), weight = 71f))
        repo.saveMeasurement(MeasurementEntity(date = noon(0), weight = 69f))

        assertEquals(71f, repo.getMeasurementForDay(noon(3))!!.weight!!, 0.001f)
        assertEquals(69f, repo.getMeasurementForDay(noon(0))!!.weight!!, 0.001f)
        assertNull(repo.getMeasurementForDay(noon(1)))
    }

    @Test fun saving_a_different_id_on_the_same_day_updates_that_days_row() = runBlocking {
        val first = MeasurementEntity(date = noon(0), weight = 70f)
        repo.saveMeasurement(first)
        repo.saveMeasurement(MeasurementEntity(date = noon(0) + 3_600_000, weight = 71f, waist = 80f))

        assertEquals(1, repo.getMeasurementCount())
        val row = repo.getMeasurementForDay(noon(0))!!
        assertEquals("the day keeps its original id", first.id, row.id)
        assertEquals(71f, row.weight!!, 0.001f)
        assertEquals(80f, row.waist!!, 0.001f)
    }

    @Test fun the_database_itself_rejects_a_second_row_for_a_day() {
        val dao = db.measurementDao()
        val a = MeasurementEntity(id = "a", date = noon(0), weight = 70f)
        val b = MeasurementEntity(id = "b", date = noon(0), weight = 71f)

        // Bypassing the repository: REPLACE on the unique day index still leaves a single row.
        runBlocking { dao.insertMeasurement(a); dao.insertMeasurement(b) }

        val all = runBlocking { dao.getAllMeasurementsList() }
        assertEquals(listOf("b"), all.map { it.id })
    }

    @Test fun saving_with_same_id_replaces_instead_of_duplicating() = runBlocking {
        val first = MeasurementEntity(date = noon(0), weight = 70f)
        repo.saveMeasurement(first)
        repo.saveMeasurement(first.copy(weight = 71f))

        assertEquals(1, repo.getMeasurementCount())
        assertNotNull(repo.getMeasurementForDay(noon(0)))
        assertEquals(71f, repo.getLatestMeasurement()!!.weight!!, 0.001f)
    }
}
