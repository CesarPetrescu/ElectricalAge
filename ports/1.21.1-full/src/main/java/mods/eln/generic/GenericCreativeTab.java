package mods.eln.generic;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class GenericCreativeTab extends CreativeModeTab {

    public Item item;

    public GenericCreativeTab(String label, Item item) {
        super(label);
        this.item = item;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack createIcon() {
        return new ItemStack(item);
    }
}
