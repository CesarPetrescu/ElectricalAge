package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*

/** No Minecraft/platform mocks: these tests use the production matrix, stamps and probes. */
class ConverterTopologyRegressionTest {
    private fun near(actual: Double, expected: Double, tolerance: Double = 1e-7) {
        assertTrue(actual.isFinite() && abs(actual - expected) <= tolerance * maxOf(1.0, abs(expected)),
            "$actual != $expected")
    }

    @Test fun irregularOpenCableTreesRemainOpenAtEitherPolarity() {
        val random = Random(401)
        repeat(100) { trial ->
            val root = RootSystem(.01, 1)
            val pins = List(8) { VoltageState() }.also { it.forEach(root::addState) }
            val source = SwitchableVoltageSource("probe").apply { connectTo(pins[0], null); voltage = 3200.0 }
            root.addComponent(source)
            for (i in 1..pins.lastIndex) {
                val parent = if (trial % 2 == 0) i - 1 else random.nextInt(i)
                root.addComponent(Resistor(pins[parent], pins[i]).setResistance(.0001 + random.nextDouble() * .1))
            }
            root.step()
            for (value in listOf(0.0, 3200.0, -3200.0)) {
                source.voltage = value
                source.enabled = true
                root.step()
                val before = pins.map { it.state }
                val response = probePort(pins[0], null, source)
                assertEquals(Double.POSITIVE_INFINITY, response.ohms, "trial $trial")
                assertEquals(0.0, response.volts)
                assertEquals(before, pins.map { it.state })
                assertTrue(source.enabled)
                assertEquals(value, source.voltage)
                near(source.power, 0.0)
            }
        }
    }

    @Test fun idleChargerResistanceIsNotDiscardedAsFloating() {
        val root = RootSystem(.01, 1)
        val port = VoltageState(); val terminal = VoltageState()
        root.addState(port); root.addState(terminal)
        val source = SwitchableVoltageSource("probe").apply { connectTo(port, null); voltage = 3200.0; enabled = true }
        root.addComponent(source)
        root.addComponent(Resistor(port, terminal).setResistance(.0637))
        root.addComponent(Resistor(terminal, null).setResistance(1e12))
        root.step()
        val response = probePort(port, null, source)
        near(response.ohms, 1e12 + .0637, 1e-12)
        near(response.volts, 0.0)
        near(source.power, 3200.0 * 3200.0 / (1e12 + .0637), 1e-10)
    }

    @Test fun affineProbeDoesNotCancelLargeNearlyEqualCurrents() {
        val root = RootSystem(.01, 1)
        val supply = VoltageState(); val port = VoltageState()
        root.addState(supply); root.addState(port)
        root.addComponent(VoltageSource("supply", supply, null).setVoltage(3200.0))
        val r = .12771
        root.addComponent(Resistor(supply, port).setResistance(r))
        val source = SwitchableVoltageSource("probe").apply { connectTo(port, null) }
        root.addComponent(source)
        root.step()
        for (command in listOf(0.0, 3200.0, 1e8, -1e8)) {
            source.voltage = command
            val response = probePort(port, null, source)
            near(response.volts, 3200.0, 1e-13)
            near(response.ohms, r, 1e-13)
            assertFalse(source.enabled)
            assertEquals(command, source.voltage)
        }
    }

    @Test fun anOpenPortWithInjectedCurrentIsNotAPassiveOpenCircuit() {
        val system = SubSystem(null, .01)
        val port = VoltageState(); system.addState(port)
        val probe = SwitchableVoltageSource("probe").apply { connectTo(port, null) }
        system.addComponent(probe)
        system.addComponent(CurrentSource("injected", port, null).setCurrent(1.0))
        val response = probePort(port, null, probe)
        assertTrue(response.volts.isNaN() && response.ohms.isNaN())
        assertFalse(probe.enabled)
    }

    @Test fun conflictingIdealSourcesAreStillRejected() {
        val system = SubSystem(null, .01)
        val port = VoltageState(); system.addState(port)
        system.addComponent(VoltageSource("external", port, null).setVoltage(12.0))
        val probe = SwitchableVoltageSource("probe").apply { connectTo(port, null); voltage = 13.0 }
        system.addComponent(probe)
        assertTrue(probePort(port, null, probe).volts.isNaN())
        assertFalse(probe.enabled)
        near(probe.voltage, 13.0)
    }

    @Test fun sourceProbePreservesIsolatedReferenceAndCapacitorHistory() {
        val system = SubSystem(null, .01)
        val plus = VoltageState(); val minus = VoltageState()
        system.addState(plus); system.addState(minus)
        val capacitor = Capacitor(plus, minus).apply { setCoulombs(.001) }
        val probe = SwitchableVoltageSource("isolated").apply { connectTo(plus, minus); enabled = true; voltage = 50.0 }
        system.addComponent(capacitor); system.addComponent(probe)
        system.step()
        val energy = capacitor.energy; val current = capacitor.current
        val before = plus.state to minus.state
        repeat(25) {
            val response = probePort(plus, minus, probe)
            near(response.ohms, 10.0)
            near(response.volts, 50.0)
            assertEquals(before, plus.state to minus.state)
            assertEquals(energy, capacitor.energy)
            assertEquals(current, capacitor.current)
        }
    }

    @Test fun converterPortSeesThroughPrivateApiLoadWithoutArtificialVoltageBoundary() {
        val root = RootSystem(.01, 1)
        val port = VoltageState(); val terminal = VoltageState().apply { setAsPrivate() }
        root.addState(port); root.addState(terminal)
        root.addComponent(Resistor(port, terminal).setResistance(.001))
        root.addComponent(Resistor(terminal, null).setResistance(10_000.0))
        val source = SwitchableVoltageSource("probe").apply { connectTo(port, null) }
        root.addComponent(source)
        root.generate()
        assertSame(port.subSystem, terminal.subSystem)
        assertTrue(port.subSystem.interSystemConnectivity.isEmpty())
        near(probePort(port, null, source).ohms, 10_000.001, 1e-11)
    }

    @Test fun ordinaryPrivatePartitionPolicyIsUnchanged() {
        val root = RootSystem(.01, 1)
        val a = VoltageState(); val b = VoltageState().apply { setAsPrivate() }
        root.addState(a); root.addState(b)
        root.addComponent(Resistor(a, b).setResistance(1.0))
        root.addComponent(VoltageSource("source", a, null).setVoltage(50.0))
        root.addComponent(Resistor(b, null).setResistance(10.0))
        root.generate()
        assertNotSame(a.subSystem, b.subSystem)
        assertTrue(a.subSystem.interSystemConnectivity.contains(b.subSystem))
    }

    @Test fun exactNetworkRebuildPreservesDisconnectionAndReconnection() {
        val root = RootSystem(.01, 1)
        val a = VoltageState(); val b = VoltageState().apply { setAsPrivate() }
        root.addState(a); root.addState(b)
        val wire = Resistor(a, b).setResistance(.01)
        val source = SwitchableVoltageSource("source").apply { connectTo(a, null); voltage = 3200.0; enabled = true }
        root.addComponent(wire); root.addComponent(source)
        root.addComponent(Resistor(b, null).setResistance(10_000.0))
        repeat(3) {
            root.step(); assertSame(a.subSystem, b.subSystem); assertTrue(b.voltage > 3190.0)
            root.removeComponent(wire); wire.breakConnection(); root.step()
            near(b.voltage, 0.0)
            wire.connectTo(a, b); root.addComponent(wire)
        }
        root.step(); near(b.voltage, 3200.0 * 10000.0 / 10000.01)
    }
}
