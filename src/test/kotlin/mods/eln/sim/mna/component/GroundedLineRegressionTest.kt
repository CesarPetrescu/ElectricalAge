package mods.eln.sim.mna.component

import mods.eln.disableLog4jJmx
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.state.VoltageState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroundedLineRegressionTest {
    private fun checkGroundedLine(groundAtStart: Boolean, supplyVolts: Double) {
        disableLog4jJmx()
        val root = RootSystem(0.01, 1)
        val supplyPin = VoltageState()
        val middle = VoltageState().apply { setCanBeSimplifiedByLine(true) }
        val a = if (groundAtStart) null else supplyPin
        val b = if (groundAtStart) supplyPin else null
        val first = Resistor(a, middle).setResistance(2.0)
        val second = Resistor(middle, b).setResistance(4.0)
        val source = VoltageSource("supply", supplyPin, null).setVoltage(supplyVolts)
        root.addState(supplyPin)
        root.addState(middle)
        root.addComponent(first)
        root.addComponent(second)
        root.addComponent(source)
        repeat(5) { root.step() }
        assertTrue(middle.abstractedBy is Line, "The real line simplifier must be exercised")
        val expected = supplyVolts * if (groundAtStart) (2.0 / 6.0) else (4.0 / 6.0)
        assertEquals(expected, middle.voltage, 1e-8)
        assertEquals(source.power, first.power + second.power, 1e-8)

        // Heating changes resistance through the actual abstraction's dirty path.
        first.resistance = 4.0
        repeat(5) { root.step() }
        assertEquals(supplyVolts / 2.0, middle.voltage, 1e-8)
        assertEquals(source.power, first.power + second.power, 1e-8)
    }

    @Test fun groundedEndReconstructsVoltageAndLosses() = checkGroundedLine(false, 12.0)
    @Test fun groundedStartReconstructsVoltageAndLosses() = checkGroundedLine(true, 12.0)
    @Test fun negativeSupplyWithGroundedEnd() = checkGroundedLine(false, -12.0)
    @Test fun negativeSupplyWithGroundedStart() = checkGroundedLine(true, -12.0)
}
