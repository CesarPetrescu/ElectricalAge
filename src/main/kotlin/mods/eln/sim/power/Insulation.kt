package mods.eln.sim.power

import kotlin.math.abs
import kotlin.math.max

/** Proposed GAME insulation classes; these values are not cable certifications. */
data class InsulationClass(val id: String, val voltsToGround: Double, val voltsBetweenCores: Double) {
    init { require(voltsToGround.isFinite() && voltsToGround > 0 && voltsBetweenCores.isFinite() && voltsBetweenCores > 0) }
}

object GameInsulation {
    val LV_1KV = InsulationClass("lv_1kv", 1_000.0, 1_000.0)
    val MV_5KV = InsulationClass("mv_5kv", 5_000.0, 5_000.0)
    val MV_20KV = InsulationClass("mv_20kv", 20_000.0, 20_000.0)
    val HV_40KV = InsulationClass("hv_40kv", 40_000.0, 40_000.0)
    val HV_150KV = InsulationClass("hv_150kv", 150_000.0, 150_000.0)
}

data class InsulationStress(val fromCore: Int, val toCore: Int?, val volts: Double, val rating: Double) {
    val ratio get() = volts / rating
}

fun worstInsulationStress(coreVoltsToGround: DoubleArray, insulation: InsulationClass): InsulationStress {
    require(coreVoltsToGround.isNotEmpty() && coreVoltsToGround.all { it.isFinite() })
    var result = InsulationStress(0, null, abs(coreVoltsToGround[0]), insulation.voltsToGround)
    fun consider(s: InsulationStress) { if (s.ratio > result.ratio) result = s }
    for (i in coreVoltsToGround.indices) {
        consider(InsulationStress(i, null, abs(coreVoltsToGround[i]), insulation.voltsToGround))
        for (j in i + 1 until coreVoltsToGround.size) {
            consider(InsulationStress(i, j, abs(coreVoltsToGround[i] - coreVoltsToGround[j]), insulation.voltsBetweenCores))
        }
    }
    return result
}

/** An explicit gameplay damage curve, NOT a physical dielectric aging law.
 * 25% sustained overvoltage gives failure after 1 simulated second; >=50% fails immediately.
 * The adapter persists exposure and the latched fault endpoints. Damage does not auto-heal.
 * Call ONLY for committed simulation steps; speculative MNA probes must not advance it.
 */
class InsulationDamage(exposure: Double = 0.0, fault: InsulationStress? = null) {
    var exposure: Double = exposure
        private set
    var fault: InsulationStress? = fault
        private set
    init { require(exposure.isFinite() && exposure >= 0) }

    fun commit(stress: InsulationStress, seconds: Double): InsulationStress? {
        require(seconds.isFinite() && seconds >= 0)
        require(stress.volts.isFinite() && stress.rating.isFinite() && stress.rating > 0)
        fault?.let { return it }
        if (seconds == 0.0) return null
        val over = max(0.0, stress.ratio - 1.0)
        exposure += (over / .25) * (over / .25) * seconds
        if (stress.ratio >= 1.5 || exposure >= 1.0 - 1e-12) fault = stress
        return fault
    }
}

/** Preserve both descriptor ids and registry keys. Feed actual captured registration manifests. */
data class RegistrationIdentity(val descriptorId: Int, val registryKey: String)
fun validateRegistrationMigration(
    old: Map<String, RegistrationIdentity>,
    proposed: Map<String, RegistrationIdentity>
) {
    require(proposed.values.map { it.descriptorId }.toSet().size == proposed.size) { "Duplicate descriptor id" }
    require(proposed.values.map { it.registryKey }.toSet().size == proposed.size) { "Duplicate registry key" }
    old.forEach { (key, identity) -> require(proposed[key] == identity) { "Legacy identity changed: $key" } }
}
