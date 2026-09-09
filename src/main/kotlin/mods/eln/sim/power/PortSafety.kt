package mods.eln.sim.power

import kotlin.math.abs
import kotlin.math.max

data class PortVoltages(val positive: Double, val reference: Double) {
    init { require(positive.isFinite() && reference.isFinite()) }
    val differential: Double get() = positive - reference
    val peakToGround: Double get() = max(abs(positive), abs(reference))
}

/** Endpoint approximation for the separate primary-to-secondary insulation check. */
fun primarySecondaryInsulationStress(primary: PortVoltages, secondary: PortVoltages): Double = maxOf(
    abs(primary.positive - secondary.positive),
    abs(primary.positive - secondary.reference),
    abs(primary.reference - secondary.positive),
    abs(primary.reference - secondary.reference)
)
