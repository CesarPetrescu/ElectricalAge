package mods.eln.simplenode.computerprobe

import mods.eln.node.NodeBlock
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import mods.eln.node.simple.SimpleNodeEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Supplier

/**
 * The computer probe: a block a computer talks to (a CC: Tweaked peripheral when that mod is
 * present, see mods.eln.integration.computercraft) with a signal gate on each of its six faces
 * and the wireless signal channels. The block is a plain textured cube (the model comes from
 * data generation); the peripheral is served from the block entity through the capability.
 */
class ComputerProbeBlock : SimpleNodeBlock(NodeBlock.nodeProperties()) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ComputerProbeEntity(pos, state)

    override fun newNode(): SimpleNode = ComputerProbeNode()
}

class ComputerProbeEntity(pos: BlockPos, state: BlockState) : SimpleNodeEntity(TYPE.get(), pos, state, ComputerProbeNode.getNodeUuidStatic()) {
    companion object {
        @JvmField
        var TYPE: Supplier<BlockEntityType<ComputerProbeEntity>> = Supplier { throw IllegalStateException("ComputerProbeEntity type not registered") }
    }
}
