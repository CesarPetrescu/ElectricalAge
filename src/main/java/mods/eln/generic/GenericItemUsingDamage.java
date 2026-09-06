package mods.eln.generic;

import mods.eln.Eln;
import mods.eln.registration.ElnRegistry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A family of descriptor-driven items. Up to 1.12.2 this <em>was</em> the item: one {@code Item}
 * whose damage value selected a {@link GenericItemUsingDamageDescriptor}. Item metadata is gone
 * since 1.13, so the family now registers one {@link DescriptorItem} per descriptor and keeps the
 * lookups ({@link #getDescriptor(ItemStack)}, the legacy id table, creative-tab grouping) that the
 * rest of the mod calls on {@code Eln.sharedItem} and friends.
 */
public class GenericItemUsingDamage<Descriptor extends GenericItemUsingDamageDescriptor> {
    public Hashtable<Integer, Descriptor> subItemList = new Hashtable<Integer, Descriptor>();
    ArrayList<Integer> orderList = new ArrayList<Integer>();
    private final Map<Integer, CreativeModeTab> creativeTabByGroup = new HashMap<Integer, CreativeModeTab>();
    public final String name;
    private int maxStackSize = 64;
    private CreativeModeTab creativeTab;

    Descriptor defaultElement = null;

    public GenericItemUsingDamage(String name) {
        this.name = name;
        CreativeTabPopulator.register(this);
    }

    public GenericItemUsingDamage<Descriptor> setMaxStackSize(int size) {
        maxStackSize = size;
        return this;
    }

    public GenericItemUsingDamage<Descriptor> setCreativeTab(CreativeModeTab tab) {
        creativeTab = tab;
        return this;
    }

    public void setDefaultElement(Descriptor descriptor) {
        defaultElement = descriptor;
    }

    /** Item properties for one descriptor; families with special needs override this. */
    protected Item.Properties newProperties(Descriptor descriptor) {
        return new Item.Properties().stacksTo(maxStackSize);
    }

    /** The registered item for one descriptor; families that need an {@code Item} subclass override this. */
    protected Item newItem(int id, Descriptor descriptor) {
        return new DescriptorItem<>(this, descriptor, id, newProperties(descriptor));
    }

    private void add(int id, Descriptor descriptor) {
        Descriptor previous = subItemList.put(id, descriptor);
        if (previous != null && previous != descriptor) {
            throw new IllegalStateException(name + ": legacy id " + id + " used by both " + previous.name + " and " + descriptor.name);
        }
        // The Item itself is created inside the item RegisterEvent (see ElnRegistry); the
        // descriptor learns about it then, and stack-needing callers wait for afterItems.
        ElnRegistry.registerDescriptorItem(descriptor.name, id, () -> newItem(id, descriptor), item -> descriptor.setParent(item, id));
        applyDefaultTab(id, descriptor);
    }

    public void addWithoutRegistry(int id, Descriptor descriptor) {
        add(id, descriptor);
    }

    public void addElement(int id, Descriptor descriptor) {
        add(id, descriptor);
        orderList.add(id);
        ElnRegistry.registerCustomItemStack(descriptor.name, () -> descriptor.newItemStack(1));
    }

    public Descriptor getDescriptor(int id) {
        return subItemList.get(id);
    }

    @SuppressWarnings("unchecked")
    public Descriptor getDescriptor(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty())
            return defaultElement;
        if (!(itemStack.getItem() instanceof DescriptorItem<?> item) || item.family != this)
            return defaultElement;
        return (Descriptor) item.descriptor;
    }

    /** Every registered descriptor, in registration order. */
    public List<Descriptor> descriptors() {
        List<Descriptor> list = new ArrayList<>(orderList.size());
        for (int id : orderList) list.add(subItemList.get(id));
        return list;
    }

    /** Feeds the creative tab: every visible descriptor whose tab is {@code tab}, in registration order. */
    public void getSubItems(CreativeModeTab tab, Consumer<ItemStack> list) {
        for (int id : orderList) {
            Descriptor descriptor = subItemList.get(id);
            if (descriptor == null || descriptor.isHidden()) continue;
            CreativeModeTab descriptorTab = descriptor.getCreativeTab();
            if (descriptorTab == null) descriptorTab = creativeTab != null ? creativeTab : Eln.creativeTabOther;
            if (tab == null || tab == descriptorTab) {
                list.accept(descriptor.newItemStack(1));
            }
        }
    }

    private void applyDefaultTab(int id, Descriptor descriptor) {
        if (descriptor.getCreativeTab() != null) return;
        CreativeModeTab tab = creativeTabByGroup.get(id >> 6);
        if (tab != null) {
            descriptor.setCreativeTab(tab);
        }
    }

    public void setCreativeTabForGroup(int group, CreativeModeTab tab) {
        creativeTabByGroup.put(group, tab);
    }
}
