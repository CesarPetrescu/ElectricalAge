package mods.eln.node.simple

import mods.eln.misc.Coordinate
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class SimpleNodeItem(b: Block) : ItemBlock(b) {
    var block: SimpleNodeBlock

    /**
     * The node has to exist before the block is placed: SimpleNodeEntity looks its node up by
     * coordinate as soon as it is created, and a placement that fails must not leave one behind.
     */
    override fun placeBlockAt(
        stack: ItemStack, player: EntityPlayer, world: World, pos: BlockPos,
        side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, newState: IBlockState
    ): Boolean {
        var node: SimpleNode? = null
        if (!world.isRemote) {
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
