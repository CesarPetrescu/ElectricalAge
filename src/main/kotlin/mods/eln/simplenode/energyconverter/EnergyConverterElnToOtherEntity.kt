package mods.eln.simplenode.energyconverter

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.Other
import mods.eln.misc.Direction
import mods.eln.node.simple.SimpleNodeEntity
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.energy.IEnergyStorage
import java.io.DataInputStream
import java.io.IOException
import java.util.function.Supplier

/**
 * The Electrical Age -> other-mod energy exporter. Only the Forge Energy side survives on 1.21
 * (IC2 and OpenComputers have no 1.21 releases); [energyStorage] is exposed through
 * `Capabilities.EnergyStorage.BLOCK` in the mod's RegisterCapabilitiesEvent handler.
 */
class EnergyConverterElnToOtherEntity(pos: BlockPos, state: BlockState) : SimpleNodeEntity(TYPE.get(), pos, state, "ElnToOther") {
    companion object {
        /** Registered by SingleNodeRegistration through ElnRegistry.registerBlockEntity. */
        @JvmField
        var TYPE: Supplier<BlockEntityType<EnergyConverterElnToOtherEntity>> = Supplier { throw IllegalStateException("EnergyConverterElnToOtherEntity type not registered") }
    }

    @JvmField
    var selectedResistance = 0.0
    @JvmField
    var hasChanges = false
    var ic2tier = 1

    @OnlyIn(Dist.CLIENT)
    override fun newGuiDraw(side: Direction, player: Player): Screen {
        return EnergyConverterElnToOtherGui(this)
    }

    override fun serverPublishUnserialize(stream: DataInputStream) {
        super.serverPublishUnserialize(stream)
        try {
            selectedResistance = stream.readDouble()
            ic2tier = stream.readInt()
            hasChanges = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // *************** Forge Energy (was cofh RF) **************
    val energyStorage = object : IEnergyStorage {
        override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int = 0

        override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
            if (world.isClientSide) return 0
            if (node == null) return 0
            val node = node as EnergyConverterElnToOtherNode
            val extract = Math.max(0, Math.min(maxExtract, node.availableEnergyInModUnits(Other.getWattsToRf()).toInt()))
            if (!simulate) node.drawEnergy(extract.toDouble(), Other.getWattsToRf())
            return extract
        }

        override fun getEnergyStored(): Int = 0

        override fun getMaxEnergyStored(): Int = 0

        override fun canExtract(): Boolean = true

        override fun canReceive(): Boolean = false
    }

    /** Whether the capability should be offered: server side, with a live node. */
    fun canConnectEnergy(): Boolean {
        if (level?.isClientSide != false) return false
        if (node == null) return false
        return true
    }

    override fun update() {
        if (Other.teLoaded) EnergyConverterElnToOtherFireWallRf.updateEntity(this)
    }
}
