package mods.eln.sim.process.heater

import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad

/** Electrical steps accumulate joules; thermal steps consume them exactly once, without clipping.
 * Sampling power (not average current) retains short pulses and alternating-current heating.
 */
class ElectricalHeatAccumulator(private val power: () -> Double, private val load: ThermalLoad) {
    var pendingJoules = 0.0
        private set
    var sampledJoules = 0.0
        private set
    var deliveredJoules = 0.0
        private set
    var lastWatts = 0.0
        private set
    val sample = IProcess { dt ->
        val watts = power()
        require(dt.isFinite() && dt > 0 && watts.isFinite() && watts >= 0) { "Invalid wire heating sample" }
        val energy = watts * dt
        pendingJoules += energy
        sampledJoules += energy
    }
    val deliver = IProcess { dt ->
        require(dt.isFinite() && dt > 0)
        lastWatts = pendingJoules / dt
        load.movePowerTo(lastWatts)
        deliveredJoules += pendingJoules
        pendingJoules = 0.0
    }
    fun flushIntoLoad() {
        load.integrateEnergy(pendingJoules)
        deliveredJoules += pendingJoules
        pendingJoules = 0.0
    }
}
