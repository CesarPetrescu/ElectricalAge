package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import mods.eln.sim.mna.state.State
import kotlin.math.abs

/** Fixed ratio remains reversible. Windings are actual resistors outside these internal ports. */
class SafeTransformerProcess(
    private val primary: State,
    private val secondary: State,
    private val input: SwitchableVoltageSource,
    private val output: SwitchableVoltageSource,
    private val populated: () -> Boolean
) : TransformerInterSystemProcess(primary, secondary, input, output), ConservativePowerProcess {
    var maximumPrimaryVoltage = 120_000.0
    var maximumSecondaryVoltage = 120_000.0
    var enabled = true
    var voltageTarget: Double? = null
    var status = "IDLE"
        private set
    private var tripped = false

    fun resetFault() { tripped = false }

    override fun rootSystemPreStepProcess() {
        if (tripped) { open("NON_CONVERGENT"); return }
        if (!enabled || !populated()) { open("DISABLED"); return }
        val target = voltageTarget
        if (target != null) {
            // Zero is an off request, not a tiny positive gain or a zero-volt short.
            if (!target.isFinite() || target < 0.0) { open("INVALID_CONTROL"); return }
            if (target == 0.0) { open("DISABLED"); return }
        }
        val a = probePort(primary, null, input)
        val b = probePort(secondary, null, output)
        if (!a.volts.isFinite() || !b.volts.isFinite() || a.ohms.isNaN() || b.ohms.isNaN() ||
            a.ohms < 0.0 || b.ohms < 0.0) { open("INVALID_NETWORK"); return }
        if (target != null && a.volts > 0.0) ratio = (target / a.volts).coerceIn(1.0 / 256, 256.0)
        if (!ratio.isFinite() || ratio <= 0) { open("INVALID_CONTROL"); return }
        val point = when {
            abs(a.volts) < 1e-12 && abs(b.volts) < 1e-12 -> null
            a.ohms >= RegulatedConverter.OPEN_OHMS && b.ohms >= RegulatedConverter.OPEN_OHMS -> null
            b.ohms >= RegulatedConverter.OPEN_OHMS -> OperatingPoint(a.volts, a.volts * ratio, 0.0, 0.0, 0.0, 0.0, 0.0)
            a.ohms >= RegulatedConverter.OPEN_OHMS -> OperatingPoint(b.volts / ratio, b.volts, 0.0, 0.0, 0.0, 0.0, 0.0)
            else -> RatioTransformer.solve(a, b, ratio)
        }
        if (point == null) { open("NO_INPUT"); return }
        if (!point.inputVolts.isFinite() || !point.outputVolts.isFinite()) { open("INVALID_NETWORK"); return }
        if (abs(point.inputVolts) > maximumPrimaryVoltage || abs(point.outputVolts) > maximumSecondaryVoltage) {
            open("VOLTAGE_LIMIT"); return
        }
        input.voltage = point.inputVolts; output.voltage = point.outputVolts
        input.enabled = true; output.enabled = true
        status = "TRANSFERRING"
    }

    private fun open(reason: String) {
        input.enabled = false
        output.enabled = false
        status = reason
    }

    override fun acceptsCandidate(): Boolean {
        if (!input.enabled && !output.enabled) return true
        return balancedPower(-pendingSourcePower(input), pendingSourcePower(output))
    }

    override fun trialSources(): List<SwitchableVoltageSource> = listOf(input, output)

    override fun failClosed() { tripped = true; open("NON_CONVERGENT") }
    override fun connectedSystems(): Set<SubSystem> = setOfNotNull(primary.subSystem, secondary.subSystem)
}
