package mods.eln.generic;

import mods.eln.client.itemrender.IItemRenderer;
import net.minecraft.world.item.ItemStack;

/**
 * The two general-purpose families ({@code Eln.sharedItem}, {@code Eln.sharedItemStackOne}). The
 * {@link IItemRenderer} dispatch is what the phase-3 item renderer calls for descriptors that draw
 * themselves. (1.7.10's {@code ISpecialArmor} on this class was dead code: nothing could wear a
 * shared item.)
 */
public class SharedItem extends GenericItemUsingDamage<GenericItemUsingDamageDescriptor> implements IItemRenderer {

    public SharedItem(String name) {
        super(name);
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        GenericItemUsingDamageDescriptor d = getDescriptor(item);
        if (d == null) return false;
        return d.handleRenderType(item, type);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        GenericItemUsingDamageDescriptor d = getDescriptor(item);
        if (d == null) return false;
        return d.shouldUseRenderHelper(type, item, helper);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GenericItemUsingDamageDescriptor d = getDescriptor(item);
        if (d != null) {
            d.renderItem(type, item, data);
        }
    }
}
