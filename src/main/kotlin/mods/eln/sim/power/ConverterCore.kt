package mods.eln.sim.power

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Passive linear network seen from an INTERNAL converter port, after winding resistors.
 * Positive infinity resistance denotes an open circuit. Negative resistance is not supported.
 * Output open-circuit voltage may be negative: differential isolated references can do this.
 */
data class PortThevenin(val volts: Double, val ohms: Double)

data class ConverterLimits(
    val minInputVolts: Double,
    val maxInputVolts: Double,
    val maxOutputVolts: Double,
    val maxInputAmps: Double,
    val maxOutputAmps: Double,
    val maxOutputWatts: Double,
    /** Electronics efficiency only. Winding I²R is already in the surrounding network. */
    val electronicsEfficiency: Double,
    val minGain: Double,
    val maxGain: Double
) {
    init {
        require(listOf(minInputVolts, maxInputVolts, maxOutputVolts, maxInputAmps,
            maxOutputAmps, maxOutputWatts, electronicsEfficiency, minGain, maxGain).all { it.isFinite() })
        require(minInputVolts > 0 && maxInputVolts >= minInputVolts)
        require(maxOutputVolts > 0 && maxInputAmps >= 0 && maxOutputAmps >= 0 && maxOutputWatts >= 0)
        require(electronicsEfficiency > 0 && electronicsEfficiency <= 1)
        require(minGain > 0 && maxGain >= minGain)
    }
}

enum class StopReason {
    DISABLED, INVALID_REQUEST, INVALID_NETWORK, NO_INPUT, INPUT_UNDERVOLTAGE,
    INPUT_OVERVOLTAGE, OUTPUT_ALREADY_HIGH, LIMIT_ZERO, GAIN_UNAVAILABLE,
    REVERSE_OUTPUT_BEYOND_CURRENT_LIMIT
}

enum class LimitReason { OUTPUT_VOLTAGE, INPUT_CURRENT_OR_SAG, OUTPUT_CURRENT, OUTPUT_POWER, MAXIMUM_GAIN }

data class OperatingPoint(
    val inputVolts: Double,
    val outputVolts: Double,
    /** Positive into the converter. */ val inputAmps: Double,
    /** Positive out of the converter. */ val outputAmps: Double,
    val inputWatts: Double,
    val outputWatts: Double,
    val electronicsHeatWatts: Double,
    val limitedBy: Set<LimitReason> = emptySet()
)

sealed interface TransferResult {
    data class Running(val point: OperatingPoint) : TransferResult
    /** The adapter must OPEN both active source branches, not command zero volts. */
    data class Off(val reason: StopReason) : TransferResult
}

/** Steady-state operating-point solver for a protected, non-inverting, one-way averaged converter.
 * Does not simulate PWM, standby consumption or output capacitance.
 * A solve call does not commit energy or advance thermal time.
 * The whole-network adapter must converge coupled converters before committing a step.
 */
object RegulatedConverter {
    const val OPEN_OHMS = 1e18

