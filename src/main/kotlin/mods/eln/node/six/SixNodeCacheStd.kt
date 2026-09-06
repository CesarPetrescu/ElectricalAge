package mods.eln.node.six

import mods.eln.misc.Utils
import mods.eln.node.ISixNodeCache
import net.minecraft.world.level.block.Block
import net.minecraft.block.BlockContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.RenderShape

class SixNodeCacheStd : ISixNodeCache {
    override fun accept(stack: ItemStack): Boolean {
        Utils.println("Testing item ${stack.hoverName} for blockiness")
        val b = Block.getBlockFromItem(stack.item) ?: return false
        if (b is BlockContainer) return false
        // 1.8+: the integer render types (0 cube, 31 logs, 39 quartz, 59 chisel) are all baked
        // models now; "camouflage-able" means a full-cube model.
        val state = b.defaultState
        Utils.println("Item is probably a block with render type ${state.renderType}")
        return if (stack.item is SixNodeItem) false else
            state.renderType == RenderShape.MODEL && state.isFullCube
    }

    override fun getMeta(stack: ItemStack): Int {
        return stack.itemDamage
    }
}
