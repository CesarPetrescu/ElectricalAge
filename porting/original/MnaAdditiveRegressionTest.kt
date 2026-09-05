package mods.eln.sim.mna

import kotlin.test.Test
import kotlin.test.assertEquals
import mods.eln.sim.mna.component.Capacitor
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.misc.ISubSystemProcessI
import mods.eln.sim.mna.state.VoltageState

/** Checks the upstream solver itself, not a substitute circuit implementation. */
class MnaAdditiveRegressionTest {
    private fun resistor(system: SubSystem, node: VoltageState) {
        val resistor = Resistor(node, null)
        resistor.resistance = 10.0
        system.addComponent(resistor)
    }

    @Test fun parallelCurrentContributionsAccumulate() {
        val system = SubSystem(null, 0.1)
        val node = VoltageState()
        system.addState(node)
        resistor(system, node)
        system.addProcess(ISubSystemProcessI { it.addToI(node, 0.01) })
        system.addProcess(ISubSystemProcessI { it.addToI(node, 0.02) })
        repeat(10) {
            system.step()
            assertEquals(0.3, node.state, 1e-10)
        }
    }

    @Test fun parallelCapacitorsAreOrderIndependent() {
        for (values in listOf(listOf(0.1, 0.2), listOf(0.2, 0.1))) {
            val system = SubSystem(null, 0.1)
            val node = VoltageState()
            node.state = 5.0
            system.addState(node)
            resistor(system, node)
            for (value in values) {
                val capacitor = Capacitor(node, null)
                capacitor.coulombs = value
                system.addComponent(capacitor)
            }
            system.step()
            assertEquals(5.0 * 0.3 / (0.3 + 0.1 / 10.0), node.state, 1e-10)
        }
    }

    @Test fun oppositeCurrentContributionsCancel() {
        val system = SubSystem(null, 0.1)
        val node = VoltageState()
        system.addState(node)
        resistor(system, node)
        system.addProcess(ISubSystemProcessI { it.addToI(node, 0.02); it.addToI(node, -0.02) })
        system.step()
        assertEquals(0.0, node.state, 1e-12)
    }
}
