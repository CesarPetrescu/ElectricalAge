package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.misc.IRootSystemPreStepProcess
import mods.eln.sim.mna.state.State
import kotlin.math.abs
import kotlin.math.max

/** Participant in the root solver's trial/commit boundary. */
interface ConservativePowerProcess : IRootSystemPreStepProcess {
    /** Initial trial commands only. Never advances a physical state or bypasses acceptance. */
    fun prepareStep() {}
    /** Voltage commands eligible for a bounded algebraic convergence correction. */
    fun trialSources(): List<SwitchableVoltageSource> = emptyList()
    fun acceptsCandidate(): Boolean
    fun failClosed()
    fun connectedSystems(): Set<SubSystem>
}

/** Precision-preserving affine Thevenin probe; never commits physical state. */
fun probePort(positive: State, negative: State?, source: SwitchableVoltageSource): PortThevenin {
    val system = positive.subSystem ?: return PortThevenin(0.0, Double.POSITIVE_INFINITY)
    if (negative != null && negative.subSystem !== system) return PortThevenin(Double.NaN, Double.NaN)
    val savedEnabled = source.enabled
    try {
        source.enabled = true
        val response = system.sourceThevenin(source)
        return PortThevenin(response.voltage, response.resistance)
    } finally {
        source.enabled = savedEnabled
    }
}

fun pendingSourcePower(source: SwitchableVoltageSource): Double {
    if (!source.enabled) return 0.0
    val system = source.subSystem ?: return Double.NaN
    return -source.voltage * system.pendingValue(source.currentState)
}

fun balancedPower(inputWatts: Double, outputWatts: Double, efficiency: Double = 1.0): Boolean =
    inputWatts.isFinite() && outputWatts.isFinite() &&
        abs(inputWatts * efficiency - outputWatts) <= 1e-7 + 1e-6 * max(abs(inputWatts), abs(outputWatts))
