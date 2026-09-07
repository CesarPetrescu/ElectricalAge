package mods.eln.mechanical

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaftElectricalMathTest {
    @Test fun conversionConservesEnergyInBothDirections() {
        for (power in listOf(-16000.0, -1.0, 0.0, 1.0, 16000.0)) {
            for ((generation, motoring) in listOf(.95 to .1, .1 to .99)) {
                val result = ShaftElectricalMath.transfer(power, generation, motoring)
                assertEquals(0.0, power + result.shaftPower + result.heatPower, 1e-8)
                assertTrue(result.heatPower >= 0.0)
            }
        }
    }
    @Test fun reverseMotorConversionIsOnlyTenPercentEfficient() {
        val result = ShaftElectricalMath.transfer(100.0, .1, .99)
        assertEquals(-1000.0, result.shaftPower)
        assertEquals(900.0, result.heatPower)
    }
    @Test fun resistorDroopIsSolvedWithoutPreviousCurrentFeedback() {
        assertEquals(480.0 * 10.0 / 10.5,
            ShaftElectricalMath.sourceVoltage(480.0, 0.0, 10.0, .5, 100.0, 1e9), 1e-8)
    }
    @Test fun bothCurrentDirectionsAreLimitedAgainstStiffBatteries() {
        for (emf in listOf(0.0, 120.0, 240.0, 480.0)) {
            val voltage = ShaftElectricalMath.sourceVoltage(emf, 120.0, .01, .5, 10.0, 1e9)
            assertTrue(kotlin.math.abs((voltage - 120.0) / .01) <= 10.000001)
        }
    }
    @Test fun emptyShaftCannotGenerate() {
        assertEquals(0.0, ShaftElectricalMath.sourceVoltage(480.0, 0.0, 10.0, .5, 100.0, 0.0))
        assertEquals(120.0, ShaftElectricalMath.sourceVoltage(480.0, 120.0, .01, .5, 100.0, 0.0))
    }
    @Test fun lastStepCannotExportMoreEnergyThanAvailable() {
        for (supply in listOf(0.0, 12.0, 120.0)) {
            val voltage = ShaftElectricalMath.sourceVoltage(480.0, supply, .01, .5, 100.0, 3.0)
            val current = (voltage - supply) / .01
            assertTrue(voltage * current <= 3.000001)
        }
    }
    @Test fun unloadedGeneratorHasOpenCircuitVoltage() {
        assertEquals(480.0, ShaftElectricalMath.sourceVoltage(480.0, 0.0, 1e12, .5, 100.0, 1e9))
    }
}
