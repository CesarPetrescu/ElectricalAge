package mods.eln.sim.power

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sim.mna.misc.IRootSystemPreStepProcess
import mods.eln.sim.mna.state.State
import kotlin.math.abs
import kotlin.math.max

/** Participant in the root solver's trial/commit boundary. */
interface ConservativePowerProcess : IRootSystemPreStepProcess {
    fun acceptsCandidate(): Boolean
    fun failClosed()
    fun connectedSystems(): Set<SubSystem>
}

/** Two-point, differential Thevenin probe. Always restores the actual command and enable state. */
fun probePort(positive: State, negative: State?, source: SwitchableVoltageSource): PortThevenin {
    val system = positive.subSystem ?: return PortThevenin(0.0, Double.POSITIVE_INFINITY)
    if (negative != null && negative.subSystem !== system) return PortThevenin(Double.NaN, Double.NaN)
    val savedVolts = source.voltage
    val savedEnabled = source.enabled
    try {
        source.enabled = true
        val v0 = (positive.state - (negative?.state ?: 0.0)).takeIf { it.isFinite() } ?: 0.0
        val delta = max(1.0, abs(v0) * 1e-4).coerceAtMost(100.0)
        source.voltage = v0
        val i0 = system.solveChecked(source.currentState)
        source.voltage = v0 + delta
        val i1 = system.solveChecked(source.currentState)
        if (!i0.isFinite() || !i1.isFinite()) return PortThevenin(Double.NaN, Double.NaN)
        if (abs(i0 - i1) <= 1e-18) return PortThevenin(0.0, Double.POSITIVE_INFINITY)
        val resistance = delta / (i0 - i1)
        if (!resistance.isFinite() || resistance < 0) return PortThevenin(Double.NaN, Double.NaN)
        if (resistance >= RegulatedConverter.OPEN_OHMS) return PortThevenin(0.0, Double.POSITIVE_INFINITY)
        return PortThevenin(v0 + resistance * i0, resistance)
    } finally {
        source.voltage = savedVolts
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
