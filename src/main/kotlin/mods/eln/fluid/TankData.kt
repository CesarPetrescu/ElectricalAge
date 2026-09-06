package mods.eln.fluid

import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluid
import mods.eln.fluid.FluidRegistry
import net.neoforged.neoforge.fluids.capability.templates.FluidTank
import mods.eln.misc.writeToNBT

data class TankData(val tank: FluidTank, val fluidWhitelist: MutableList<Fluid> = mutableListOf(), var fractionalDemandMb: Double = 0.0):
    INBTTReady {

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        tank.readFromNBT(mods.eln.misc.McRegistries.access(), nbt.getCompound("${str}tank"))
        val fluidWhitelistNames = nbt.getString("${str}whitelist")?.split("|")!!
        fluidWhitelist.clear()
        fluidWhitelistNames.forEach {
            val fluid = FluidRegistry.getFluid(it)
            if (fluid != null) fluidWhitelist.add(fluid) else if (it.isNotEmpty()) Utils.println("Error, could not find fluid $it")
        }
        fractionalDemandMb = nbt.getDouble("${str}demandMb")
        tank.capacity = nbt.getInt("${str}capacity")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        val tag = CompoundTag()
        tank.writeToNBT(mods.eln.misc.McRegistries.access(), tag)
        nbt.put("${str}tank", tag)
        nbt.putString("${str}whitelist", fluidWhitelist.joinToString("|") { FluidRegistry.getFluidName(it) ?: "" })
        nbt.putDouble("${str}demandMb", fractionalDemandMb)
        nbt.putInt("${str}capacity", tank.capacity)
    }

    override fun toString(): String {
        return "TankData(${tank.fluidAmount}/${tank.capacity}mB of ${tank.fluid}, whitelist: ${fluidWhitelist}, ${fractionalDemandMb}mB spare"
    }
}
