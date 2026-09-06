package mods.eln.generic;

import mods.eln.Eln;
import mods.eln.registration.ElnRegistry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A family of descriptor-driven items that place a block: ores, and the six-node and
 * transparent-node elements. Up to 1.12.2 this was the block's single {@code ItemBlock} with the
 * descriptor in the damage value; it is now the registrar of one item per descriptor (see
 * {@link GenericItemUsingDamage} for the reasoning), and keeps the lookups the mod calls on
 * {@code Eln.sixNodeItem} and friends. Subclasses decide what the item is through {@link #newItem}.
 */
public class GenericItemBlockUsingDamage<Descriptor extends GenericItemBlockUsingDamageDescriptor> {

    public Hashtable<Integer, Descriptor> subItemList = new Hashtable<Integer, Descriptor>();
    public ArrayList<Integer> orderList = new ArrayList<Integer>();
    public ArrayList<Descriptor> descriptors = new ArrayList<Descriptor>();
    private final Map<Integer, CreativeModeTab> creativeTabByGroup = new HashMap<Integer, CreativeModeTab>();
    /** The block this family places (the shared node block; null for ores, which have one block each). Exists once blocks are registered. */
    public final Supplier<Block> block;
    public final String name;
    private CreativeModeTab creativeTab;

    public Descriptor defaultElement = null;

    public GenericItemBlockUsingDamage(Supplier<Block> b, String name) {
        this.block = b;
        this.name = name;
        CreativeTabPopulator.register(this);
    }

    public GenericItemBlockUsingDamage<Descriptor> setCreativeTab(CreativeModeTab tab) {
        creativeTab = tab;
        return this;
    }

    public void setDefaultElement(Descriptor descriptor) {
        defaultElement = descriptor;
    }

    public void doubleEntry(int src, int dst) {
        subItemList.put(dst, subItemList.get(src));
    }

    protected Item.Properties newProperties(Descriptor descriptor) {
        return new Item.Properties().stacksTo(descriptor.getItemStackLimit(ItemStack.EMPTY));
    }

    /** The registered item for one descriptor; runs inside the item RegisterEvent, so blocks exist. Ores make a BlockItem of their own block; node families their placer item. */
    protected Item newItem(int id, Descriptor descriptor) {
        return new DescriptorBlockItem<>(this, descriptor, id, block.get(), newProperties(descriptor));
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

    public void addDescriptor(int id, Descriptor descriptor) {
        add(id, descriptor);
        orderList.add(id);
        descriptors.add(descriptor);
        ElnRegistry.registerCustomItemStack(descriptor.name, () -> descriptor.newItemStack(1));
    }

    public void addWithoutRegistry(int id, Descriptor descriptor) {
        add(id, descriptor);
    }

    public Descriptor getDescriptor(int id) {
        return subItemList.get(id);
    }

    @SuppressWarnings("unchecked")
    public Descriptor getDescriptor(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return defaultElement;
        if (!(itemStack.getItem() instanceof IDescriptorItem item) || item.descriptorFamily() != this) return defaultElement;
        return (Descriptor) subItemList.get(item.legacyId());
    }

    /** Feeds the creative tab: every visible descriptor whose tab is {@code tab}, in registration order. */
    public void getSubItems(CreativeModeTab tab, Consumer<ItemStack> list) {
        for (int id : orderList) {
            Descriptor descriptor = subItemList.get(id);
              if (descriptor == null || descriptor.isHidden()) continue;
            CreativeModeTab descriptorTab = descriptor.getCreativeTab();
            if (descriptorTab == null) descriptorTab = creativeTab != null ? creativeTab : Eln.creativeTabOther;
            descriptorTab = CreativeCategories.resolve(descriptor, descriptorTab);
            if (tab == null || tab == descriptorTab) {
                list.accept(descriptor.newCreativeTabStack());
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
