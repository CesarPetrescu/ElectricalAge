package mods.eln.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mods.eln.misc.FunctionTable
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState

class BatteryProcessTest {
    private fun battery(): BatteryProcess = BatteryProcess(null, null,
        FunctionTable(doubleArrayOf(0.0, 1.0), 1.0), 10.0, VoltageSource("test"), ThermalLoad()).apply {
        QNominal = 100.0; uNominal = 12.0; charge = .5
    }

    @Test fun chargeAndDischargeUseSourceCurrentSign() {
        val b = battery()
        b.voltageSource.currentState.state = -1.0
        b.process(1.0)
        assertEquals(.49, b.charge, 1e-9)
        b.voltageSource.currentState.state = 1.0
        b.process(1.0)
        assertEquals(.5, b.charge, 1e-9)
    }
    @Test fun emptyBatteryHasNoVoltageOrStoredEnergy() {
        val b = battery(); b.charge = 0.0
        assertEquals(0.0, b.u); assertEquals(0.0, b.energy)
    }
    @Test fun fullBatteryDoesNotStoreUnlimitedEnergy() {
        val b = battery(); b.charge = 1.0
        b.voltageSource.currentState.state = 1000.0
        repeat(50) { b.process(1.0) }
        assertEquals(1.0, b.charge); assertEquals(b.energyMax, b.energy, .0001)
    }
    @Test fun invalidLifeAndChargeCannotInjectNanIntoCircuit() {
        val b = battery(); b.life = 0.0; b.Q = Double.NaN
        b.process(.01)
        assertTrue(b.life > 0.0 && b.charge.isFinite() && b.u.isFinite())
    }
    @Test fun timeStepPartitionDoesNotChangeCharge() {
        val a = battery(); val b = battery()
        a.voltageSource.currentState.state = -1.0; b.voltageSource.currentState.state = -1.0
        a.process(1.0); repeat(100) { b.process(.01) }
        assertEquals(a.Q, b.Q, 1e-9)
    }
    @Test fun invalidStepsDoNotChangeCharge() {
        val b = battery()
        for (dt in listOf(0.0, -1.0, Double.NaN)) b.process(dt)
        assertEquals(.5, b.charge)
    }
    @Test
    fun processUpdatesVoltageAndWasteHeatOnRecharge() {
        val voltageFunction = FunctionTable(doubleArrayOf(1.0, 1.0), 1.0)
        val source = VoltageSource("batt")
        val thermal = ThermalLoad()
        val process = BatteryProcess(VoltageState(), VoltageState(), voltageFunction, 10.0, source, thermal)
        process.QNominal = 1.0
        process.uNominal = 10.0
        process.Q = 0.5
        process.isRechargeable = false
        source.currentState.state = 1.0

        process.process(1.0)

        assertEquals(10.0, source.voltage)
        assertTrue(thermal.PcTemp > 0.0)
    }

    @Test
    fun changeLifeScalesCharge() {
        val voltageFunction = FunctionTable(doubleArrayOf(1.0, 1.0), 1.0)
        val process = BatteryProcess(null, null, voltageFunction, 10.0, VoltageSource("b"), ThermalLoad())
        process.QNominal = 1.0
        process.uNominal = 5.0
        process.Q = 1.0
        process.life = 1.0

        process.changeLife(0.5)

        assertEquals(0.5, process.life)
        assertEquals(0.5, process.Q)
    }

    @Test
    fun energyComputationsArePositive() {
        val voltageFunction = FunctionTable(doubleArrayOf(1.0, 1.0), 1.0)
        val process = BatteryProcess(null, null, voltageFunction, 10.0, VoltageSource("b"), ThermalLoad())
        process.QNominal = 2.0
        process.uNominal = 4.0
        process.Q = 1.0
        process.life = 1.0

        assertTrue(process.energy > 0.0)
        assertTrue(process.energyMax > 0.0)
        assertEquals(process.computeVoltage(), process.u)
    }
}
