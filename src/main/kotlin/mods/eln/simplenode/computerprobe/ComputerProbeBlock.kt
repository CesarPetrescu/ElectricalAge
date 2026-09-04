package mods.eln.simplenode.computerprobe

import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import net.minecraft.block.material.Material
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.World

class ComputerProbeBlock : SimpleNodeBlock(Material.PACKED_ICE) {

    override fun createNewTileEntity(world: World?, meta: Int): TileEntity {
        return ComputerProbeEntity()
    }

    override fun newNode(): SimpleNode {
        return ComputerProbeNode()
    }


}
