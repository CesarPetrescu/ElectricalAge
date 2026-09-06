package mods.eln.generic;

import mods.eln.misc.RealisticEnum;
import mods.eln.misc.Tooltips;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The registered {@link Item} behind one {@link GenericItemUsingDamageDescriptor}. 1.13 removed item
 * metadata, so what used to be `sharedItem` with 200 damage values is 200 of these. This class is
 * the 1.21 API boundary: the descriptor hooks keep their 1.7.10 signatures (stack first, int
 * coordinates, `List<String>` tooltips) and are adapted here, once.
 */
public class DescriptorItem<D extends GenericItemUsingDamageDescriptor> extends Item implements IDescriptorItem {
    public final GenericItemUsingDamage<D> family;
    public final D descriptor;
    private final int legacyId;

    public DescriptorItem(GenericItemUsingDamage<D> family, D descriptor, int legacyId, Properties properties) {
        super(properties);
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

    /** The 1.7.10 lang key ("Copper_Dust.name"): the six shipped language files keep working unchanged. */
    @Override
    public String getDescriptionId() {
        return descriptor.name.replaceAll("\\s+", "_") + ".name";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        ItemStack result = descriptor.onItemRightClick(s, level, player);
        // 1.7.10 swung the arm only when the stack changed; keep that.
        boolean changed = result != s || result.getCount() != s.getCount();
        return changed ? InteractionResultHolder.success(result) : InteractionResultHolder.pass(result);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        var hit = context.getClickLocation();
        boolean handled = descriptor.onItemUse(context.getItemInHand(), context.getPlayer(), context.getLevel(),
            pos.getX(), pos.getY(), pos.getZ(), context.getClickedFace().get3DDataValue(),
            (float) (hit.x - pos.getX()), (float) (hit.y - pos.getY()), (float) (hit.z - pos.getZ()));
        return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        descriptor.onUpdate(stack, level, entity, slotId, isSelected);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return descriptor.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miningEntity) {
        if (level.isClientSide) return false;
        return descriptor.onBlockDestroyed(stack, level, state, pos, miningEntity);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return descriptor.onDroppedByPlayer(item, player);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return descriptor.onEntitySwing(entity, stack);
    }

    // Item.onBlockStartBreak is gone on NeoForge 1.21: the descriptor's hook of that name is driven
    // from BlockEvent.BreakEvent by the mod's event handler instead (see ElnForgeEventsHandler).
}
