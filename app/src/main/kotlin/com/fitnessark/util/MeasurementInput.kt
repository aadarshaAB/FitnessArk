package com.fitnessark.util

import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.UnitSystem
import kotlin.math.roundToInt

/**
 * Parsing and range checks for typed measurement values. Typed values are in the user's
 * [UnitSystem]; the limits below are metric (kg / cm) and are applied after converting.
 */
object MeasurementInput {

    private val WEIGHT_RANGE = 20f..400f
    private val BODY_RANGE   = 10f..300f

    /** Accepts both "72.5" and "72,5". Returns null for blank or unparseable text. */
    fun parse(text: String): Float? =
        text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

    /** The metric value for typed [text], or null for blank or unparseable text. */
    fun parseMetric(metric: Metric, text: String, system: UnitSystem): Float? =
        parse(text)?.let { metric.toMetric(it, system) }

    /**
     * Returns an error message for [text], or null when it is valid.
     * Blank is valid (the field is optional); use [parse] to tell blank from a value.
     */
    fun validate(metric: Metric, text: String, system: UnitSystem = UnitSystem.METRIC): String? {
        if (text.isBlank()) return null
        val typed = parse(text) ?: return "Enter a number"
        val range = if (metric == Metric.WEIGHT) WEIGHT_RANGE else BODY_RANGE
        // Compare in metric, with a little slack so the exact limit typed back in lb/in is accepted.
        val metricValue = metric.toMetric(typed, system)
        if (metricValue in (range.start - 0.05f)..(range.endInclusive + 0.05f)) return null
        val low = metric.toDisplay(range.start, system).roundToInt()
        val high = metric.toDisplay(range.endInclusive, system).roundToInt()
        return "Must be $low–$high"
    }
}
