package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.misc.ISubSystemProcessFlush
import mods.eln.sim.mna.state.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*

/** Real production controllers and MNA components; no substitute controller or simulated test result. */
class ParallelConverterRuntimeTest {
    @Test fun fourConverterOverloadAndRecoveryWithPublicAndPrivateLoads() {
        for (privateLoad in listOf(false, true)) {
            val rig = ConverterBusFixture(4, privateLoad)
            for (r in listOf(10000.0, 100.0, 10.0, 100.0, 10000.0)) {
                rig.load.resistance = r
                rig.step(3)
                if (r == 10.0) {
                    assertTrue(rig.bus.voltage in 1400.0..1550.0, rig.diagnostic())
                    assertTrue(rig.controllers.all { it.status == "INPUT_CURRENT_OR_SAG" }, rig.diagnostic())
                    rig.inputs.forEach { assertEquals(200.0, -it.current, .001) }
                } else assertTrue(rig.bus.voltage in 3190.0..3200.001, rig.diagnostic())
            }
        }
    }

    @Test fun openOutputIdleChargerAndLoadTransitionsNeedNoArtificialGround() {
        val rig = ConverterBusFixture(4, true)
        rig.detachLoad()
        rig.step(3)
        assertTrue(rig.bus.voltage in 3199.0..3200.001, rig.diagnostic())
        assertTrue(abs(rig.outputs.sumOf { it.power }) < 1e-7)
        rig.attachLoad(1e12)
        rig.step(3)
        assertTrue(rig.bus.voltage > 3199.0)
        rig.load.resistance = 3200.0 * 3200.0 / 22000.0
        rig.step(3)
        assertTrue(rig.load.power > 21900.0)
        rig.load.resistance = 1e12
        rig.step(3)
        assertTrue(rig.bus.voltage > 3199.0)
    }

    @Test fun mismatchedSetpointsAndChangedTargetsBlockReversePower() {
        val rig = ConverterBusFixture(4, true)
        rig.targets.indices.forEach { rig.targets[it] = 2800.0 + it * 150.0 }
        for (r in listOf(10000.0, 10.0, 320.0, 10000.0)) {
            rig.load.resistance = r; rig.step(3)
            assertTrue(rig.bus.voltage > 500.0, rig.diagnostic())
            rig.outputs.forEach { assertTrue(it.current >= -1e-7) }
        }
        rig.targets.fill(1600.0); rig.step(3)
        assertTrue(rig.bus.voltage in 1590.0..1600.001, rig.diagnostic())
        rig.targets.fill(6400.0); rig.step(3)
        assertTrue(rig.bus.voltage in 6390.0..6400.001, rig.diagnostic())
    }

    @Test fun losingSuppliesAndReconnectingRecoversWithoutFaultReset() {
        val rig = ConverterBusFixture(4, true)
        rig.load.resistance = 100.0; rig.step(3)
        for (source in rig.supplies) { source.voltage = 0.0; rig.step(3) }
        assertEquals(0.0, rig.bus.voltage, 1e-7)
        for (source in rig.supplies) { source.voltage = 300.0; rig.step(3) }
        assertTrue(rig.bus.voltage > 3190.0, rig.diagnostic())
    }

    @Test fun currentLimitedParallelOutputsShareAndRecover() {
        val rig = ConverterBusFixture(4, true)
        rig.maximumOutputAmps = 2.0
        rig.load.resistance = 10.0; rig.step(3)
        assertTrue(rig.bus.voltage in 79.9..80.1, rig.diagnostic())
        rig.outputs.forEach { assertEquals(2.0, it.current, 1e-5) }
        rig.load.resistance = 10000.0; rig.step(3)
        assertTrue(rig.bus.voltage > 3199.0, rig.diagnostic())
    }

    @Test fun isolatedSharedOutputPreservesDifferentialReferenceAndEnergy() {
        val rig = ConverterBusFixture(4, true, floatingOutput = true)
        rig.load.resistance = 10.0; rig.step(3)
        val v = rig.bus.voltage - rig.returnPin!!.voltage
        assertTrue(v in 1400.0..1550.0, rig.diagnostic())
        assertTrue(rig.root.systems.any { it.captureDebugSnapshot().hasReferenceGauge() })
    }

    @Test fun insertionOrderDoesNotChangeOverloadedBusResult() {
        val voltages = mutableListOf<Double>()
        for (order in listOf(listOf(0,1,2,3), listOf(3,2,1,0), listOf(2,0,3,1))) {
            val rig = ConverterBusFixture(4, true, order = order)
            rig.load.resistance = 10.0; rig.step(3); voltages += rig.bus.voltage
        }
        for (value in voltages) assertEquals(voltages.first(), value, .001)
    }

    @Test fun repeatedElectricalTrialsCommitHistoryOnlyOnce() {
        val rig = ConverterBusFixture(4, true)
        var commits = 0
        rig.root.addProcess(ISubSystemProcessFlush { commits++ })
        rig.load.resistance = 10.0
        rig.step(1)
        assertEquals(1, commits)
        rig.step(1)
        assertEquals(2, commits)
    }

