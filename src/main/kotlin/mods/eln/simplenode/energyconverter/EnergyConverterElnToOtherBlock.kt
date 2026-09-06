package mods.eln.simplenode.energyconverter

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.misc.Direction
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import mods.eln.node.simple.SimpleNodeEntity
import mods.eln.node.NodeBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level

class EnergyConverterElnToOtherBlock(private val descriptor: EnergyConverterElnToOtherDescriptor) : SimpleNodeBlock(NodeBlock.nodeProperties()) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = EnergyConverterElnToOtherEntity(pos, state)

    override fun newNode(): SimpleNode {
        return EnergyConverterElnToOtherNode()
    }


    init {
        setDescriptor(descriptor)
    }
}
