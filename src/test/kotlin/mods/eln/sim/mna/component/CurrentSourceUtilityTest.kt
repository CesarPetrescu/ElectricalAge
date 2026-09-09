package mods.eln.sim.mna.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import mods.eln.sim.mna.SubSystem
import mods.eln.disableLog4jJmx
import mods.eln.sim.mna.state.VoltageState

class CurrentSourceUtilityTest {
    // Use the production component, not a subclass that registers its callback twice.
    private class Circuit {
        val a = VoltageState()
        val b = VoltageState()
        val source = CurrentSource("i", a, b).setCurrent(2.0)
        val system = SubSystem(null, 0.1).apply {
            addState(a)
            addState(b)
            addComponent(Resistor(a, null).setResistance(100.0))
            addComponent(Resistor(b, null).setResistance(100.0))
            addComponent(source)
        }
        fun checkPowered() {
            system.step()
            assertSame(system, source.subSystem)
            assertEquals(200.0, a.voltage, 1e-8)
            assertEquals(-200.0, b.voltage, 1e-8)
        }
        fun checkUnpowered() {
            system.step()
            val rhs = system.captureDebugSnapshot().rhsVector
            assertEquals(0.0, rhs[a.id])
            assertEquals(0.0, rhs[b.id])
            assertEquals(0.0, a.voltage, 1e-8)
            assertEquals(0.0, b.voltage, 1e-8)
            assertNull(source.subSystem)
        }
    }

    @Test
    fun getCurrentReturnsConfiguredValue() {
        val source = CurrentSource("i").setCurrent(2.5)
        assertEquals(2.5, source.current)
    }

    @Test
    fun quitSubSystemRemovesProcess() {
        disableLog4jJmx()
        val circuit = Circuit()
        circuit.checkPowered()
        circuit.source.quitSubSystem()
        circuit.checkUnpowered()
    }

    @Test
    fun componentRemovalAndReattachmentDoNotDuplicateCurrent() {
        disableLog4jJmx()
        val circuit = Circuit()
        repeat(4) {
            circuit.checkPowered()
            circuit.system.removeComponent(circuit.source)
            circuit.checkUnpowered()
            circuit.system.addComponent(circuit.source)
        }
        circuit.checkPowered()
    }

    @Test
    fun repeatedQuitIsHarmlessAndClearsOwnership() {
        disableLog4jJmx()
        val circuit = Circuit()
        circuit.checkPowered()
        circuit.source.quitSubSystem()
        circuit.source.quitSubSystem()
        circuit.checkUnpowered()
    }
}
