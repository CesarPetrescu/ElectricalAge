package mods.eln.simplenode.computerprobe

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.Level

class ComputerProbeBlock : SimpleNodeBlock(Material.PACKED_ICE) {

    override fun createNewTileEntity(world: Level?, meta: Int): BlockEntity {
        return ComputerProbeEntity()
    }

    override fun newNode(): SimpleNode {
        return ComputerProbeNode()
    }


}
