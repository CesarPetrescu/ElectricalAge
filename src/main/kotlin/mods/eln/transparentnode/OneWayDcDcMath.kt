package mods.eln.transparentnode

import mods.eln.misc.Utils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class OneWayDcDcMode {
    FIXED,
    BOOST,
    BUCK,
    BOOST_BUCK,
    ISOLATION
}

internal interface OneWayDcDcThevenin {
    val voltage: Double
    val resistance: Double
}

internal data class OneWayDcDcTransfer(
    val inputSourceVoltage: Double,
    val outputSourceVoltage: Double,
    val power: Double
)

internal object OneWayDcDcMath {
    fun solve(
        inputTh: OneWayDcDcThevenin,
        outputTh: OneWayDcDcThevenin,
        ratio: Double,
        maxOutputVoltage: Double
    ): OneWayDcDcTransfer? {
        if (!inputTh.voltage.isFinite() || !outputTh.voltage.isFinite()) return null
        if (!inputTh.resistance.isFinite() || !outputTh.resistance.isFinite()) return null
        if (inputTh.voltage <= 0.0 || !ratio.isFinite() || ratio <= 0.0 ||
            !maxOutputVoltage.isFinite() || maxOutputVoltage <= 0.0 ||
            inputTh.resistance < 0.0 || outputTh.resistance < 0.0) return null

        val targetOutputVoltage = Utils.limit(inputTh.voltage * ratio, 0.0, maxOutputVoltage)
        if (outputTh.resistance >= 1.0e18) {
            return OneWayDcDcTransfer(inputTh.voltage, targetOutputVoltage, 0.0)
        }
        if (targetOutputVoltage <= outputTh.voltage || targetOutputVoltage <= 0.0) return null

        val outputResistance = outputTh.resistance.coerceAtLeast(0.0)
        if (outputResistance <= 0.0) return null
        val demandedOutputCurrent = ((targetOutputVoltage - outputTh.voltage) / outputResistance).coerceAtLeast(0.0)

        var outputPower = targetOutputVoltage * demandedOutputCurrent
        val inputResistance = inputTh.resistance.coerceAtLeast(0.0)
        val inputMaxPower = if (inputResistance <= 0.0) {
            Double.POSITIVE_INFINITY
        } else {
            inputTh.voltage * inputTh.voltage / (4.0 * inputResistance)
        }
        outputPower = outputPower.coerceAtMost(inputMaxPower).coerceAtLeast(0.0)
        if (outputPower <= 0.0) return null

        val outputSourceVoltage = sourceVoltageForPower(
            theveninVoltage = outputTh.voltage,
            resistance = outputResistance,
            power = outputPower,
            maxVoltage = targetOutputVoltage
        )
        val actualOutputPower = outputPowerAtSource(outputTh.voltage, outputResistance, outputSourceVoltage)
            .coerceAtMost(inputMaxPower)
            .coerceAtLeast(0.0)
        if (actualOutputPower <= 0.0) return null

        val inputSourceVoltage = sinkVoltageForPower(
            theveninVoltage = inputTh.voltage,
            resistance = inputResistance,
            power = actualOutputPower
        )

        return OneWayDcDcTransfer(inputSourceVoltage, outputSourceVoltage, actualOutputPower)
    }

    private fun outputPowerAtSource(theveninVoltage: Double, resistance: Double, sourceVoltage: Double): Double {
        val current = if (resistance <= 0.0) {
            return 0.0
        } else {
            ((sourceVoltage - theveninVoltage) / resistance).coerceAtLeast(0.0)
        }
        return sourceVoltage * current
    }

    private fun sourceVoltageForPower(theveninVoltage: Double, resistance: Double, power: Double, maxVoltage: Double): Double {
        if (power <= 0.0) return theveninVoltage
        if (resistance <= 0.0) return min(maxVoltage, theveninVoltage).coerceAtLeast(theveninVoltage)
        val voltage = (sqrt(theveninVoltage * theveninVoltage + 4.0 * power * resistance) + theveninVoltage) / 2.0
        return min(maxVoltage, voltage).coerceAtLeast(theveninVoltage)
    }

    private fun sinkVoltageForPower(theveninVoltage: Double, resistance: Double, power: Double): Double {
        if (power <= 0.0) return theveninVoltage
        if (resistance <= 0.0) return theveninVoltage
        val clampedPower = min(power, theveninVoltage * theveninVoltage / (4.0 * resistance))
        val discriminant = (theveninVoltage * theveninVoltage - 4.0 * clampedPower * resistance).coerceAtLeast(0.0)
        val voltageByPower = (theveninVoltage + sqrt(discriminant)) / 2.0
        return max(0.0, voltageByPower)
    }
}
