package mods.eln.node.simple

import mods.eln.misc.Coordinate
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import mods.eln.misc.getBlockState

class SimpleNodeItem(b: Block) : BlockItem(b) {
    var block: SimpleNodeBlock

    /**
     * The node has to exist before the block is placed: SimpleNodeEntity looks its node up by
     * coordinate as soon as it is created, and a placement that fails must not leave one behind.
     */
    override fun placeBlockAt(
        stack: ItemStack, player: Player, world: Level, pos: BlockPos,
        side: Direction, hitX: Float, hitY: Float, hitZ: Float, newState: BlockState
    ): Boolean {
        var node: SimpleNode? = null
        if (!world.isClientSide) {
            node = block.newNode()
            node!!.descriptorKey = block.descriptorKey
            node.onBlockPlacedBy(Coordinate(pos.x, pos.y, pos.z, world), block.getFrontForPlacement(player), player, stack)
        }
        if (!world.setBlockState(pos, newState, 3)) {
            node?.onBreakBlock()
            return false
        }
        if (world.getBlockState(pos).block === this.block) {
            this.block.onBlockPlacedBy(world, pos, newState, player, stack)
        }
        return true
    }

    init {
        block = b as SimpleNodeBlock
    }
}
