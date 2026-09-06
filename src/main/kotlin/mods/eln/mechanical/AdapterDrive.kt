package mods.eln.mechanical

import kotlin.math.*

/** Pure mechanics, independent of Create and Minecraft, so power accounting is directly testable. */
class AdapterDrive(val maxPower: Double, val maxTorque: Double, val efficiency: Double = 0.9, val wattsPerSu: Double = 1.0) {
    init {
        require(maxPower.isFinite() && maxPower > 0 && maxTorque.isFinite() && maxTorque > 0)
        require(efficiency.isFinite() && efficiency > 0 && efficiency <= 1 && wattsPerSu.isFinite() && wattsPerSu > 0)
    }
    fun target(rpm: Double, ratio: Int): Double = abs(rpm) * PI / 30.0 * ratio

    fun requestedPower(rpm: Double, ratio: Int, omega: Double, inertia: Double, dt: Double): Double {
        if (!listOf(rpm, omega, inertia, dt).all { it.isFinite() } || inertia <= 0 || dt <= 0 || rpm == 0.0 || ratio !in RATIOS) return 0.0
        val target = target(rpm, ratio)
        if (target > MAX_SPEED || omega >= target || omega < 0) return 0.0
        val next = min(target, omega + min((target - omega) / RESPONSE, maxTorque / inertia) * dt)
        return min(maxPower, max(0.0, inertia * (next * next - omega * omega) / (2 * dt)))
    }

    fun stressImpact(outputPower: Double, rpm: Double): Double =
        if (rpm.isFinite() && abs(rpm) > 1e-6) outputPower.coerceIn(0.0, maxPower) / (efficiency * wattsPerSu * abs(rpm)) else 0.0

    fun permittedEnergy(requestedPower: Double, acceptedImpact: Double, rpm: Double, dt: Double): Double {
        if (!listOf(requestedPower, acceptedImpact, rpm, dt).all { it.isFinite() } || dt <= 0) return 0.0
        return min(requestedPower.coerceIn(0.0, maxPower), max(0.0, acceptedImpact) * abs(rpm) * wattsPerSu * efficiency) * dt
    }

    companion object {
        val RATIOS = listOf(1, 2, 4, 8)
        const val MAX_SPEED = 240.0
        const val RESPONSE = 0.5
    }
}
