package mods.eln.generic;

import mods.eln.registration.ElnRegistry;

import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import mods.eln.Eln;
import mods.eln.misc.RealisticEnum;
import mods.eln.misc.UtilsClient;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class GenericItemUsingDamage<Descriptor extends GenericItemUsingDamageDescriptor> extends Item implements IGenericItemUsingDamage {
    public Hashtable<Integer, Descriptor> subItemList = new Hashtable<Integer, Descriptor>();
    ArrayList<Integer> orderList = new ArrayList<Integer>();
    private final Map<Integer, CreativeTabs> creativeTabByGroup = new HashMap<Integer, CreativeTabs>();

    Descriptor defaultElement = null;

    public GenericItemUsingDamage() {
        super();
        setHasSubtypes(true);
        CreativeTabPopulator.register(this);
    }

    public void setDefaultElement(Descriptor descriptor) {
        defaultElement = descriptor;
    }

    @SuppressWarnings("deprecation")
    public void addWithoutRegistry(int damage, Descriptor descriptor) {
        subItemList.put(damage, descriptor);
        ItemStack stack = new ItemStack(this, 1, damage);
        descriptor.setParent(this, damage);
        applyDefaultTab(damage, descriptor);
    }

    @SuppressWarnings("deprecation")
    public void addElement(int damage, Descriptor descriptor) {
        subItemList.put(damage, descriptor);
        ItemStack stack = new ItemStack(this, 1, damage);
        orderList.add(damage);
        descriptor.setParent(this, damage);
        applyDefaultTab(damage, descriptor);
        ElnRegistry.registerCustomItemStack(descriptor.name, descriptor.newItemStack(1));
    }

    public Descriptor getDescriptor(int damage) {
        return subItemList.get(damage);
    }

    public Descriptor getDescriptor(ItemStack itemStack) {
        if (itemStack == null)
            return defaultElement;
        if (itemStack.getItem() != this)
            return defaultElement;
        return getDescriptor(itemStack.getItemDamage());
    }

    // 1.12.2 boundary: the descriptors keep their 1.7.10-shaped hooks (stack first, int coordinates);
    // this class adapts the vanilla signatures once instead of touching ~100 descriptor overrides.
    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        Descriptor desc = getDescriptor(s);
        if (desc == null)
            return new ActionResult<>(EnumActionResult.PASS, s);
        ItemStack result = desc.onItemRightClick(s, w, p);
        // 1.7.10 swung the arm only when the stack changed; keep that.
        boolean changed = result != s || result.getCount() != s.getCount();
        return new ActionResult<>(changed ? EnumActionResult.SUCCESS : EnumActionResult.PASS, result);
    }

	/*//caca1.5.1
    @Override
	@SideOnly(Side.CLIENT)
	public int getIconFromDamage(int damage) {
	return getDescriptor(damage).getIconId();

	}
	@Override
	public String getTextureFile () {
	return CommonProxy.ITEMS_PNG;
	}
	@Override
	public String getItemNameIS(ItemStack itemstack) {
	return getItemName() + "." + getDescriptor(itemstack).name;
	}

	/*
	@Override
	public String getUnlocalizedNameInefficiently(ItemStack par1ItemStack) {
		return "trololol";
	}
	*/

    @Override
    public String getTranslationKey(ItemStack par1ItemStack) {
        Descriptor desc = getDescriptor(par1ItemStack);
        if (desc != null) {
            return desc.name.replaceAll("\\s+", "_");
        } else {
            return null;
        }
    }

    @Override
    public String getUnlocalizedNameInefficiently(ItemStack stack) {
        return getTranslationKey(stack);
    }

	/*
	@Override
	public String getItemStackDisplayName(ItemStack par1ItemStack) {
		Descriptor desc = getDescriptor(par1ItemStack);
		if (desc == null)
			return "NullItem";
		return desc.getName(par1ItemStack);
	}
	*/



    @Override
    public void getSubItems(CreativeTabs tabs, NonNullList<ItemStack> list) {
        // You can also take a more direct approach and do each one individual but I prefer the lazy / right way
        for (int id : orderList) {
            Descriptor descriptor = subItemList.get(id);
            if (descriptor == null || descriptor.isHidden()) continue;
            CreativeTabs descriptorTab = descriptor.getCreativeTab();
            if (descriptorTab == null) descriptorTab = Eln.creativeTabOther;
            if (tabs == null || tabs == descriptorTab || tabs == CreativeTabs.SEARCH) {
                descriptor.getSubItems(list);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, World world, List<String> list, ITooltipFlag flag) {
        Descriptor desc = getDescriptor(itemStack);
        if (desc == null) return;
        List<String> listFromDescriptor = new ArrayList<String>();
        List<String> realismData = new ArrayList<String>();
        desc.addInformation(itemStack, Minecraft.getMinecraft().player, listFromDescriptor, flag.isAdvanced());
        RealisticEnum realism = desc.addRealismContext(realismData);
        UtilsClient.showItemTooltip(listFromDescriptor, realismData, realism, list);
    }

    /**
     * Callback for item usage. If the item does something special on right clicking, he will have one of those. Return
     * True if something happen and false if it don't. This is for ITEMS, not BLOCKS
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float vx, float vy, float vz) {
        ItemStack stack = player.getHeldItem(hand);
        GenericItemUsingDamageDescriptor d = getDescriptor(stack);
        if (d == null)
            return EnumActionResult.PASS;
        return d.onItemUse(stack, player, world, pos.getX(), pos.getY(), pos.getZ(), side.getIndex(), vx, vy, vz)
            ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
    }

    public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack) {
        GenericItemUsingDamageDescriptor d = getDescriptor(stack);
        if (d == null)
            return super.onEntitySwing(entityLiving, stack);
        return d.onEntitySwing(entityLiving, stack);
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player) {
        GenericItemUsingDamageDescriptor d = getDescriptor(itemstack);
        if (d == null)
            return super.onBlockStartBreak(itemstack, pos, player);
        return d.onBlockStartBreak(itemstack, pos.getX(), pos.getY(), pos.getZ(), player);
    }

    public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5) {
        if (world.isRemote) {
            return;
        }

        GenericItemUsingDamageDescriptor d = getDescriptor(stack);

        if (d == null)
            return;
        d.onUpdate(stack, world, entity, par4, par5);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        GenericItemUsingDamageDescriptor d = getDescriptor(stack);
        if (d == null)
            return 0.2f;
        return d.getDestroySpeed(stack, state);
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        return true;
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World w, IBlockState state, BlockPos pos, EntityLivingBase entity) {
        if (w.isRemote) {
            return false;
        }

        GenericItemUsingDamageDescriptor d = getDescriptor(stack);

        if (d == null)
            return true;
        return d.onBlockDestroyed(stack, w, state, pos, entity);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, EntityPlayer player) {
        GenericItemUsingDamageDescriptor d = getDescriptor(item);
        if (d == null)
            return super.onDroppedByPlayer(item, player);
        return d.onDroppedByPlayer(item, player);
    }

    private void applyDefaultTab(int damage, Descriptor descriptor) {
        if (descriptor.getCreativeTab() != null) return;
        CreativeTabs tab = creativeTabByGroup.get(damage >> 6);
        if (tab != null) {
            descriptor.setCreativeTab(tab);
        }
    }

    public void setCreativeTabForGroup(int group, CreativeTabs tab) {
        creativeTabByGroup.put(group, tab);
    }
}
