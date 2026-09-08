package mods.eln.audit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import mods.eln.disableLog4jJmx
import mods.eln.misc.FunctionTable
import mods.eln.sim.BatteryProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Capacitor
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.nbt.NbtResistor
import mods.eln.transparentnode.evaporative.EvaporationModel
import net.minecraft.nbt.CompoundTag

/** Imported from the independent QA baseline; mandatory under the normal test task. */
class IndependentElectricalAuditTest {
    private fun rc(): Triple<RootSystem, Capacitor, Resistor> {
        disableLog4jJmx()
        val root = RootSystem(.01, 1)
        val sourcePin = VoltageState()
        val capacitorPin = VoltageState()
        val source = VoltageSource("audit", sourcePin, null).setVoltage(10.0)
        val resistor = Resistor(sourcePin, capacitorPin).setResistance(100.0)
        val capacitor = Capacitor(capacitorPin, null).apply { setCoulombs(.001) }
        root.addState(sourcePin); root.addState(capacitorPin)
        root.addComponent(source); root.addComponent(resistor); root.addComponent(capacitor)
        return Triple(root, capacitor, resistor)
    }

    @Test fun rcChargingVoltageMatchesBackwardEuler() {
        val (root, capacitor, _) = rc()
        root.step()
        assertEquals(10.0 / 11.0, capacitor.voltage, 1e-8)
        repeat(99) { root.step() }
        assertEquals(10.0 * (1.0 - Math.pow(10.0 / 11.0, 100.0)), capacitor.voltage, 1e-7)
    }

    @Test fun capacitorReportsActualChargingCurrent() {
        val (root, capacitor, resistor) = rc()
        root.step()
        val expected = .001 * capacitor.voltage / .01
        println("AUDIT_CAPACITOR expectedA=$expected resistorA=${resistor.current} reportedA=${capacitor.current}")
        assertTrue(expected > .09, "Fixture must actually be charging")
        assertEquals(expected, abs(resistor.current), 1e-8)
        assertEquals(expected, capacitor.current, 1e-8,
            "PowerCapacitorElement's multimeter reads this getter; 0 A is false during charging")
    }

    @Test fun zeroResistanceCannotInsertInfiniteConductance() {
        val r = Resistor().setResistance(10.0)
        r.setResistance(0.0)
        assertEquals(10.0, r.resistance)
        assertEquals(.1, r.resistanceInverse)
        println("AUDIT_ZERO_RESISTOR R=${r.resistance} conductance=${r.resistanceInverse}")
        assertTrue(r.resistanceInverse.isFinite(), "Reject zero or use an explicit finite short-circuit floor")
    }

    @Test fun missingResistorNbtDoesNotCreateInfiniteConductance() {
        val r = NbtResistor("audit", VoltageState(), null)
        r.setResistance(10.0)
        r.readFromNBT(CompoundTag(), "prefix")
        println("AUDIT_MISSING_NBT R=${r.resistance} conductance=${r.resistanceInverse}")
        assertTrue(r.resistanceInverse.isFinite() && r.resistance > 0,
            "An absent R tag must preserve a safe default, not deserialize as a 0-ohm division")
    }

    @Test fun positiveResistorValuesFollowOhmsLaw() {
        for (ohms in listOf(.001, .1, 1.0, 10.0, 100.0, 1e9)) {
            val a = VoltageState().apply { state = 48.0 }
            val r = Resistor(a, null).setResistance(ohms)
            assertEquals(48.0 / ohms, r.current, 1e-6)
            assertTrue(r.power >= 0 && r.power.isFinite())
        }
    }

    @Test fun validResistorNbtRoundTrips() {
        val saved = NbtResistor("audit", VoltageState(), null).apply { setResistance(470.0) }
        val tag = CompoundTag(); saved.writeToNBT(tag, "prefix")
        val loaded = NbtResistor("audit", VoltageState(), null); loaded.readFromNBT(tag, "prefix")
        assertEquals(470.0, loaded.resistance)
    }

    @Test fun batteryChargeIntegratesCoulombsAndRejectsInvalidTimesteps() {
        val source = VoltageSource("audit-battery")
        val b = BatteryProcess(null, null, FunctionTable(doubleArrayOf(1.0, 1.0), 1.0), 10.0, source, ThermalLoad())
        b.QNominal = 100.0; b.uNominal = 12.0; b.charge = .5
        source.currentState.state = -2.0
        repeat(400) { b.process(.0025) }
        assertEquals(.48, b.charge, 1e-10)
        assertEquals(576.0, b.energy, 1e-8)
        for (dt in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) b.process(dt)
        assertEquals(.48, b.charge, 1e-10)
    }

    @Test fun evaporationWaterAndLatentHeatBudgets() {
        var cases = 0
        for (air in listOf(5.0, 20.0, 35.0)) for (rh in listOf(10.0, 50.0, 90.0))
            for (surface in listOf(10.0, 30.0, 60.0, 90.0)) for (water in listOf(0.0, .001, 100.0))
                for (dt in listOf(.0025, .05, 1.0)) {
                    val x = EvaporationModel.step(surface, air, rh, 40.0, 10000.0, water, 150.0, 30000.0, dt, true)
                    assertTrue(x.waterMb() >= 0 && x.waterMb() <= water + 1e-10)
                    assertEquals(x.evaporationWatts() * dt,
                        x.waterMb() * .001 * EvaporationModel.latentHeat(surface), 1e-6)
                    assertTrue(x.netCoolingWatts().isFinite())
                    cases++
                }
        println("AUDIT_EVAPORATION_CASES=$cases")
    }
}
