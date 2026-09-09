package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.*
import mods.eln.sim.mna.misc.ISubSystemProcessFlush
import org.junit.Test
import kotlin.test.*
import kotlin.math.abs

class ConverterMnaTest {
    private fun resistor(root: RootSystem, a: State, b: State?, ohms: Double) = Resistor(a, b).also {
        it.resistance = ohms; root.addComponent(it)
    }
    private fun close(actual: Double, expected: Double) = assertEquals(expected, actual, 1e-6 * maxOf(1.0, abs(expected)))

    @Test fun disabledSourceIsOpenRatherThanZeroVoltShort() {
        val root = RootSystem(.01, 1)
        val battery = VoltageState(); val port = VoltageState()
        root.addState(battery); root.addState(port)
        root.addComponent(VoltageSource("battery", battery, null).setVoltage(12.0))
        resistor(root, battery, port, 10.0)
        val source = SwitchableVoltageSource("isolator").apply { connectTo(port, null); voltage = 123.0 }
        root.addComponent(source)
        root.step(); close(port.voltage, 12.0); close(source.current, 0.0)
        source.voltage = 0.0; source.enabled = true
        root.step(); close(port.voltage, 0.0); close(source.current, -1.2)
        source.enabled = false
        root.step(); close(port.voltage, 12.0); close(source.current, 0.0)
    }

    @Test fun floatingDifferentialPortDoesNotRequireLeakageOrSharedGround() {
        val system = SubSystem(null, .01)
        val plus = VoltageState(); val minus = VoltageState()
        val source = SwitchableVoltageSource("floating").apply { connectTo(plus, minus); enabled = true; voltage = 50.0 }
        val load = Resistor(plus, minus).apply { resistance = 100.0 }
        system.addState(plus); system.addState(minus); system.addComponent(source); system.addComponent(load)
        system.step(); close(plus.voltage - minus.voltage, 50.0); close(source.current, .5)
        assertTrue(system.captureDebugSnapshot().isSingular)
        assertTrue(system.captureDebugSnapshot().hasReferenceGauge())
        source.enabled = false; system.step(); close(load.current, 0.0)
    }

    @Test fun floatingUnbalancedCurrentIsRejectedNotAbsorbedByGauge() {
        val system = SubSystem(null, .01)
        val port = VoltageState(); system.addState(port)
        system.addComponent(CurrentSource("unbalanced", port, null).setCurrent(1.0))
        system.stepCalc()
        assertFalse(system.hasValidStepSolution())
        assertTrue(system.solveChecked(port).isNaN())
    }

    @Test fun differentialProbeRestoresCommandEnableStateAndPhysicalStates() {
        val root = RootSystem(.01, 1)
        val supply = VoltageState(); val port = VoltageState(); val reference = VoltageState()
        listOf(supply, port, reference).forEach(root::addState)
        root.addComponent(VoltageSource("reference", reference, null).setVoltage(1500.0))
        root.addComponent(VoltageSource("feed", supply, reference).setVoltage(50.0))
        resistor(root, supply, port, 2.0)
        val source = SwitchableVoltageSource("probe").apply { connectTo(port, reference); voltage = 321.0 }
        root.addComponent(source); root.step()
        val before = port.state
        repeat(10) {
            val th = probePort(port, reference, source)
            close(th.volts, 50.0); close(th.ohms, 2.0)
            close(source.voltage, 321.0); assertFalse(source.enabled); close(port.state, before)
        }
    }

    @Test fun reversibleTransformerUsesLoadedRatioAndConservesInternalPower() {
        val root = RootSystem(.01, 1)
        val feed = VoltageState(); val primary = VoltageState(); val secondary = VoltageState()
        listOf(feed, primary, secondary).forEach(root::addState)
        val supply = VoltageSource("feed", feed, null).setVoltage(50.0)
        root.addComponent(supply)
        val inputR = resistor(root, feed, primary, 1.0)
        val load = resistor(root, secondary, null, 6400.0)
        val a = SwitchableVoltageSource("a").apply { connectTo(primary, null) }
        val b = SwitchableVoltageSource("b").apply { connectTo(secondary, null) }
        root.addComponent(a); root.addComponent(b)
        val process = SafeTransformerProcess(primary, secondary, a, b) { true }.apply { ratio = 16.0 }
        root.addProcess(process); repeat(10) { root.step() }
        close(secondary.voltage, primary.voltage * 16)
        close(-a.power, b.power)
        close(supply.power, inputR.power + load.power)
        assertTrue(load.power > 90)
        root.removeComponent(supply); supply.breakConnection()
        repeat(3) { root.step() }
        close(load.power, 0.0); assertFalse(a.enabled); assertFalse(b.enabled)
    }

    @Test fun noLoadHighRatioWorksWithoutPhantomPower() {
        val root = RootSystem(.01, 1)
        val feed = VoltageState(); val aPin = VoltageState(); val bPin = VoltageState()
        listOf(feed, aPin, bPin).forEach(root::addState)
        root.addComponent(VoltageSource("feed", feed, null).setVoltage(50.0))
        resistor(root, feed, aPin, .1)
        val a = SwitchableVoltageSource("a").apply { connectTo(aPin, null) }
        val b = SwitchableVoltageSource("b").apply { connectTo(bPin, null) }
        root.addComponent(a); root.addComponent(b)
        root.addProcess(SafeTransformerProcess(aPin, bPin, a, b) { true }.apply { ratio = 256.0 })
        root.step(); close(bPin.voltage, 12_800.0); close(a.power, 0.0); close(b.power, 0.0)
    }

    @Test fun failedGroupOpensAndCommitsPhysicalStateExactlyOnce() {
        val root = RootSystem(.01, 1)
        val node = VoltageState(); root.addState(node)
        var attempts = 0; var commits = 0; var closed = false
        val bad = object : ConservativePowerProcess {
            override fun rootSystemPreStepProcess() { attempts++ }
            override fun acceptsCandidate() = closed
            override fun failClosed() { closed = true }
            override fun connectedSystems() = setOfNotNull(node.subSystem)
        }
        root.addProcess(bad)
        root.addProcess(ISubSystemProcessFlush { commits++ })
        root.step()
        assertTrue(closed); assertEquals(64, attempts); assertEquals(1, commits)
    }

    @Test fun invalidSourceSetpointCannotPublishNaNStates() {
        val system = SubSystem(null, .01); val node = VoltageState()
        system.addState(node); system.addComponent(VoltageSource("bad", node, null).setVoltage(Double.NaN))
        system.step(); assertTrue(node.state.isFinite()); close(node.state, 0.0)
    }
}
