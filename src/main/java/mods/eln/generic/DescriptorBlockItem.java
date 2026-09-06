package mods.eln.generic;

import mods.eln.misc.RealisticEnum;
import mods.eln.misc.Tooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The registered item behind one {@link GenericItemBlockUsingDamageDescriptor}: a {@link BlockItem}
 * whose vanilla hooks are adapted onto the descriptor's 1.7.10-shaped ones. Ores use it as is
 * (each ore descriptor has its own block); the node families give it a block to place and take
 * over {@link #useOn} in their subclasses.
 */
public class DescriptorBlockItem<D extends GenericItemBlockUsingDamageDescriptor> extends BlockItem implements IDescriptorItem {
    public final GenericItemBlockUsingDamage<D> family;
    public final D descriptor;
    private final int legacyId;

    public DescriptorBlockItem(GenericItemBlockUsingDamage<D> family, D descriptor, int legacyId, Block block, Properties properties) {
        super(block, properties);
        this.family = family;
        this.descriptor = descriptor;
        this.legacyId = legacyId;
    }

    @Override
    public Object descriptorFamily() {
        return family;
    }

    @Override
    public int legacyId() {
        return legacyId;
    }

    /** The 1.7.10 lang key ("Copper_Ore.name"): the six shipped language files keep working unchanged. */
    @Override
    public String getDescriptionId() {
        return descriptor.name.replaceAll("\\s+", "_") + ".name";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<String> listFromDescriptor = new ArrayList<>();
        List<String> realismData = new ArrayList<>();
        descriptor.addInformation(stack, Tooltips.viewer(), listFromDescriptor, flag.isAdvanced());
        RealisticEnum realism = descriptor.addRealismContext(realismData);
        Tooltips.showItemTooltip(listFromDescriptor, realismData, realism, tooltip);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        return descriptor.onEntityItemUpdate(entity);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (descriptor.onItemUseFirst(stack, context.getPlayer())) return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }
}
