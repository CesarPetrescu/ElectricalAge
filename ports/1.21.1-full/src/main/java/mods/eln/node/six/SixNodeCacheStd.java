package mods.eln.node.six;

import mods.eln.node.ISixNodeCache;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.item.ItemStack;

public class SixNodeCacheStd implements ISixNodeCache {

    @Override
    public boolean accept(ItemStack stack) {

        Block b = Block.getBlockFromItem(stack.getItem());
        if (b == null) return false;
        if (b instanceof BaseEntityBlock) return false;
        return b.getRenderType(b.getDefaultState()).equals(0) && !(stack.getItem() instanceof SixNodeItem);
    }

    @Override
    public int getMeta(ItemStack stack) {

        return stack.getItemDamage();
    }

}
