package mods.eln.devtest

import kotlin.math.abs
import kotlin.math.max

/** Independent checks for the native fixtures. Never changes production simulation state. */
internal object NativeCampaignOracles {
    fun near(actual: Double, expected: Double, tolerance: Double, label: String) {
        check(actual.isFinite() && expected.isFinite() && tolerance.isFinite() && tolerance >= 0.0 &&
            abs(actual - expected) <= tolerance) {
            "$label: expected $expected +/- $tolerance, observed $actual"
        }
    }

    fun inputs(requested: List<Double>, observed: List<Double>, signalVolts: Double) {
        check(signalVolts.isFinite() && signalVolts > 0.0)
        check(requested.size == observed.size && requested.isNotEmpty()) { "Missing signal inputs" }
        requested.zip(observed).forEachIndexed { index, (target, measured) ->
            check(target.isFinite() && target in 0.0..signalVolts) { "Input $index outside the signal domain: $target V" }
            near(measured, target, signalVolts * .02, "Input $index (requested=$requested observed=$observed)")
        }
    }

    fun digitalOutput(volts: Double, high: Boolean, signalVolts: Double) =
        near(volts, if (high) signalVolts else 0.0, signalVolts * .05, "Digital output high=$high")

    /** This is a reference inequality, not a call to ShaftNetwork.wouldExplode. Units: rad/s. */
    fun unsafeRigidMerge(first: Double, second: Double): Boolean {
        require(first.isFinite() && second.isFinite() && first >= 0.0 && second >= 0.0)
        return abs(first - second) > 50.0 - .1 * max(first, second)
    }

    fun batteryCurrent(total: Double, external: Double, internal: Double, referenceLeakBound: Double) {
        check(external >= -1e-8 && internal >= -1e-8 && referenceLeakBound >= 0.0)
        near(total, external + internal, referenceLeakBound + 1e-6 + abs(total) * 1e-5,
            "Battery current balance (external=$external internal=$internal)")
    }
}
