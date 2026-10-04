package com.fitnessark.util

import com.fitnessark.data.model.Metric
import com.fitnessark.data.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConversionTest {

    @Test fun kilograms_and_pounds_convert_both_ways() {
        assertEquals(154.32f, Metric.WEIGHT.toDisplay(70f, UnitSystem.IMPERIAL), 0.01f)
        assertEquals(70f, Metric.WEIGHT.toMetric(154.32f, UnitSystem.IMPERIAL), 0.01f)
    }

    @Test fun centimetres_and_inches_convert_both_ways() {
        assertEquals(20f, Metric.WAIST.toDisplay(50.8f, UnitSystem.IMPERIAL), 0.001f)
        assertEquals(50.8f, Metric.WAIST.toMetric(20f, UnitSystem.IMPERIAL), 0.001f)
    }

    @Test fun metric_is_left_untouched() {
        assertEquals(72.5f, Metric.WEIGHT.toDisplay(72.5f, UnitSystem.METRIC), 0f)
        assertEquals(88f, Metric.CHEST.toMetric(88f, UnitSystem.METRIC), 0f)
    }

    @Test fun unit_labels_follow_the_system() {
        assertEquals("kg", Metric.WEIGHT.unit(UnitSystem.METRIC))
        assertEquals("lb", Metric.WEIGHT.unit(UnitSystem.IMPERIAL))
        assertEquals("cm", Metric.HIPS.unit(UnitSystem.METRIC))
        assertEquals("in", Metric.HIPS.unit(UnitSystem.IMPERIAL))
    }

    @Test fun format_shows_value_with_unit_or_a_dash() {
        assertEquals("72.5 kg", Metric.WEIGHT.format(72.5f, UnitSystem.METRIC))
        assertEquals("160.0lb", Metric.WEIGHT.format(72.57f, UnitSystem.IMPERIAL, separator = ""))
        assertEquals("—", Metric.WEIGHT.format(null, UnitSystem.IMPERIAL))
    }

    @Test fun a_logged_value_survives_the_stored_rounding_when_shown_at_one_decimal() {
        // 160 lb is stored as 72.57 kg (2 decimals) and must still read 160.0 lb
        val stored = Math.round(Metric.WEIGHT.toMetric(160f, UnitSystem.IMPERIAL) * 100f) / 100f
        assertEquals("160.0", "%.1f".format(Metric.WEIGHT.toDisplay(stored, UnitSystem.IMPERIAL)))
    }

    @Test fun imperial_validation_uses_converted_limits_and_messages() {
        assertNull(MeasurementInput.validate(Metric.WEIGHT, "44.1", UnitSystem.IMPERIAL))   // ~20 kg
        assertNotNull(MeasurementInput.validate(Metric.WEIGHT, "40", UnitSystem.IMPERIAL))
        assertNull(MeasurementInput.validate(Metric.WAIST, "4", UnitSystem.IMPERIAL))       // ~10.2 cm
        assertNotNull(MeasurementInput.validate(Metric.WAIST, "3", UnitSystem.IMPERIAL))
        assertEquals("Must be 44–882", MeasurementInput.validate(Metric.WEIGHT, "10", UnitSystem.IMPERIAL))
    }

    @Test fun parse_metric_converts_typed_imperial_values() {
        assertEquals(72.57f, MeasurementInput.parseMetric(Metric.WEIGHT, "160", UnitSystem.IMPERIAL)!!, 0.01f)
        assertEquals(81.28f, MeasurementInput.parseMetric(Metric.WAIST, "32,0", UnitSystem.IMPERIAL)!!, 0.01f)
        assertNull(MeasurementInput.parseMetric(Metric.WEIGHT, "", UnitSystem.IMPERIAL))
    }
}
