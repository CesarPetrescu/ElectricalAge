package mods.eln.sim.power

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState
import mods.eln.transparentnode.DcDcControl
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real production source branches, controller processes and MNA solver; no mocked circuit solver. */
class ConverterControlRuntimeRegressionTest {
    private class Circuit(inputOhms: Double = 1.0, loadOhms: Double = 1000.0) {
        val root = RootSystem(.01, 1)
        val feed = VoltageState()
        val primary = VoltageState()
        val secondary = VoltageState()
        val supply = VoltageSource("supply", feed, null).setVoltage(50.0)
        val winding = Resistor(feed, primary).apply { resistance = inputOhms }
        val load = Resistor(secondary, null).apply { resistance = loadOhms }
        val input = SwitchableVoltageSource("input").apply { connectTo(primary, null) }
        val output = SwitchableVoltageSource("output").apply { connectTo(secondary, null) }

        init {
            listOf(feed, primary, secondary).forEach(root::addState)
            root.addComponent(supply)
            root.addComponent(winding)
            root.addComponent(load)
            root.addComponent(input)
            root.addComponent(output)
        }

        fun settle() { repeat(8) { root.step() } }
    }

    private fun close(actual: Double, expected: Double) =
        assertEquals(expected, actual, 1e-6 * maxOf(1.0, abs(expected)))

    private fun assertOpen(c: Circuit) {
        assertFalse(c.input.enabled)
        assertFalse(c.output.enabled)
        close(c.input.current, 0.0)
        close(c.output.current, 0.0)
        // An open input still measures the supply; commanding 0 V would short it through the winding.
        close(c.primary.voltage, 50.0)
        close(c.load.power, 0.0)
    }

    private fun limits() = ConverterLimits(.1, 120_000.0, 120_000.0,
        1000.0, 1000.0, 1_000_000.0, .97, 1.0 / 256, 256.0)

    @Test fun regulatedInvalidRequestsOpenBothBranchesAndRecover() {
        val c = Circuit()
        var request = 100.0
        var throwControlFault = false
        val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
            { true }, { limits() }, {
                require(!throwControlFault) { "Invalid active signal" }
                request
            })
        c.root.addProcess(process)
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            request = 100.0
            c.settle()
            assertTrue(c.output.enabled)
            assertTrue(c.load.power > 0.0)
            request = invalid
            c.settle()
            assertEquals("INVALID_CONTROL", process.status)
            assertOpen(c)
        }
        request = 100.0
        throwControlFault = true
        c.settle()
        assertEquals("INVALID_CONTROL", process.status)
        assertOpen(c)
        throwControlFault = false
        c.settle()
        assertTrue(c.output.enabled)
        close(c.secondary.voltage, 100.0)
    }

    @Test fun regulatedInvalidRatingCallbackCannotEscapeTheTick() {
        val c = Circuit()
        val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
            { true }, { throw IllegalArgumentException("Invalid winding rating") }, { 100.0 })
        c.root.addProcess(process)
        c.settle()
        assertEquals("INVALID_CONTROL", process.status)
        assertOpen(c)
    }

    @Test fun regulatedZeroTargetAndDisableAreOpenCircuitsNotShorts() {
        val c = Circuit()
        var request = 100.0
        var enabled = true
        val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
            { enabled }, { limits() }, { request })
        c.root.addProcess(process)
        c.settle()
        assertTrue(c.output.enabled)
        request = 0.0
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
        request = 100.0
        c.settle()
        assertTrue(c.output.enabled)
        enabled = false
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
    }

    @Test fun manualModesOperateWithAnInvalidInactiveSignal() {
        for (mode in listOf("VOLTAGE", "RATIO")) {
            val c = Circuit()
            val control = DcDcControl().apply {
                this.mode = mode
                value = if (mode == "VOLTAGE") 100.0 else 2.0
            }
            val process = RegulatedPowerProcess(c.primary, null, c.secondary, null, c.input, c.output,
                { control.enabled }, { limits() }, { vin ->
                    if (control.mode == "VOLTAGE") control.value
                    else vin * control.ratio(ConverterKind.BUCK_BOOST, Double.NaN)
                })
            c.root.addProcess(process)
            c.settle()
            assertTrue(c.input.enabled)
            assertTrue(c.output.enabled)
            close(c.secondary.voltage, 100.0)
            close(c.output.power, -c.input.power * .97)
        }
    }

    @Test fun safeTransformerZeroAndInvalidVoltageTargetsDoNotBecomeMinimumGain() {
        val c = Circuit()
        val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
        c.root.addProcess(process)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            process.voltageTarget = 100.0
            c.settle()
            assertTrue(c.output.enabled)
            assertTrue(c.load.power > 0.0)
            process.voltageTarget = invalid
            c.settle()
            assertEquals(if (invalid == 0.0) "DISABLED" else "INVALID_CONTROL", process.status)
            assertOpen(c)
        }
    }

    @Test fun safeTransformerDisabledAndIncompleteStatesDoNotReportTransferring() {
        val c = Circuit()
        var populated = true
        val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { populated }
        c.root.addProcess(process)
        c.settle()
        assertEquals("TRANSFERRING", process.status)
        process.enabled = false
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
        process.enabled = true
        c.settle()
        assertTrue(c.output.enabled)
        populated = false
        c.settle()
        assertEquals("DISABLED", process.status)
        assertOpen(c)
    }

    @Test fun fixedStepDownUnityAndStepUpRatiosRemainLoadedAndPowerConserving() {
        for (ratio in listOf(.25, 1.0, 4.0)) {
            val c = Circuit()
            c.root.addProcess(SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
                .apply { this.ratio = ratio })
            c.settle()
            val expectedPrimary = 50.0 / (1.0 + ratio * ratio / 1000.0)
            close(c.primary.voltage, expectedPrimary)
            close(c.secondary.voltage, expectedPrimary * ratio)
            close(-c.input.power, c.output.power)
            close(c.supply.power, c.winding.power + c.load.power)
        }
    }

    @Test fun longerSeriesWireChangesLoadedVoltageNotTheInternalTransformerRatio() {
        var previousOutput = Double.POSITIVE_INFINITY
        for (wireOhms in listOf(1.0, 10.0, 100.0)) {
            val c = Circuit(inputOhms = wireOhms)
            val process = SafeTransformerProcess(c.primary, c.secondary, c.input, c.output) { true }
                .apply { ratio = 2.0 }
            c.root.addProcess(process)
            c.settle()
            close(c.secondary.voltage, c.primary.voltage * 2.0)
            assertTrue(c.secondary.voltage < previousOutput)
            close(c.supply.power, c.winding.power + c.load.power)
            previousOutput = c.secondary.voltage
        }
    }
}
