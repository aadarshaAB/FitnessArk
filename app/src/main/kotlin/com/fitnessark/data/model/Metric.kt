package com.fitnessark.data.model

import com.fitnessark.data.local.entity.MeasurementEntity

/** A body measurement the app tracks. Values are always stored metric ([unit]). */
enum class Metric(val label: String, val unit: String) {
    WEIGHT("Weight", "kg"),
    CHEST("Chest",   "cm"),
    WAIST("Waist",   "cm"),
    HIPS("Hips",     "cm"),
    BICEPS("Biceps", "cm"),
    THIGHS("Thighs", "cm");

    /** This metric's logged value in [m], or null if it wasn't logged that day. */
    fun valueIn(m: MeasurementEntity): Float? = when (this) {
        WEIGHT -> m.weight
        CHEST  -> m.chest
        WAIST  -> m.waist
        HIPS   -> m.hips
        BICEPS -> m.biceps
        THIGHS -> m.thighs
    }

    /** The unit label shown for this metric in [system]. */
    fun unit(system: UnitSystem): String = when (system) {
        UnitSystem.METRIC   -> unit
        UnitSystem.IMPERIAL -> if (this == WEIGHT) "lb" else "in"
    }

    private fun factor(system: UnitSystem): Float = when (system) {
        UnitSystem.METRIC   -> 1f
        UnitSystem.IMPERIAL -> if (this == WEIGHT) UnitSystem.POUNDS_PER_KG else 1f / UnitSystem.CM_PER_INCH
    }

    /** A stored (metric) value, or a difference between two, expressed in [system]. */
    fun toDisplay(metricValue: Float, system: UnitSystem): Float = metricValue * factor(system)

    /** A value typed in [system], converted to the metric value that gets stored. */
    fun toMetric(displayValue: Float, system: UnitSystem): Float = displayValue / factor(system)

    /** Text for an input field pre-filled with a stored value: as stored in metric, one decimal in imperial. */
    fun toInputText(metricValue: Float, system: UnitSystem): String =
        if (system == UnitSystem.METRIC) metricValue.toString()
        else "%.1f".format(toDisplay(metricValue, system))

    /** "72.5 kg" / "159.8 lb" for a stored value, or "—" when it wasn't logged. */
    fun format(metricValue: Float?, system: UnitSystem, separator: String = " "): String =
        metricValue?.let { "%.1f%s%s".format(toDisplay(it, system), separator, unit(system)) } ?: "—"
}
