package mods.eln.sixnode.electricalcable

import mods.eln.sim.nbt.NbtThermalLoad
import net.minecraft.nbt.CompoundTag

/** Retains the existing ambient-relative NBT temperature and persists phase-change energy. */
class WireThermalLoad(private val key: String, val physics: WireThermalPhysics) : NbtThermalLoad(key) {
    var ambientCelsius = 20.0
    var phaseJoules = 0.0
        private set
    val absoluteCelsius get() = temperatureCelsius + ambientCelsius
    val storedJoules get() = physics.sensibleEnthalpy(absoluteCelsius) + phaseJoules
    val failed get() = phaseJoules >= physics.fusionJoules

    fun updateProperties(ambient: Double, surrounding: Double, insulated: Boolean) {
        require(ambient.isFinite() && surrounding.isFinite())
        ambientCelsius = ambient
        heatCapacity = physics.capacity(absoluteCelsius)
        Rs = physics.endpointThermalResistance
        Rp = 1.0 / physics.coolingConductance(absoluteCelsius, surrounding, insulated)
    }

    override fun integrateEnergy(joules: Double) {
        require(joules.isFinite())
        val energy = storedJoules + joules
        temperatureCelsius = physics.temperature(energy) - ambientCelsius
        // Retain even overshoot energy until the failure transition; never silently clip it.
        phaseJoules = (energy - physics.meltingEnthalpy).coerceAtLeast(0.0)
        heatCapacity = physics.capacity(absoluteCelsius)
    }

    fun inheritHeat(from: WireThermalLoad) {
        ambientCelsius = from.ambientCelsius
        temperatureCelsius = from.temperatureCelsius
        phaseJoules = from.phaseJoules
    }

    override fun readFromNBT(nbt: CompoundTag, prefix: String) {
        super.readFromNBT(nbt, prefix)
        phaseJoules = nbt.getDouble(prefix + key + "FusionJ").takeIf { it.isFinite() && it >= 0 } ?: 0.0
    }
    override fun writeToNBT(nbt: CompoundTag, prefix: String) {
        super.writeToNBT(nbt, prefix)
        nbt.putDouble(prefix + key + "FusionJ", phaseJoules)
    }
}
