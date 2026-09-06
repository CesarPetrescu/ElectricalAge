package mods.eln.simplenode.energyconverter

import mods.eln.Other
import mods.eln.misc.Direction
import net.minecraftforge.energy.CapabilityEnergy
import net.neoforged.neoforge.energy.IEnergyStorage

/**
 * Pushes energy into neighbouring Forge Energy receivers (the 1.12.2 successor of the cofh RF API).
 */
object EnergyConverterElnToOtherFireWallRf {

    fun updateEntity(e: EnergyConverterElnToOtherEntity) {
        if (e.level.isClientSide) return
        if (e.node == null) return
        val node = e.node as EnergyConverterElnToOtherNode

        val energySinkList: List<IEnergyStorage> = Direction.all.mapNotNull { direction ->
            val neighbour = direction.applyToTileEntity(e) ?: return@mapNotNull null
            // Ask the neighbour for the face that touches us.
            val side = direction.inverse.toFacing()
            if (!neighbour.hasCapability(CapabilityEnergy.ENERGY, side)) return@mapNotNull null
            neighbour.getCapability(CapabilityEnergy.ENERGY, side)?.takeIf { it.canReceive() }
        }
        if (energySinkList.isEmpty()) return
        val rfUsed = energySinkList.map {
            val rfAvailable = (node.availableEnergyInModUnits(Other.getWattsToRf()) / energySinkList.size)
            // receiveEnergy takes RF in, gives out RF
            val rfUsed = it.receiveEnergy(rfAvailable.toInt(), false).toDouble()
            rfUsed
        }.sum()
        node.drawEnergy(rfUsed, Other.getWattsToRf())
    }
}
