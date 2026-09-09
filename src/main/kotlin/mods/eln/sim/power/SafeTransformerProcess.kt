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
        if (tripped || !enabled || !populated()) { open(); return }
        val a = probePort(primary, null, input)
        val b = probePort(secondary, null, output)
        voltageTarget?.let { target ->
            if (a.volts > 0 && a.volts.isFinite()) ratio = (target / a.volts).coerceIn(1.0 / 256, 256.0)
        }
        if (!ratio.isFinite() || ratio <= 0) { status = "INVALID_CONTROL"; open(); return }
        val point = when {
            !a.volts.isFinite() || !b.volts.isFinite() -> null
            a.ohms >= RegulatedConverter.OPEN_OHMS && b.ohms >= RegulatedConverter.OPEN_OHMS -> null
            b.ohms >= RegulatedConverter.OPEN_OHMS -> OperatingPoint(a.volts, a.volts * ratio, 0.0, 0.0, 0.0, 0.0, 0.0)
            a.ohms >= RegulatedConverter.OPEN_OHMS -> OperatingPoint(b.volts / ratio, b.volts, 0.0, 0.0, 0.0, 0.0, 0.0)
            else -> RatioTransformer.solve(a, b, ratio)
        }
        if (point == null) { status = "NO_INPUT"; open(); return }
        if (abs(point.inputVolts) > maximumPrimaryVoltage || abs(point.outputVolts) > maximumSecondaryVoltage) {
            status = "VOLTAGE_LIMIT"; open(); return
        }
        input.voltage = point.inputVolts; output.voltage = point.outputVolts
        input.enabled = true; output.enabled = true
        status = "TRANSFERRING"
    }

    private fun open() { input.enabled = false; output.enabled = false }

    override fun acceptsCandidate(): Boolean {
        if (!input.enabled && !output.enabled) return true
        return balancedPower(-pendingSourcePower(input), pendingSourcePower(output))
    }

    override fun failClosed() { open(); tripped = true; status = "NON_CONVERGENT" }
    override fun connectedSystems(): Set<SubSystem> = setOfNotNull(primary.subSystem, secondary.subSystem)
}
