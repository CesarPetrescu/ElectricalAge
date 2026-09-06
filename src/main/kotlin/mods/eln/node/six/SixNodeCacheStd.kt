package mods.eln.node.six

import mods.eln.misc.Utils
import mods.eln.node.ISixNodeCache
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.core.BlockPos

/** Which blocks can camouflage a six-node: plain full-cube blocks without a block entity. */
class SixNodeCacheStd : ISixNodeCache {
    override fun accept(stack: ItemStack): Boolean {
        Utils.println("Testing item ${stack.hoverName.string} for blockiness")
        val b = Block.byItem(stack.item)
        if (b === Blocks.AIR) return false
        if (b is EntityBlock) return false
        // 1.8+: the integer render types (0 cube, 31 logs, 39 quartz, 59 chisel) are all baked
        // models now; "camouflage-able" means a full-cube model.
        val state = b.defaultBlockState()
        Utils.println("Item is probably a block with render type ${state.renderShape}")
        return if (stack.item is SixNodeItem.Placer) false else
            state.renderShape == RenderShape.MODEL && Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
    }

    /** The block's metadata used to travel with the cache; block states have none (the default state is used). */
    override fun getMeta(stack: ItemStack): Int {
        return 0
    }
}
