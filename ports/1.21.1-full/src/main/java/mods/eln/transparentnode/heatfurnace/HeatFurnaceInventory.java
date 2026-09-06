package mods.eln.transparentnode.heatfurnace;

import mods.eln.node.transparent.TransparentNodeElement;
import mods.eln.node.transparent.TransparentNodeElementInventory;
import mods.eln.node.transparent.TransparentNodeElementRender;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;

public class HeatFurnaceInventory extends TransparentNodeElementInventory {
    public HeatFurnaceInventory(int size, int stackLimit, TransparentNodeElement TransparentNodeElement) {
        super(size, stackLimit, TransparentNodeElement);
    }

    public HeatFurnaceInventory(int size, int stackLimit, TransparentNodeElementRender TransparentnodeRender) {
        super(size, stackLimit, TransparentnodeRender);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{HeatFurnaceContainer.combustibleId};
    }

    @Override
    public boolean canPlaceItemThroughFace(int var1, ItemStack var2, Direction var3) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int var1, ItemStack var2, Direction var3) {
        return false;
    }
}
