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
        assertFailsWith<IllegalArgumentException> { CircuitDiagnostics.difference(Double.NaN, 1.0) }
        assertFailsWith<IllegalArgumentException> { CircuitDiagnostics.difference(0.0, Double.POSITIVE_INFINITY) }
    }
}
