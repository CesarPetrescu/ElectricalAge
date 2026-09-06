package mods.eln.init

import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.ElectricalCableRender

class Cable {
    companion object {
        const val gateOutputCurrent = 0.1

        const val SVU = 5.0
        const val SVUinv = 1.0 / SVU
        const val SVIinv = gateOutputCurrent / SVU
        const val LVU = 50.0
        const val LVUinv = 1.0 / LVU
        const val MVU = 200.0
        const val MVUinv = 1.0 / MVU
        const val HVU = 800.0
        const val HVUinv = 1.0 / HVU
        const val VHVU = 3200.0
        const val VHVUinv = 1.0 / VHVU
        
        // Nominal power calculations (from Eln_old.java)
        const val SVP = gateOutputCurrent * SVU
        @JvmStatic
        fun LVP() = 1000 * Config.cablePowerFactor
        @JvmStatic
        fun MVP() = 2000 * Config.cablePowerFactor
        @JvmStatic
        fun HVP() = 5000 * Config.cablePowerFactor
        @JvmStatic
        fun VHVP() = 15000 * Config.cablePowerFactor
        
        // Cable instances - initialized in Descriptors.preInit()
        lateinit var battery: Cable
        lateinit var signal: Cable
        lateinit var lowVoltage: Cable
        lateinit var mediumVoltage: Cable
        lateinit var highVoltage: Cable
        lateinit var veryHighVoltage: Cable

        @JvmStatic
        val smallRs get() = lowVoltage.descriptor.electricalRs

        @JvmStatic
        fun applySmallRs(aLoad: NbtElectricalLoad) {
            lowVoltage.descriptor.applyTo(aLoad)
        }

        @JvmStatic
        fun applySmallRs(r: Resistor) {
            lowVoltage.descriptor.applyTo(r)
        }

    }

    lateinit var descriptor: ElectricalCableDescriptor
}
