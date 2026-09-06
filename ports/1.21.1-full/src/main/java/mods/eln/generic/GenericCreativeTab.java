package mods.eln.generic;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GenericCreativeTab extends CreativeModeTab {

    public Item item;

    public GenericCreativeTab(String label, Item item) {
        super(label);
        this.item = item;
    }

    @Override
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    public ItemStack createIcon() {
        return new ItemStack(item);
    }
}
