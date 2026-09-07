package mods.eln.mechanical

import kotlin.math.abs
import kotlin.math.sqrt

/** Electrical source power is positive when supplying the circuit. All powers here are watts. */
object ShaftElectricalMath {
    data class Transfer(val shaftPower: Double, val heatPower: Double)

    /** Friction consumes remaining kinetic/input energy; a stopped, unpowered shaft emits no heat. */
    fun frictionPower(energy: Double, shaftPower: Double, requested: Double, dt: Double): Double {
        require(dt.isFinite() && dt > 0)
        return minOf(requested.coerceAtLeast(0.0), (energy / dt + shaftPower).coerceAtLeast(0.0))
    }

    fun transfer(sourcePower: Double, generatingEfficiency: Double, motoringEfficiency: Double): Transfer {
        require(generatingEfficiency > 0.0 && generatingEfficiency <= 1.0)
        require(motoringEfficiency > 0.0 && motoringEfficiency <= 1.0)
        if (!sourcePower.isFinite()) return Transfer(0.0, 0.0)
        val shaftPower = if (sourcePower >= 0.0) -sourcePower / generatingEfficiency
            else -sourcePower * motoringEfficiency
        return Transfer(shaftPower, -sourcePower - shaftPower)
    }

    /** Solve droop and both current limits together; a previous-step current creates feedback oscillation. */
    fun sourceVoltage(emf: Double, supplyVoltage: Double, resistance: Double, droop: Double,
                      currentLimit: Double, availableOutputPower: Double): Double {
        if (!emf.isFinite() || !supplyVoltage.isFinite()) return 0.0
        if (!resistance.isFinite() || resistance > 1e8) return emf.coerceAtLeast(0.0)
        val r = resistance.coerceAtLeast(1e-9)
        var current = ((emf - supplyVoltage) / (r + droop.coerceAtLeast(0.0)))
            .coerceIn(-abs(currentLimit), abs(currentLimit))
        if (current > 0.0) {
            // (Vsupply + I*R)*I cannot exceed the mechanical energy available this step.
            val p = availableOutputPower.coerceAtLeast(0.0)
            val root = sqrt(supplyVoltage * supplyVoltage + 4.0 * r * p)
            val powerLimitedCurrent = if (supplyVoltage >= 0.0) {
                if (p == 0.0) 0.0 else 2.0 * p / (supplyVoltage + root)
            } else (root - supplyVoltage) / (2.0 * r)
            current = minOf(current, powerLimitedCurrent)
        }
        return (supplyVoltage + current * r).coerceAtLeast(0.0)
    }
}
