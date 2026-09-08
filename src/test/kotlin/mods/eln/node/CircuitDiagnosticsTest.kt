package mods.eln.node

import kotlin.test.*

class CircuitDiagnosticsTest {
    @Test fun floatingBatteryUsesDifferenceNotGroundVoltage() {
        assertEquals(12.0, CircuitDiagnostics.difference(-6.0, 6.0))
        assertEquals(-12.0, CircuitDiagnostics.difference(6.0, -6.0))
    }
    @Test fun commonModeDoesNotChangeMeasuredVoltage() {
        assertEquals(12.0, CircuitDiagnostics.difference(1000.0, 1012.0))
        assertEquals(0.0, CircuitDiagnostics.difference(6.0, 6.0))
    }
    @Test fun unsolvedValuesAreNotReportedAsRealVoltages() {
        // kotlin.test.assertFailsWith uses reflection across NeoForge's plugin/app classloaders.
        // Catch the JVM exception directly while retaining the exact expected exception type.
        for ((a, b) in listOf(Double.NaN to 1.0, 0.0 to Double.POSITIVE_INFINITY)) {
            var rejected = false
            try { CircuitDiagnostics.difference(a, b) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
}
