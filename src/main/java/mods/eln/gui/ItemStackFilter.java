package mods.eln.gui;

import mods.eln.misc.OreDict;
import mods.eln.misc.Utils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Accepts one item. The 1.7.10 damage mask is gone with the Flattening: every variant is its own item. */
public class ItemStackFilter implements IItemStackFilter {

    int itemId;

    public ItemStackFilter(Item item, int damageMask, int damageValue) {
        this(item);
    }

    public ItemStackFilter(Block block, int damageMask, int damageValue) {
        this(block);
    }

    public ItemStackFilter(Item item) {
        this.itemId = mods.eln.misc.McBridge.itemId(item);
    }

    public ItemStackFilter(Block block) {
        this.itemId = Utils.getItemId(block);
    }

    /** 1.7.10's ore-dictionary filter: one filter per item under the name (see {@link OreDict}). */
    public static ItemStackFilter[] OreDict(String name) {
        final List<ItemStack> ores = OreDict.getOres(name);
        ItemStackFilter[] filters = new ItemStackFilter[ores.size()];
        for (int i = 0; i < ores.size(); i++) {
            filters[i] = new ItemStackFilter(ores.get(i).getItem());
        }
        return filters;
    }

    @Override
    public boolean tryItemStack(ItemStack itemStack) {// caca1.5.1
        return Utils.getItemId(itemStack) == itemId;
    }
}
