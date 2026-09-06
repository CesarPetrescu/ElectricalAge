package mods.eln.node.simple

import mods.eln.misc.Coordinate
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import mods.eln.misc.getBlockState

class SimpleNodeItem(b: Block, properties: Properties = Properties()) : BlockItem(b, properties) {
    val nodeBlock: SimpleNodeBlock = b as SimpleNodeBlock

    /**
     * The node has to exist before the block is placed: SimpleNodeEntity looks its node up by
     * coordinate as soon as it is created, and a placement that fails must not leave one behind.
     * (1.7.10's `placeBlockAt`; vanilla's `place` does the sound, the stat and the stack around it.)
     */
    override fun placeBlock(context: BlockPlaceContext, newState: BlockState): Boolean {
        val world = context.level
        val pos = context.clickedPos
        val player = context.player ?: return false
        var node: SimpleNode? = null
        if (!world.isClientSide) {
            node = nodeBlock.newNode()
            node!!.descriptorKey = nodeBlock.descriptorKey
            node.onBlockPlacedBy(Coordinate(pos.x, pos.y, pos.z, world), nodeBlock.getFrontForPlacement(player), player, context.itemInHand)
        }
        if (!super.placeBlock(context, newState)) {
            node?.onBreakBlock()
            return false
        }
        return true
    }
}
