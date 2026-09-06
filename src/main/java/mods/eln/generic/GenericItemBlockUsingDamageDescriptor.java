package mods.eln.generic;

import mods.eln.Eln;
import mods.eln.misc.RealisticEnum;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.List;

/**
 * One entry of a {@link GenericItemBlockUsingDamage} family (ores, six-node and transparent-node
 * elements). Since the 1.21 port every descriptor is its own registered {@link Item}
 * ({@link #parentItem}); {@link #parentItemDamage} is the legacy sub-id.
 */
public class GenericItemBlockUsingDamageDescriptor {

    String iconName;
    public String name;

    public static String INVALID_NAME = "$NO_DESCRIPTOR";

    public static HashMap<String, GenericItemBlockUsingDamageDescriptor> byName = new HashMap<>();

    public static GenericItemBlockUsingDamageDescriptor getByName(String name) {
        return byName.get(name);
    }

    public Item parentItem;
    public int parentItemDamage;

    public GenericItemBlockUsingDamageDescriptor(String name) {
        this(name, name);
    }

    public GenericItemBlockUsingDamageDescriptor(String name, String iconName) {
        setDefaultIcon(iconName);
        this.name = name;
        byName.put(name, this);
    }

    public void setDefaultIcon(String name) {
        // 1.13+ resource paths are [a-z0-9/._-]: "Suspended Lamp Socket (noswing)" is suspendedlampsocket_noswing
        String iconName = name.replaceAll(" ", "").toLowerCase().replaceAll("[()]", "_").replaceAll("_+$", "").replaceAll("[^a-z0-9/._-]", "_");
        if (Eln.uiIconsNoSymbols() &&
            getClass().getClassLoader().getResource("assets/eln/textures/blocks/" + iconName + "-ni.png") != null) {
            this.iconName = iconName + "-ni";
        } else {
            this.iconName = iconName;
        }
    }

    /** The sprite name under assets/eln/textures/blocks/ (no "eln:" prefix). */
    public String getIconName() {
        return iconName;
    }

    public CompoundTag getDefaultNBT() {
        return null;
    }

    public void addInformation(ItemStack itemStack, Player entityPlayer, List<String> list, boolean par4) {
    }

    public RealisticEnum addRealismContext(List<String> list) {
        return null;
    }

    public String getName(ItemStack stack) {
        return name;
    }

    private boolean hidden = false;

    public void setParent(Item item, int damage) {
        this.parentItem = item;
        this.parentItemDamage = damage;
    }

    private Item item() {
        if (parentItem == null) throw new IllegalStateException("descriptor " + name + " has no item yet");
        return parentItem;
    }

    public ItemStack newItemStack(int size) {
        return new ItemStack(item(), size);
    }

    public ItemStack newItemStack() {
        return new ItemStack(item(), 1);
    }

    public ItemStack newCreativeTabStack() {
        ItemStack stack = new ItemStack(item(), 1);
        CompoundTag nbt = getDefaultNBT();
        if (nbt != null) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        return stack;
    }

    public int getItemStackLimit(ItemStack stack) {
        return 64;
    }

    public boolean checkSameItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() == parentItem;
    }

    public static GenericItemBlockUsingDamageDescriptor getDescriptor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof IDescriptorItem item)) return null;
        if (!(item.descriptorFamily() instanceof GenericItemBlockUsingDamage<?> family)) return null;
        return family.getDescriptor(stack);
    }

    public static GenericItemBlockUsingDamageDescriptor getDescriptor(ItemStack stack, Class<?> extendClass) {
        GenericItemBlockUsingDamageDescriptor desc = getDescriptor(stack);
        if (desc == null) return null;
        if (!extendClass.isAssignableFrom(desc.getClass())) return null;
        return desc;
    }

    public boolean onEntityItemUpdate(ItemEntity entityItem) {
        return false;
    }

    public boolean onItemUseFirst(ItemStack stack, Player player) {
        return false;
    }

    private CreativeModeTab creativeTab;

    public GenericItemBlockUsingDamageDescriptor setCreativeTab(CreativeModeTab creativeTab) {
        this.creativeTab = creativeTab;
        return this;
    }

    public CreativeModeTab getCreativeTab() {
        return creativeTab;
    }

    public GenericItemBlockUsingDamageDescriptor hideFromCreative() {
        this.hidden = true;
        return this;
    }

    public boolean isHidden() {
        return hidden;
    }
}
