package mods.eln.gui;

import net.minecraft.world.item.ItemStack;

public interface IItemStackFilter {
    boolean tryItemStack(ItemStack itemStack);
}
