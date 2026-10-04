package com.fitnessark.util

import com.fitnessark.data.model.Metric

/**
 * Parsing and range checks for typed measurement values (always metric: kg / cm).
 */
object MeasurementInput {

    private val WEIGHT_RANGE = 20f..400f
    private val BODY_RANGE   = 10f..300f

    /** Accepts both "72.5" and "72,5". Returns null for blank or unparseable text. */
    fun parse(text: String): Float? =
        text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

    /**
     * Returns an error message for [text], or null when it is valid.
     * Blank is valid (the field is optional); use [parse] to tell blank from a value.
     */
    fun validate(metric: Metric, text: String): String? {
        if (text.isBlank()) return null
        val value = parse(text) ?: return "Enter a number"
        val range = if (metric == Metric.WEIGHT) WEIGHT_RANGE else BODY_RANGE
        return if (value in range) null
        else "Must be ${range.start.toInt()}–${range.endInclusive.toInt()}"
    }
}
