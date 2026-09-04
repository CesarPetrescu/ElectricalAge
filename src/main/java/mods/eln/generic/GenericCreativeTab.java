package mods.eln.generic;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import mods.eln.Eln;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public class GenericCreativeTab extends CreativeTabs {

    private ItemStack iconStack;

    public GenericCreativeTab(String label, Item item) {
        this(label, new ItemStack(item));
    }

    public GenericCreativeTab(String label, ItemStack stack) {
        super(label);
        setIcon(stack);
    }

    public void setIcon(ItemStack stack) {
        if (stack == null) {
            this.iconStack = null;
        } else {
            this.iconStack = stack.copy();
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack createIcon() {
        return iconStack != null ? iconStack : new ItemStack(Items.REDSTONE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void displayAllRelevantItems(NonNullList<ItemStack> list) {
        super.displayAllRelevantItems(list);
        if (this != Eln.creativeTabOther) {
            CreativeTabPopulator.addEntries(this, list);
        }
    }
}