    @Test fun disabledConverterAndUnequalInputsRemainConservative() {
        val rig = ConverterBusFixture(4, true)
        listOf(241.0, 300.0, 400.0, 800.0).forEachIndexed { i, v -> rig.supplies[i].voltage = v }
        rig.load.resistance = 10.0; rig.step(3)
        rig.enabled[1] = false; rig.step(3)
        assertFalse(rig.outputs[1].enabled)
        rig.enabled[1] = true; rig.step(3)
        assertTrue(rig.bus.voltage > 1000.0, rig.diagnostic())
    }

    @Test fun seededLoadAndSetpointChangesAcrossParallelBus() {
        val rng = Random(9309)
        repeat(12) { trial ->
            val rig = ConverterBusFixture(4, trial % 2 == 0)
            rig.supplies.forEach { it.voltage = 200.0 + rng.nextDouble() * 600.0 }
            repeat(8) {
                rig.targets.indices.forEach { rig.targets[it] = 1000.0 + rng.nextDouble() * 4000.0 }
                rig.load.resistance = 10.0 + rng.nextDouble() * 2000.0
                rig.step(2)
                assertTrue(rig.bus.voltage > 1.0, rig.diagnostic())
            }
        }
    }
}

internal class ConverterBusFixture(
    count: Int,
    privateLoad: Boolean,
    floatingOutput: Boolean = false,
    order: List<Int> = (0 until count).toList()
) {
    val root = RootSystem(.01, 1)
    fun pin() = VoltageState().also(root::addState)
    private val resistors = mutableListOf<Resistor>()
    fun resistor(a: State, b: State?, r: Double) = Resistor(a, b).also {
        it.resistance = r; root.addComponent(it); resistors += it
    }
    val returnPin = if (floatingOutput) pin() else null
    val bus = pin()
    val loadPin = pin().apply { if (privateLoad) setAsPrivate() }
    private val feedWire = resistor(bus, loadPin, .001)
    val load = resistor(loadPin, returnPin, 10000.0)
    private var attached = true
    val supplies = mutableListOf<VoltageSource>()
    val inputs = mutableListOf<SwitchableVoltageSource>()
    val outputs = mutableListOf<SwitchableVoltageSource>()
    val controllers = mutableListOf<RegulatedPowerProcess>()
    val targets = DoubleArray(count) { 3200.0 }
    val enabled = BooleanArray(count) { true }
    var maximumOutputAmps = 100.0

    init {
        repeat(count) { i ->
            val feed = pin(); val primary = pin(); val secondary = pin()
            supplies += VoltageSource("feed-$i", feed, null).setVoltage(300.0).also(root::addComponent)
            resistor(feed, primary, .0637)
            resistor(secondary, bus, .0637 + i * .001)
            val input = SwitchableVoltageSource("input-$i").apply { connectTo(primary, null) }
            val output = SwitchableVoltageSource("output-$i").apply { connectTo(secondary, returnPin) }
            root.addComponent(input); root.addComponent(output)
            inputs += input; outputs += output
            controllers += RegulatedPowerProcess(primary, null, secondary, returnPin, input, output,
                { enabled[i] },
                { ConverterLimits(.1, 40000.0, 40000.0, 200.0, maximumOutputAmps, 1e6, .97, 1.0 / 256, 256.0) },
                { targets[i] })
        }
        order.forEach { root.addProcess(controllers[it]) }
    }
    fun detachLoad() {
        check(attached); root.removeComponent(load); load.breakConnection(); attached = false
    }
    fun attachLoad(ohms: Double) {
        check(!attached); load.connectTo(loadPin, returnPin); load.resistance = ohms; root.addComponent(load); attached = true
    }
    fun step(count: Int) { repeat(count) { root.step(); verify() } }
    fun diagnostic() = "bus=${bus.voltage}, R=${load.resistance}, targets=${targets.toList()}, " +
        "states=${controllers.map { it.status }}, Iin=${inputs.map { -it.current }}, Iout=${outputs.map { it.current }}"
    fun verify() {
        assertTrue(controllers.none { it.status == "NON_CONVERGENT" || it.status == "INVALID_NETWORK" }, diagnostic())
        for (i in inputs.indices) {
            assertTrue(-inputs[i].current in -1e-7..200.000201, diagnostic())
            assertTrue(outputs[i].current in -1e-7..(maximumOutputAmps * 1.000001 + 1e-7), diagnostic())
            assertTrue(balancedPower(-inputs[i].power, outputs[i].power, .97), diagnostic())
        }
        val physicalLosses = resistors.filter { it !== load || attached }.sumOf { it.power }
        val electronics = inputs.indices.sumOf { -inputs[it].power - outputs[it].power }
        val pin = supplies.sumOf { it.power }
        assertEquals(pin, physicalLosses + electronics, 1e-5 + abs(pin) * 1e-6, diagnostic())
    }
}
