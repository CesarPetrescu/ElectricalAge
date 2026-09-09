package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.state.State

/** The production regulated controller, independent of inventory/UI and reusable in circuit tests.
 * Windings remain physical resistors outside these ports. No method here advances physical time.
 */
class RegulatedPowerProcess(
    private val primary: State,
    private val primaryReference: State?,
    private val secondary: State,
    private val secondaryReference: State?,
    private val input: SwitchableVoltageSource,
    private val output: SwitchableVoltageSource,
    private val enabled: () -> Boolean,
    private val limits: () -> ConverterLimits,
    private val target: (Double) -> Double
) : ConservativePowerProcess {
    var status = "IDLE"
        private set
    private var tripped = false
    private var activeLimits: ConverterLimits? = null
    private var previousTarget = Double.NaN

    fun resetFault() { tripped = false; previousTarget = Double.NaN }

    private fun open(reason: String) {
        input.enabled = false
        output.enabled = false
        status = reason
    }

    override fun prepareStep() {
        if (tripped || !enabled()) return
        val a = probePort(primary, primaryReference, input)
        val rating = limits()
        if (!a.volts.isFinite() || a.volts < rating.minInputVolts || a.volts > rating.maxInputVolts ||
            a.ohms.isNaN() || a.ohms >= RegulatedConverter.OPEN_OHMS) return
        val requested = target(a.volts)
        if (!requested.isFinite() || requested <= 0) return
        // Retain the last operating point, especially in current limit. Re-seeding a
        // limited bus at its unloaded target each tick needlessly repeats startup.
        if (input.enabled && output.enabled && requested == previousTarget) return
        previousTarget = requested
        input.voltage = a.volts
        output.voltage = requested.coerceAtMost(rating.maxOutputVolts)
        input.enabled = true
        output.enabled = true
    }

    override fun rootSystemPreStepProcess() {
        if (tripped) { open("NON_CONVERGENT"); return }
        if (!enabled()) { open("DISABLED"); return }
        val a = probePort(primary, primaryReference, input)
        val b = probePort(secondary, secondaryReference, output)
        val rating = limits()
        activeLimits = rating
        when (val result = RegulatedConverter.solve(a, b, target(a.volts), rating)) {
            is TransferResult.Off -> open(result.reason.name)
            is TransferResult.Running -> {
                input.voltage = result.point.inputVolts
                output.voltage = result.point.outputVolts
                input.enabled = true
                output.enabled = true
                status = result.point.limitedBy.firstOrNull()?.name ?: "REGULATING"
            }
        }
    }

    override fun acceptsCandidate(): Boolean {
        if (!input.enabled && !output.enabled) return true
        val rating = activeLimits ?: return false
        val a = input.subSystem ?: return false
        val b = output.subSystem ?: return false
        val ampsIn = a.pendingValue(input.currentState)
        val ampsOut = -b.pendingValue(output.currentState)
        if (!ampsIn.isFinite() || !ampsOut.isFinite() || ampsIn < -1e-7 || ampsOut < -1e-7 ||
            ampsIn > rating.maxInputAmps * (1 + 1e-6) || ampsOut > rating.maxOutputAmps * (1 + 1e-6)) return false
        return balancedPower(-pendingSourcePower(input), pendingSourcePower(output), rating.electronicsEfficiency)
    }

    override fun failClosed() { tripped = true; open("NON_CONVERGENT") }
    override fun connectedSystems(): Set<SubSystem> = setOfNotNull(primary.subSystem, secondary.subSystem)
    override fun trialSources(): List<SwitchableVoltageSource> = listOf(input, output)
}
