package mods.eln.simplenode.energyconverter

import mods.eln.Other
import mods.eln.misc.Direction
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage

/**
 * Pushes energy into neighbouring Forge Energy receivers (the 1.12.2 successor of the cofh RF API).
 */
object EnergyConverterElnToOtherFireWallRf {

    fun updateEntity(e: EnergyConverterElnToOtherEntity) {
        val level = e.level ?: return
        if (level.isClientSide) return
        if (e.node == null) return
        val node = e.node as EnergyConverterElnToOtherNode

        val energySinkList: List<IEnergyStorage> = Direction.all.mapNotNull { direction ->
            // Ask the neighbour for the face that touches us (1.21: block capabilities are looked up on the level).
            val side = direction.inverse.toFacing()
            level.getCapability(Capabilities.EnergyStorage.BLOCK, e.blockPos.relative(direction.toFacing()), side)?.takeIf { it.canReceive() }
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
