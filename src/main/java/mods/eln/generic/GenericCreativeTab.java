package mods.eln.generic;

import mods.eln.registration.ElnRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Electrical Age's creative tabs. 1.21 builds tabs through {@link CreativeModeTab.Builder} and
 * registers them like any other registry object, and their contents come from
 * {@link CreativeTabPopulator} (via {@code BuildCreativeModeTabContentsEvent}) rather than from the
 * tab itself. The icon is read through a supplier, so {@link #setIcon} still works after the fact,
 * once the descriptor whose item is the icon exists.
 */
public final class GenericCreativeTab {
    private static final Map<CreativeModeTab, ItemStack> ICONS = new HashMap<>();

    private GenericCreativeTab() {
    }

    public static CreativeModeTab create(String label, ItemLike icon) {
        return create(label, new ItemStack(icon));
    }

    /** {@code label} is the 1.7.10 tab label; its lang key stays {@code itemGroup.<label>}. */
    public static CreativeModeTab create(String label, ItemStack icon) {
        CreativeModeTab[] self = new CreativeModeTab[1];
        CreativeModeTab tab = CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + label))
            .icon(() -> {
                ItemStack stack = ICONS.get(self[0]);
                return stack == null || stack.isEmpty() ? new ItemStack(Items.REDSTONE) : stack;
            })
            .build();
        self[0] = tab;
        ICONS.put(tab, icon.copy());
        return ElnRegistry.registerCreativeTab(label, tab);
    }

    public static void setIcon(CreativeModeTab tab, ItemStack stack) {
        ICONS.put(tab, stack == null ? ItemStack.EMPTY : stack.copy());
    }
}