    fun solve(
        input: PortThevenin,
        output: PortThevenin,
        targetOutputVolts: Double,
        limits: ConverterLimits,
        enabled: Boolean = true
    ): TransferResult {
        fun off(reason: StopReason) = TransferResult.Off(reason)
        if (!enabled) return off(StopReason.DISABLED)
        if (!targetOutputVolts.isFinite() || targetOutputVolts < 0) return off(StopReason.INVALID_REQUEST)
        if (targetOutputVolts == 0.0) return off(StopReason.DISABLED)
        if (!valid(input) || !valid(output) || output.ohms == 0.0) return off(StopReason.INVALID_NETWORK)
        if (input.ohms >= OPEN_OHMS || input.volts <= 0) return off(StopReason.NO_INPUT)
        if (input.volts < limits.minInputVolts) return off(StopReason.INPUT_UNDERVOLTAGE)
        // Conservative startup rule: do not use loading to hide an overvoltage source.
        if (input.volts > limits.maxInputVolts) return off(StopReason.INPUT_OVERVOLTAGE)
        if (limits.maxInputAmps == 0.0 || limits.maxOutputAmps == 0.0 || limits.maxOutputWatts == 0.0)
            return off(StopReason.LIMIT_ZERO)

        val target = min(targetOutputVolts, limits.maxOutputVolts)
        val reasons = linkedSetOf<LimitReason>()
        if (target < targetOutputVolts) reasons += LimitReason.OUTPUT_VOLTAGE

        if (output.ohms >= OPEN_OHMS) {
            val noLoadVolts = min(target, input.volts * limits.maxGain)
            if (noLoadVolts < input.volts * limits.minGain) return off(StopReason.GAIN_UNAVAILABLE)
            if (noLoadVolts < target) reasons += LimitReason.MAXIMUM_GAIN
            return TransferResult.Running(OperatingPoint(input.volts, noLoadVolts, 0.0, 0.0,
                0.0, 0.0, 0.0, reasons))
        }

        // An actually open port has no driving Thevenin voltage; its previous floating
        // reading must not be mistaken for an independently powered/precharged output.
        if (output.volts >= target) return off(StopReason.OUTPUT_ALREADY_HIGH)

        // Stay on the high-voltage branch of a constant-power input.
        var inputAmpCap = limits.maxInputAmps
        if (input.ohms > 0) {
            inputAmpCap = min(inputAmpCap, input.volts / (2 * input.ohms))
            inputAmpCap = min(inputAmpCap, (input.volts - limits.minInputVolts) / input.ohms)
        }
        val inputWattCap = inputAmpCap * (input.volts - input.ohms * inputAmpCap)
        if (!inputWattCap.isFinite() || inputWattCap <= 0) return off(StopReason.INPUT_UNDERVOLTAGE)
        val powerCap = min(limits.maxOutputWatts, limits.electronicsEfficiency * inputWattCap)
        if (!powerCap.isFinite() || powerCap <= 0) return off(StopReason.LIMIT_ZERO)

        fun inputPoint(outputWatts: Double): Pair<Double, Double> {
            val inputWatts = outputWatts / limits.electronicsEfficiency
            if (inputWatts == 0.0) return input.volts to 0.0
            val discriminant = max(0.0, input.volts * input.volts - 4 * input.ohms * inputWatts)
            // Rationalized quadratic root: avoids subtracting almost-equal voltages at tiny loads.
            val amps = 2 * inputWatts / (input.volts + sqrt(discriminant))
            return (input.volts - input.ohms * amps) to amps
        }

        fun feasible(amps: Double): Boolean {
            val volts = max(0.0, output.volts + output.ohms * amps)
            val watts = volts * amps
            if (!volts.isFinite() || !watts.isFinite() || watts > powerCap) return false
            val (vin, _) = inputPoint(watts)
            return volts <= vin * limits.maxGain
        }

        // With a negative output Thevenin voltage, first find the current needed for Vout = 0.
        // The subsequent nonnegative-output branch has monotonically increasing power.
        val lower = max(0.0, -output.volts / output.ohms)
        val demandedAmps = (target - output.volts) / output.ohms
        if (!lower.isFinite() || !demandedAmps.isFinite()) return off(StopReason.INVALID_NETWORK)
        var upper = min(demandedAmps, limits.maxOutputAmps)
        if (upper < lower) return off(StopReason.REVERSE_OUTPUT_BEYOND_CURRENT_LIMIT)
        if (upper < demandedAmps) reasons += LimitReason.OUTPUT_CURRENT
        if (!feasible(lower)) return off(StopReason.GAIN_UNAVAILABLE)
        if (!feasible(upper)) {
            var lo = lower
            var hi = upper
            repeat(64) {
                val mid = lo + (hi - lo) * 0.5
                if (feasible(mid)) lo = mid else hi = mid
            }
            upper = lo
        }
        val outputVolts = max(0.0, output.volts + output.ohms * upper)
        val outputWatts = outputVolts * upper
        val (inputVolts, inputAmps) = inputPoint(outputWatts)
        if (outputVolts <= 0 || outputVolts + 1e-10 < inputVolts * limits.minGain)
            return off(StopReason.GAIN_UNAVAILABLE)
        val inputWatts = inputVolts * inputAmps
        if (outputVolts < target - 1e-8) {
            if (near(outputWatts, limits.maxOutputWatts)) reasons += LimitReason.OUTPUT_POWER
            if (near(outputWatts, limits.electronicsEfficiency * inputWattCap))
                reasons += LimitReason.INPUT_CURRENT_OR_SAG
            if (near(outputVolts, inputVolts * limits.maxGain)) reasons += LimitReason.MAXIMUM_GAIN
        }
        return TransferResult.Running(OperatingPoint(inputVolts, outputVolts, inputAmps, upper,
            inputWatts, outputWatts, max(0.0, inputWatts - outputWatts), reasons))
    }

    private fun valid(th: PortThevenin): Boolean =
        th.volts.isFinite() && !th.ohms.isNaN() && th.ohms >= 0.0

    private fun near(a: Double, b: Double) = abs(a - b) <= 1e-7 * max(1.0, max(abs(a), abs(b)))
}

/** Fixed-ratio, reversible ideal magnetic coupling. Real winding resistors live outside it.
 * Positive primary current enters; positive secondary current leaves.
 * A current limit is a separate trip/series-impedance action, NOT arbitrary output-power clamping.
 */
object RatioTransformer {
    fun solve(primary: PortThevenin, secondary: PortThevenin, ratio: Double): OperatingPoint? {
        if (!listOf(primary.volts, primary.ohms, secondary.volts, secondary.ohms, ratio).all { it.isFinite() }) return null
        if (primary.ohms < 0 || secondary.ohms < 0 || ratio <= 0) return null
        val denominator = secondary.ohms + ratio * ratio * primary.ohms
        if (!denominator.isFinite() || denominator <= 0) return null
        val isec = (ratio * primary.volts - secondary.volts) / denominator
        val ipri = ratio * isec
        val vpri = primary.volts - primary.ohms * ipri
        val vsec = ratio * vpri
        val pin = vpri * ipri
        val pout = vsec * isec
        if (!listOf(isec, ipri, vpri, vsec, pin, pout).all { it.isFinite() }) return null
        return OperatingPoint(vpri, vsec, ipri, isec, pin, pout, 0.0)
    }
}
