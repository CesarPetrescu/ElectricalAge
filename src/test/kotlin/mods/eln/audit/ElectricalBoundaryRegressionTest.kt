package mods.eln.audit

import mods.eln.disableLog4jJmx
import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.misc.ISubSystemProcessI
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.nbt.NbtResistor
import net.minecraft.nbt.*
import kotlin.math.abs
import kotlin.test.*

class ElectricalBoundaryRegressionTest {
    init { disableLog4jJmx() }

    private val invalid = listOf(0.0, -0.0, -1.0, Double.NaN,
        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE)

    @Test fun rejectedInputsPreserveBothFieldsWithoutDirtying() {
        val r = object : Resistor() {
            var invalidations = 0
            override fun dirty() { invalidations++ }
        }
        r.setResistance(10.0)
        for (bad in invalid) repeat(2) {
            assertSame(r, r.setResistance(bad))
            assertEquals(10.0, r.resistance)
            assertEquals(.1, r.resistanceInverse)
            assertEquals(1, r.invalidations)
        }
        r.setResistance(20.0)
        assertEquals(.05, r.resistanceInverse)
        assertEquals(2, r.invalidations)
    }

    @Test fun constructorDefaultsSurviveInvalidValues() {
        for (bad in invalid) {
            val r = Resistor().setResistance(bad)
            assertEquals(MnaConst.highImpedance, r.resistance)
            assertEquals(1 / MnaConst.highImpedance, r.resistanceInverse)
        }
    }

    @Test fun explicitShortAndFiniteExtremeValuesAreNotClamped() {
        for (r in listOf(MnaConst.noImpedance, 1e-12, 1e-300, 1e300, Double.MAX_VALUE)) {
            val resistor = Resistor().setResistance(r)
            assertEquals(r, resistor.resistance)
            assertEquals(1 / r, resistor.resistanceInverse)
            assertTrue(resistor.resistanceInverse.isFinite() && resistor.resistanceInverse > 0)
        }
    }

    @Test fun malformedNbtPreservesConfiguredResistanceAndCanBeRepaired() {
        val tags = invalid.map { DoubleTag.valueOf(it) } + listOf(StringTag.valueOf("10"), CompoundTag(), ListTag())
        for (value in tags) {
            val r = NbtResistor("named", VoltageState(), null).apply { resistance = 10.0 }
            val tag = CompoundTag().apply { put("prefixR", value) }
            repeat(3) { r.readFromNBT(tag, "prefix") }
            assertEquals(10.0, r.resistance)
            assertEquals(.1, r.resistanceInverse)
            r.writeToNBT(tag, "prefix")
            assertEquals(10.0, tag.getDouble("prefixR"))
            assertEquals(setOf("prefixR"), tag.allKeys)
        }
    }

    @Test fun missingNbtPreservesDefaultAndDoesNotReadAnotherPrefix() {
        val r = NbtResistor("named", VoltageState(), null)
        val tag = CompoundTag().apply { putDouble("otherR", 470.0) }
        r.readFromNBT(tag, "prefix")
        assertEquals(MnaConst.highImpedance, r.resistance)
        r.resistance = 12.0
        r.readFromNBT(tag, "prefix")
        assertEquals(12.0, r.resistance)
        r.writeToNBT(tag, "prefix")
        assertEquals(setOf("prefixR", "otherR"), tag.allKeys)
    }

    @Test fun everyNumericNbtTypeLoadsAndRepeatedPrefixesStayStable() {
        val values = listOf(ByteTag.valueOf(12.toByte()), ShortTag.valueOf(12.toShort()),
            IntTag.valueOf(12), LongTag.valueOf(12), FloatTag.valueOf(12f), DoubleTag.valueOf(12.0))
        val r = NbtResistor("named", VoltageState(), null)
        for (value in values) for (prefix in listOf("", "one", "two", "one")) {
            val tag = CompoundTag().apply { put(prefix + "R", value) }
            r.readFromNBT(tag, prefix)
            assertEquals(12.0, r.resistance)
            r.writeToNBT(tag, prefix)
            assertEquals(setOf(prefix + "R"), tag.allKeys)
        }
    }

    @Test fun rejectedChildResistanceDoesNotCorruptCombinedLine() {
        val a = Resistor().setResistance(2.0)
        val b = Resistor().setResistance(3.0)
        val line = Line().apply { resistors.add(a); resistors.add(b); recalculateResistance() }
        assertEquals(5.0, line.resistance)
        for (bad in invalid) { a.setResistance(bad); line.recalculateResistance(); assertEquals(5.0, line.resistance) }
        a.setResistance(7.0); line.recalculateResistance()
        assertEquals(10.0, line.resistance)
    }

    private class RC(val dt: Double = .01, reversed: Boolean = false) {
        val system = SubSystem(null, dt)
        val sourcePin = VoltageState()
        val capPin = VoltageState()
        val source = VoltageSource("regression", sourcePin, null).setVoltage(10.0)
        val resistor = Resistor(sourcePin, capPin).setResistance(100.0)
        val capacitor = (if (reversed) Capacitor(null, capPin) else Capacitor(capPin, null)).apply { setCoulombs(.001) }
        init {
            system.addState(sourcePin); system.addState(capPin)
            system.addComponent(source); system.addComponent(resistor); system.addComponent(capacitor)
        }
    }

