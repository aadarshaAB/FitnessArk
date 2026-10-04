package com.fitnessark.util

import com.fitnessark.data.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementInputTest {

    @Test fun parses_dot_and_comma_decimals() {
        assertEquals(72.5f, MeasurementInput.parse("72.5")!!, 0.0001f)
        assertEquals(72.5f, MeasurementInput.parse("72,5")!!, 0.0001f)
        assertEquals(72.5f, MeasurementInput.parse("  72,5 ")!!, 0.0001f)
    }

    @Test fun blank_and_garbage_do_not_parse() {
        assertNull(MeasurementInput.parse(""))
        assertNull(MeasurementInput.parse("abc"))
        assertNull(MeasurementInput.parse("1,2,3"))
        assertNull(MeasurementInput.parse("NaN"))
        assertNull(MeasurementInput.parse("Infinity"))
    }

    @Test fun blank_is_valid_because_fields_are_optional() {
        assertNull(MeasurementInput.validate(Metric.WEIGHT, ""))
        assertNull(MeasurementInput.validate(Metric.WAIST, "   "))
    }

    @Test fun weight_range_is_20_to_400() {
        assertNull(MeasurementInput.validate(Metric.WEIGHT, "20"))
        assertNull(MeasurementInput.validate(Metric.WEIGHT, "400"))
        assertNotNull(MeasurementInput.validate(Metric.WEIGHT, "19.9"))
        assertNotNull(MeasurementInput.validate(Metric.WEIGHT, "400.1"))
        assertNotNull(MeasurementInput.validate(Metric.WEIGHT, "0"))
        assertNotNull(MeasurementInput.validate(Metric.WEIGHT, "-5"))
    }

    @Test fun body_measurement_range_is_10_to_300() {
        for (key in Metric.entries - Metric.WEIGHT) {
            assertNull(key.name, MeasurementInput.validate(key, "10"))
            assertNull(key.name, MeasurementInput.validate(key, "300"))
            assertNotNull(key.name, MeasurementInput.validate(key, "9.9"))
            assertNotNull(key.name, MeasurementInput.validate(key, "301"))
        }
    }

    @Test fun non_numeric_text_reports_an_error() {
        assertEquals("Enter a number", MeasurementInput.validate(Metric.WEIGHT, "heavy"))
    }
}
