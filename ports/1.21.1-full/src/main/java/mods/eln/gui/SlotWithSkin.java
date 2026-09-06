package mods.eln.gui;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotWithSkin extends Slot implements ISlotSkin {

    SlotSkin skin;

    public SlotWithSkin(IInventory par1iInventory, int par2, int par3, int par4, SlotSkin skin) {
        super(par1iInventory, par2, par3, par4);
        this.skin = skin;
    }

    @Override
    public ItemStack getStack() {
        ItemStack stack = super.getStack();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public boolean getHasStack() {
        return !getStack().isEmpty();
    }

    @Override
    public void putStack(ItemStack stack) {
        super.putStack(stack == null ? ItemStack.EMPTY : stack);
    }

    @Override
    public SlotSkin getSlotSkin() {
        return skin;
    }
}
