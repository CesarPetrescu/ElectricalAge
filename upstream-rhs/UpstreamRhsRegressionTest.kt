package mods.eln.sim.mna

import mods.eln.disableLog4jJmx
import mods.eln.sim.mna.component.Capacitor
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.misc.ISubSystemProcessI
import mods.eln.sim.mna.state.VoltageState
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

/** Independent regression cases for the shared 1.7.10 and 1.12.2 RHS-stamping bug. */
class UpstreamRhsRegressionTest {
    @Before fun quietLogging() { disableLog4jJmx() }

    private fun circuit(): Pair<SubSystem, VoltageState> {
        val system = SubSystem(null, 0.01)
        val node = VoltageState()
        system.addState(node)
        val resistor = Resistor(node, null)
        resistor.setResistance(10.0)
        system.addComponent(resistor)
        return system to node
    }

    @Test fun currentContributionsAdd() {
        val (system, node) = circuit()
        system.addProcess(ISubSystemProcessI { it.addToI(node, 0.01); it.addToI(node, 0.02) })
        system.step()
        assertEquals(0.3, node.state, 1e-10)
    }

    @Test fun oppositeContributionsCancel() {
        val (system, node) = circuit()
        system.addProcess(ISubSystemProcessI { it.addToI(node, 0.02); it.addToI(node, -0.02) })
        system.step()
        assertEquals(0.0, node.state, 1e-10)
    }

    @Test fun rhsIsResetBeforeEveryStep() {
        val (system, node) = circuit()
        system.addProcess(ISubSystemProcessI { it.addToI(node, 0.02); it.addToI(node, 0.03) })
        repeat(10) { system.step(); assertEquals(0.5, node.state, 1e-10) }
    }

    private fun capacitorVoltage(first: Double, second: Double): Double {
        val system = SubSystem(null, 0.01)
        val node = VoltageState()
        node.state = 1.0
        system.addState(node)
        val resistor = Resistor(node, null)
        resistor.setResistance(10.0)
        system.addComponent(resistor)
        for (value in doubleArrayOf(first, second)) {
            val capacitor = Capacitor(node, null)
            capacitor.setCoulombs(value)
            system.addComponent(capacitor)
        }
        system.step()
        return node.state
    }

    @Test fun parallelCapacitorsRetainTheCombinedCharge() {
        assertEquals(0.3 / 0.31, capacitorVoltage(0.001, 0.002), 1e-10)
    }

    @Test fun capacitorRegistrationOrderDoesNotChangeVoltage() {
        assertEquals(capacitorVoltage(0.001, 0.002), capacitorVoltage(0.002, 0.001), 1e-10)
    }
}
