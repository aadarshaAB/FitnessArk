package com.fitnessark.data.model

/** How values are shown and typed. Storage is always metric; only the display changes. */
enum class UnitSystem(val label: String, val hint: String) {
    METRIC("Metric", "kg, cm"),
    IMPERIAL("Imperial", "lb, in");

    companion object {
        const val POUNDS_PER_KG = 2.2046226f
        const val CM_PER_INCH = 2.54f
    }
}
