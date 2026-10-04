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
}
