package mods.eln.simplenode.energyconverter

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.misc.Direction
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import mods.eln.node.simple.SimpleNodeEntity
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level

class EnergyConverterElnToOtherBlock(private val descriptor: EnergyConverterElnToOtherDescriptor) : SimpleNodeBlock(Material.PACKED_ICE) {

    override fun createNewTileEntity(var1: Level, var2: Int): BlockEntity {
        return EnergyConverterElnToOtherEntity()
    }

    override fun newNode(): SimpleNode {
        return EnergyConverterElnToOtherNode()
    }


    init {
        setDescriptor(descriptor)
    }
}
