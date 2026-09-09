package mods.eln.sim.power

/** A geometry-only wrapper. Supply rho/alpha from the repository's existing WirePhysics.
 * This intentionally does not duplicate the repository's material-property constants.
 */
data class WindingGeometry(
    val lengthMeters: Double,
    val areaPerCoreMm2: Double,
    val turns: Double,
    val parallelPaths: Int = 1
) {
    init {
        require(lengthMeters.isFinite() && lengthMeters > 0)
        require(areaPerCoreMm2.isFinite() && areaPerCoreMm2 > 0)
        require(turns.isFinite() && turns > 0 && parallelPaths >= 1)
    }
    fun resistanceOhms(rho20OhmMm2PerMeter: Double, alpha: Double, absoluteCelsius: Double): Double {
        require(rho20OhmMm2PerMeter.isFinite() && rho20OhmMm2PerMeter > 0)
        require(alpha.isFinite() && alpha >= 0 && absoluteCelsius.isFinite())
        val thermalFactor = (1 + alpha * (absoluteCelsius - 20)).coerceAtLeast(.05)
        return rho20OhmMm2PerMeter * lengthMeters / (areaPerCoreMm2 * parallelPaths) * thermalFactor
    }
    fun metalVolumeCubicMeters(): Double = lengthMeters * areaPerCoreMm2 * parallelPaths * 1e-6
}

/** Joules accumulator: actual electrical steps call commit, thermal steps call drain.
 * The adapter must feed measured losses from the accepted solution, never a solver guess.
 */
class HeatAccumulator {
    private var joules = 0.0
    fun commit(watts: Double, seconds: Double) {
        require(watts.isFinite() && watts >= 0 && seconds.isFinite() && seconds >= 0)
        val next = joules + watts * seconds
        check(next.isFinite()) { "Heat accumulator overflow" }
        joules = next
    }
    fun drainJoules(): Double = joules.also { joules = 0.0 }
}
