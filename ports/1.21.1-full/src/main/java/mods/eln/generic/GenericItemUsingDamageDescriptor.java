package mods.eln.generic;

import mods.eln.misc.VoltageLevelColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

public class GenericItemUsingDamageDescriptor {

    public String IconName;
    public String name;
    public VoltageLevelColor voltageLevelColor = VoltageLevelColor.None;

    public Item parentItem;
    public int parentItemDamage;

    public GenericItemUsingDamageDescriptor(String name) {
        this(name, name);
    }

    public GenericItemUsingDamageDescriptor(String name, String iconName) {
        setDefaultIcon(iconName);
        this.name = name;
    }

    public void setDefaultIcon(String name) {
        this.IconName = "eln:" + name.replaceAll(" ", "").toLowerCase();
    }

    public CompoundTag getDefaultNBT() {
        return null;
    }

    public void addInformation(ItemStack itemStack, Player entityPlayer, List list, boolean par4) {

    }

    public InteractionResultHolder<ItemStack> onItemRightClick(ItemStack s, Level w, Player p) {
        return new InteractionResultHolder(InteractionResult.PASS, s);
    }

    public void getSubItems(List list) {
        ItemStack stack = newItemStack(1);
        list.add(stack);
    }

    // TODO(1.10): These are all implicit now.
//    @SideOnly(value = Side.CLIENT)
//    public void updateIcons(IIconRegister iconRegister) {
//        this.iconIndex = iconRegister.registerIcon(IconName);
//    }
//
//    public IIcon getIcon() {
//        return iconIndex;
//    }

    public String getName(ItemStack stack) {
        return name;
    }

    public static GenericItemUsingDamageDescriptor getDescriptor(ItemStack stack) {
        if (stack == null)
            return null;
        if (!(stack.getItem() instanceof GenericItemUsingDamage))
            return null;
        return ((GenericItemUsingDamage<GenericItemUsingDamageDescriptor>) stack.getItem()).getDescriptor(stack);
    }

    public static GenericItemUsingDamageDescriptor getDescriptor(ItemStack stack, Class extendClass) {
        GenericItemUsingDamageDescriptor desc = getDescriptor(stack);
        if (desc == null)
            return null;
        if (extendClass.isAssignableFrom(desc.getClass()) == false)
            return null;
        return desc;
    }

    public void setParent(Item item, int damage) {
        this.parentItem = item;
        this.parentItemDamage = damage;
    }

    public ItemStack newItemStack(int size) {
        ItemStack stack = new ItemStack(parentItem, size, parentItemDamage);
        stack.setTagCompound(getDefaultNBT());
        return stack;
    }

    public ItemStack newItemStack() {
        return newItemStack(1);
    }

    public boolean checkSameItemStack(@Nonnull ItemStack stack) {
        return stack.getItem() == parentItem && stack.getItemDamage() == parentItemDamage;
    }

    /**
     * Callback for item usage. If the item does something special on right clicking, he will have one of those. Return
     * True if something happen and false if it don't. This is for ITEMS, not BLOCKS
     */
    public InteractionResult onItemUse(ItemStack stack, Player player, Level world, BlockPos pos, InteractionHand hand, Direction facing, float vx, float vy, float vz) {
        return InteractionResult.PASS;
    }

    // TODO(1.10): Fix item render.
//    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
//        return voltageLevelColor != VoltageLevelColor.None;
//    }
//
//    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
//        return false;
//    }
//
//    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
//        if (getIcon() == null)
//            return;
//
//        voltageLevelColor.drawIconBackground(type);
//
//        // remove "eln:" to add the full path replace("eln:", "textures/blocks/") + ".png";
//        String icon = getIcon().getIconName().substring(4);
//        UtilsClient.drawIcon(type, new ResourceLocation("eln", "textures/items/" + icon + ".png"));
//    }

    public void onUpdate(ItemStack stack, Level world, Entity entity, int par4, boolean par5) {
    }

    protected CompoundTag getNbt(ItemStack stack) {
        CompoundTag nbt = stack.getTagCompound();
        if (nbt == null) {
            stack.setTagCompound(nbt = getDefaultNBT());
        }
        return nbt;
    }

    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 0.2f;
    }

    public boolean onBlockDestroyed(ItemStack stack, Level w, BlockState state, BlockPos pos, LivingEntity entity) {
        return false;
    }

    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return true;
    }

    public boolean onEntitySwing(LivingEntity entityLiving, ItemStack stack) {
        return false;
    }

    public boolean onBlockStartBreak(ItemStack itemstack, int x, int y, int z, Player player) {
        return false;
    }
}
