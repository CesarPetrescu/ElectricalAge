package mods.eln.generic;

import mods.eln.misc.Utils;
import mods.eln.misc.UtilsClient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class GenericItemBlockUsingDamage<Descriptor extends GenericItemBlockUsingDamageDescriptor> extends BlockItem {

    public Hashtable<Integer, Descriptor> subItemList = new Hashtable<Integer, Descriptor>();
    public ArrayList<Integer> orderList = new ArrayList<Integer>();
    public ArrayList<Descriptor> descriptors = new ArrayList<Descriptor>();

    public Descriptor defaultElement = null;

    public GenericItemBlockUsingDamage(Block b) {
        super(b);
        setHasSubtypes(true);
    }

    public void setDefaultElement(Descriptor descriptor) {
        defaultElement = descriptor;
    }

    public void doubleEntry(int src, int dst) {
        subItemList.put(dst, subItemList.get(src));
    }

    public void addDescriptor(int damage, Descriptor descriptor) {
        subItemList.put(damage, descriptor);
        ItemStack stack = new ItemStack(this, 1, damage);
        stack.setTagCompound(descriptor.getDefaultNBT());
        //LanguageRegistry.addName(stack, descriptor.name);
        orderList.add(damage);
        descriptors.add(descriptor);
        descriptor.setParent(this, damage);
        // In 1.12.2, items are registered via RegistryEvent.Register, not here
        // The parent ItemBlock is already registered, descriptors are just metadata
    }

    public void addWithoutRegistry(int damage, Descriptor descriptor) {
        subItemList.put(damage, descriptor);
        ItemStack stack = new ItemStack(this, 1, damage);
        stack.setTagCompound(descriptor.getDefaultNBT());
        descriptor.setParent(this, damage);
    }

    public Descriptor getDescriptor(int damage) {
        return subItemList.get(damage);
    }

    public Descriptor getDescriptor(ItemStack itemStack) {
        if (itemStack == null) return defaultElement;
        if (itemStack.getItem() != this) return defaultElement;
        return getDescriptor(itemStack.getItemDamage());
    }

	/*
    @Override
	@SideOnly(Side.CLIENT)
	public int getIconFromDamage(int damage) {
		return getDescriptor(damage).getIconId();
		
	}
	//caca1.5.1
	@Override
	public String getTextureFile () {
		return CommonProxy.ITEMS_PNG;
	}
	@Override
	public String getItemNameIS(ItemStack itemstack) {
		return getItemName() + "." + getDescriptor(itemstack).name;
	}
	*/

	/*@Override
    public String getItemStackDisplayName(ItemStack par1ItemStack) {
		Descriptor desc = getDescriptor(par1ItemStack);
		if(desc == null) return "Unknown";
        return desc.getName(par1ItemStack);
    }*/

    @Override
    public String getTranslationKey(ItemStack stack) {
        Descriptor desc = getDescriptor(stack);
        if (desc == null) {
            return "tile." + this.getClass().getName().toLowerCase();
        } else {
            return "tile." + desc.name.toLowerCase().replaceAll("\\s+", "_");
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(CreativeModeTab tabs, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tabs)) {
            // Add all sub-items to the creative tab
            for (int id : orderList) {
                Descriptor descriptor = subItemList.get(id);
                if (descriptor != null) {
                    ItemStack stack = new ItemStack(this, 1, id);
                    stack.setTagCompound(descriptor.getDefaultNBT());
                    items.add(stack);
                }
            }
        }
    }

    public void addInformation(ItemStack itemStack, Player entityPlayer, List list, boolean par4) {
        Descriptor desc = getDescriptor(itemStack);
        if (desc == null) return;
        List listFromDescriptor = new ArrayList();
        desc.addInformation(itemStack, entityPlayer, listFromDescriptor, par4);
        UtilsClient.showItemTooltip(listFromDescriptor, list);
    }
}
