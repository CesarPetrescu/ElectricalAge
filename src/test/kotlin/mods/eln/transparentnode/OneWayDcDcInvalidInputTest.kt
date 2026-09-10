package mods.eln.transparentnode

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OneWayDcDcInvalidInputTest {
    private fun th(voltage: Double, resistance: Double) = object : OneWayDcDcThevenin {
        override val voltage = voltage
        override val resistance = resistance
    }

    @Test fun invalidGainAndVoltageCeilingAreRejected() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0)) {
            assertNull(OneWayDcDcMath.solve(th(50.0, 0.0), th(0.0, 100.0), value, 120_000.0))
            assertNull(OneWayDcDcMath.solve(th(50.0, 0.0), th(0.0, 100.0), 1.0, value))
        }
    }

    @Test fun negativeResistanceIsNotAnIdealSupply() {
        assertNull(OneWayDcDcMath.solve(th(50.0, -1.0), th(0.0, 100.0), 1.0, 120_000.0))
        assertNull(OneWayDcDcMath.solve(th(50.0, 0.0), th(0.0, -1.0), 1.0, 120_000.0))
        assertNull(OneWayDcDcMath.solve(th(50.0, 0.0), th(0.0, 0.0), 1.0, 120_000.0))
    }

    @Test fun overflowingOperatingPointCannotReturnNonfinitePower() {
        assertNull(OneWayDcDcMath.solve(th(1e300, 0.0), th(0.0, 1e-300), 256.0, 120_000.0))
    }

    @Test fun validLegacyUnityStepUpStepDownAndDifferentialReferenceRemainUnchanged() {
        for (gain in listOf(.25, 1.0, 4.0)) {
            val result = assertNotNull(OneWayDcDcMath.solve(th(50.0, 0.0), th(0.0, 100.0), gain, 120_000.0))
            val volts = 50.0 * gain
            assertEquals(50.0, result.inputSourceVoltage, 1e-9)
            assertEquals(volts, result.outputSourceVoltage, 1e-9)
            assertEquals(volts * volts / 100.0, result.power, 1e-9)
        }
        val isolated = assertNotNull(OneWayDcDcMath.solve(th(50.0, 0.0), th(-50.0, 100.0), 1.0, 120_000.0))
        assertEquals(50.0, isolated.outputSourceVoltage, 1e-9)
        assertEquals(50.0, isolated.power, 1e-9)
    }
}
