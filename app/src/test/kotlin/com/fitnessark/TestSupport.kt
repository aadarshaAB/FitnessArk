package com.fitnessark

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitnessark.data.local.AppDatabase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.Calendar

object TestSupport {

    fun context(): Context = ApplicationProvider.getApplicationContext()

    fun inMemoryDb(context: Context = context()): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** Local noon, [daysAgo] calendar days before today. Noon keeps tests clear of midnight/DST edges. */
    fun noon(daysAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Waits (real time, up to 5s) for a ViewModel's state to satisfy [predicate]; fails the test on timeout. */
suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
    withTimeout(5_000) { first(predicate) }
