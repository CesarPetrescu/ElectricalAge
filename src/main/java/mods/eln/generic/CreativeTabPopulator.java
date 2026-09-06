package mods.eln.generic;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Keeps track of the descriptor families so their items can be listed in the creative tabs.
 * 1.21: tab contents are assembled in {@link BuildCreativeModeTabContentsEvent}; the mod listens
 * for it and calls {@link #addEntries} for each of its tabs.
 */
public final class CreativeTabPopulator {

    private static final List<GenericItemBlockUsingDamage<?>> BLOCK_ITEMS = new ArrayList<GenericItemBlockUsingDamage<?>>();
    private static final List<GenericItemUsingDamage<?>> GENERIC_ITEMS = new ArrayList<GenericItemUsingDamage<?>>();
    /** Items outside the descriptor families (armor, tools, plain blocks): 1.7.10's {@code setCreativeTab}. */
    private static final List<Map.Entry<CreativeModeTab, java.util.function.Supplier<ItemStack>>> PLAIN_ITEMS = new ArrayList<>();

    private CreativeTabPopulator() {
    }

    public static void register(GenericItemBlockUsingDamage<?> item) {
        if (!BLOCK_ITEMS.contains(item)) {
            BLOCK_ITEMS.add(item);
        }
    }

    public static void register(GenericItemUsingDamage<?> item) {
        if (!GENERIC_ITEMS.contains(item)) {
            GENERIC_ITEMS.add(item);
        }
    }

    public static void register(CreativeModeTab tab, java.util.function.Supplier<ItemStack> stack) {
        PLAIN_ITEMS.add(Map.entry(tab, stack));
    }

    public static void addEntries(CreativeModeTab tab, Consumer<ItemStack> out) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<CreativeModeTab, java.util.function.Supplier<ItemStack>> entry : PLAIN_ITEMS) {
            if (entry.getKey() == tab) stacks.add(entry.getValue().get());
        }
        for (GenericItemBlockUsingDamage<?> item : BLOCK_ITEMS) {
            item.getSubItems(tab, stacks::add);
        }
        for (GenericItemUsingDamage<?> item : GENERIC_ITEMS) {
            item.getSubItems(tab, stacks::add);
        }
        stacks.sort(java.util.Comparator.comparing(CreativeTabPopulator::sortKey));
        List<ItemStack> unique = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || unique.stream().anyMatch(other -> ItemStack.isSameItemSameComponents(other, stack))) continue;
            unique.add(stack);
            out.accept(stack);
        }
    }

    private static String sortKey(ItemStack stack) {
        String family = stack.getItem() instanceof DescriptorBlockItem<?> item ? item.descriptor.getClass().getName() : stack.getItem().getClass().getName();
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        var matcher = java.util.regex.Pattern.compile("\\d+").matcher(id);
        StringBuilder ordered = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(ordered, String.format(java.util.Locale.ROOT, "%012d", Long.parseLong(matcher.group())));
        matcher.appendTail(ordered);
        return family + ":" + ordered;
    }

    public static void addEntries(BuildCreativeModeTabContentsEvent event) {
        addEntries(event.getTab(), event::accept);
    }
}
