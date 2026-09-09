package mods.eln.transparentnode

import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.sixnode.electricalcable.WireThermalPhysics
import net.minecraft.nbt.CompoundTag

/** One-temperature winding assembly. Copper length sets mass; only 25% of the straight-wire
 * cooling surface is exposed (explicit gameplay packing assumption). Heat remains in the
 * assembly during inventory reconfiguration; swapping coils is not a free cooling action.
 * This does not yet transport heat in a removed item or simulate individual turn insulation.
 */
class WindingThermalLoad(private val key: String) : NbtThermalLoad(key) {
    private var physics: WireThermalPhysics? = null
    private var insulated = false
    private var ambient = 20.0
    private var initialized = false
    var energyJoules = 0.0
        private set
    val absoluteCelsius get() = physics?.temperature(energyJoules) ?: (20.0 + energyJoules / 100.0)
    val failed get() = physics?.let { energyJoules >= it.failureEnthalpy } ?: false

    fun configure(material: UtilityCableMaterial?, areaMm2: Double, meters: Double, jacket: Boolean, ambientC: Double) {
        val initialTemperature = temperatureCelsius + ambientC
        physics = if (material != null && areaMm2 > 0 && meters > 0)
            WireThermalPhysics(material, areaMm2, meters) else null
        insulated = jacket
        if (!initialized) {
            energyJoules = physics?.sensibleEnthalpy(initialTemperature) ?: ((initialTemperature - 20.0) * 100.0)
            initialized = true
        }
        updateAmbient(ambientC)
    }

    fun updateAmbient(ambientC: Double) {
        require(ambientC.isFinite())
        ambient = ambientC
        val t = absoluteCelsius
        temperatureCelsius = t - ambient
        val p = physics
        if (p == null) {
            set(1.0e9, 10.0, 100.0)
        } else {
            set(p.endpointThermalResistance,
                1.0 / (0.25 * p.coolingConductance(t, ambient, insulated)), p.capacity(t))
        }
    }

    override fun integrateEnergy(joules: Double) {
        require(joules.isFinite())
        // A zero-energy disconnect flush before configure must not erase legacy saved temperature.
        if (!initialized && joules == 0.0) return
        energyJoules += joules
        initialized = true
        updateAmbient(ambient)
    }

    override fun readFromNBT(nbt: CompoundTag, prefix: String) {
        super.readFromNBT(nbt, prefix)
        val value = nbt.getDouble(prefix + key + "EnergyJ")
        initialized = nbt.contains(prefix + key + "EnergyJ") && value.isFinite()
        energyJoules = if (initialized) value else 0.0
    }

    override fun writeToNBT(nbt: CompoundTag, prefix: String) {
        super.writeToNBT(nbt, prefix)
        if (initialized) nbt.putDouble(prefix + key + "EnergyJ", energyJoules)
    }
}
