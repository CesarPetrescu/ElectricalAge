package mods.eln.simplenode.energyconverter

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import mods.eln.misc.Direction
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import mods.eln.node.simple.SimpleNodeEntity
import net.minecraft.block.material.Material
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

class EnergyConverterElnToOtherBlock(private val descriptor: EnergyConverterElnToOtherDescriptor) : SimpleNodeBlock(Material.packedIce) {

    override fun createNewTileEntity(var1: World, var2: Int): TileEntity {
        return EnergyConverterElnToOtherEntity()
    }

    override fun newNode(): SimpleNode {
        return EnergyConverterElnToOtherNode()
    }


    init {
        setDescriptor(descriptor)
    }
}
