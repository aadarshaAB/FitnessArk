package com.fitnessark.util

/** Smoothing for the progress charts and the dashboard's weekly average. */
object TrendLine {

    /** One logged value on the day containing [date] (a timestamp). */
    data class Point(val date: Long, val value: Float)

    const val WINDOW_DAYS = 7

    /**
     * Trailing moving average, one value per entry of [points] (which must be oldest first): the mean
     * of every logged value in the [windowDays] calendar days ending on that point's day. Days with
     * nothing logged are skipped rather than counted as zero, so gaps don't pull the line down.
     */
    fun movingAverage(points: List<Point>, windowDays: Int = WINDOW_DAYS): List<Float> =
        points.indices.map { i ->
            var sum = 0f
            var count = 0
            var j = i
            while (j >= 0 && DateUtils.getDaysBetween(points[j].date, points[i].date) < windowDays) {
                sum += points[j].value
                count++
                j--
            }
            sum / count
        }

    /** Mean of the values logged in the [windowDays] days ending on [now]'s day, or null if none. */
    fun recentAverage(points: List<Point>, now: Long, windowDays: Int = WINDOW_DAYS): Float? {
        val recent = points.filter {
            it.date <= now && DateUtils.getDaysBetween(it.date, now) < windowDays
        }
        return if (recent.isEmpty()) null else recent.map { it.value }.average().toFloat()
    }
}