    @Test fun signedChargingAndDischargingMatchEachCompletedStep() {
        for (dt in listOf(.0025, .01, .1)) for (reversed in listOf(false, true)) {
            val rc = RC(dt, reversed)
            assertEquals(0.0, rc.capacitor.current)
            for (sourceV in listOf(10.0, 0.0, -10.0, 10.0)) {
                rc.source.voltage = sourceV
                repeat(20) {
                    val old = rc.capacitor.voltage
                    rc.system.step()
                    val expected = .001 * (rc.capacitor.voltage - old) / dt
                    assertEquals(expected, rc.capacitor.current, 1e-9)
                    assertEquals(if (reversed) -rc.resistor.current else rc.resistor.current, rc.capacitor.current, 1e-9)
                    assertEquals(rc.capacitor.voltage * rc.capacitor.voltage * .001 / 2, rc.capacitor.energy, 1e-12)
                }
            }
        }
    }

    @Test fun steadyStateCurrentDecaysWithoutChangingVoltageSolution() {
        val rc = RC()
        repeat(500) { rc.system.step() }
        assertEquals(10.0, rc.capacitor.voltage, 1e-9)
        assertEquals(0.0, rc.capacitor.current, 1e-10)
    }

    @Test fun speculativeQueriesAndRepeatedReadsDoNotCommitCurrentOrHistory() {
        val rc = RC()
        rc.system.step()
        val oldCurrent = rc.capacitor.current
        val oldVoltage = rc.capacitor.voltage
        repeat(3) { rc.system.solve(rc.capPin); assertEquals(oldCurrent, rc.capacitor.current); assertEquals(oldVoltage, rc.capacitor.voltage) }
        rc.system.stepCalc()
        assertEquals(oldCurrent, rc.capacitor.current)
        // Even a query against temporary pin values must not replace the real step's sample.
        rc.capPin.state = 123.0
        rc.system.solve(rc.capPin)
        rc.capPin.state = oldVoltage
        rc.system.stepFlush()
        assertEquals(.001 * (rc.capacitor.voltage - oldVoltage) / rc.dt, rc.capacitor.current, 1e-9)
        val current = rc.capacitor.current
        repeat(3) { assertEquals(current, rc.capacitor.current) }
    }

    @Test fun detachAndRejoinWithDifferentTimestepResetsTransientTelemetry() {
        val rc = RC()
        rc.system.step()
        assertTrue(rc.capacitor.current > 0)
        rc.system.removeComponent(rc.capacitor)
        assertEquals(0.0, rc.capacitor.current)
        rc.system.step()
        assertEquals(0.0, rc.capacitor.current) // Removed flush callback must not run.
        val next = SubSystem(null, .02)
        next.addState(rc.sourcePin); next.addState(rc.capPin)
        next.addComponent(rc.source); next.addComponent(rc.resistor); next.addComponent(rc.capacitor)
        rc.source.voltage = 0.0
        val previous = rc.capacitor.voltage
        next.step()
        assertEquals(.001 * (rc.capacitor.voltage - previous) / .02, rc.capacitor.current, 1e-9)
        assertTrue(rc.capacitor.current < 0)
    }

    @Test fun singularAndNonFiniteSolutionsDoNotReportFictitiousCurrent() {
        val rc = RC()
        rc.system.step()
        val floating = VoltageState()
        rc.system.addState(floating); rc.system.step()
        assertEquals(0.0, rc.capacitor.current)
        rc.system.removeState(floating); rc.system.step()
        assertTrue(rc.capacitor.current > 0)
        val invalidRhs = ISubSystemProcessI { it.addToI(rc.capPin, Double.NaN) }
        rc.system.addProcess(invalidRhs); rc.system.step()
        assertEquals(0.0, rc.capacitor.current)
        rc.system.removeProcess(invalidRhs)
        rc.sourcePin.state = 0.0; rc.capPin.state = 0.0
        rc.system.step()
        assertEquals(10.0 / 11, rc.capacitor.voltage, 1e-8)
        assertEquals(1.0 / 11, rc.capacitor.current, 1e-8)
    }

    @Test fun capacitanceChangesAndZeroCapacitanceDoNotInventTelemetry() {
        val rc = RC()
        rc.system.step()
        val oldVoltage = rc.capacitor.voltage
        rc.capacitor.setCoulombs(.002)
        rc.system.step()
        assertEquals(.002 * (rc.capacitor.voltage - oldVoltage) / rc.dt, rc.capacitor.current, 1e-9)
        rc.capacitor.setCoulombs(0.0); rc.system.step()
        assertEquals(0.0, rc.capacitor.current)
        assertTrue(abs(rc.capacitor.voltage - 10) < 1e-8)
    }
}
