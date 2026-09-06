package mods.eln.node.transparent;

import mods.eln.generic.GenericItemBlockUsingDamage;
import mods.eln.ghost.GhostGroup;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class TransparentNodeItem extends GenericItemBlockUsingDamage<TransparentNodeDescriptor> {


    public TransparentNodeItem(Block b) {
        super(b);
        setHasSubtypes(true);
    }


    @Override
    public InteractionResult onItemUse(Player player, Level world, BlockPos pos, InteractionHand hand, net.minecraft.core.Direction facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return InteractionResult.FAIL;

        BlockState iblockstate = world.getBlockState(pos);
        Block block = iblockstate.getBlock();

        if (!block.isReplaceable(world, pos)) {
            pos = pos.offset(facing);
        }

        if (player.canPlayerEdit(pos, facing, stack) && world.mayPlace(this.block, pos, false, facing, null)) {
            TransparentNodeDescriptor descriptor = getDescriptor(stack);
            if (descriptor == null) return InteractionResult.FAIL;

            Direction direction = Direction.fromFacing(facing).getInverse();
            Direction front = descriptor.getFrontFromPlace(direction, player);

            // Apply spawn delta
            int[] v = new int[]{descriptor.getSpawnDeltaX(), descriptor.getSpawnDeltaY(), descriptor.getSpawnDeltaZ()};
            front.rotateFromXN(v);
            BlockPos adjustedPos = pos.add(v[0], v[1], v[2]);

            if (!world.getBlockState(adjustedPos).getBlock().isReplaceable(world, adjustedPos)) {
                return InteractionResult.FAIL;
            }

            Coordinate coord = new Coordinate(adjustedPos, world);
            String error = descriptor.checkCanPlace(coord, front);
            if (error != null) {
                if (!world.isRemote) Utils.sendMessage(player, error);
                return InteractionResult.FAIL;
            }

            if (world.isRemote) return InteractionResult.SUCCESS;

            // Plot ghosts
            GhostGroup ghostgroup = descriptor.getGhostGroup(front);
            if (ghostgroup != null) ghostgroup.plot(coord, coord, descriptor.getGhostGroupUuid());

            // Create Node
            TransparentNode node = new TransparentNode();
            node.onBlockPlacedBy(coord, front, player, stack);

            // Set block state
            int metadata = node.getBlockMetadata();
            BlockState newState = this.block.getStateFromMeta(metadata);
            if (world.setBlockState(adjustedPos, newState, 3)) {
                // Play placement sound
                SoundType soundtype = this.block.getSoundType(newState, world, adjustedPos, player);
                world.playSound(player, adjustedPos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
                
                // Notify block
                ((NodeBlock) this.block).onBlockPlacedBy(world, adjustedPos, front, player, newState);
                
                stack.shrink(1);
                node.checkCanStay(true);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.FAIL;
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, Player player, Level world, BlockPos pos, net.minecraft.core.Direction side, float hitX, float hitY, float hitZ, BlockState state) {
        // Handled in onItemUse
        return false;
    }

    // TODO(1.10): Fix item rendering.
//    @Override
//    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
//        TransparentNodeDescriptor d = getDescriptor(item);
//        if (Utils.nullCheck(d)) return false;
//        return d.handleRenderType(item, type);
//    }
//
//    @Override
//    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item,
//                                         ItemRendererHelper helper) {
//
//        return getDescriptor(item).shouldUseRenderHelper(type, item, helper);
//    }
//
//    public boolean shouldUseRenderHelperEln(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
//        return getDescriptor(item).shouldUseRenderHelperEln(type, item, helper);
//    }
//
//    @Override
//    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
//        Minecraft.getMinecraft().profiler.startSection("TransparentNodeItem");
//
//        if (shouldUseRenderHelperEln(type, item, null)) {
//            switch (type) {
//                case ENTITY:
//                    GL11.glTranslatef(0.00f, 0.3f, 0.0f);
//                    break;
//                case EQUIPPED_FIRST_PERSON:
//                    GL11.glTranslatef(0.50f, 1, 0.5f);
//                    break;
//                case EQUIPPED:
//                    GL11.glTranslatef(0.50f, 1, 0.5f);
//                    break;
//                case FIRST_PERSON_MAP:
//                    break;
//                case INVENTORY:
//                    GL11.glRotatef(90, 0, 1, 0);
//                    break;
//                default:
//                    break;
//            }
//        }
//        getDescriptor(item).renderItem(type, item, data);
//
//        Minecraft.getMinecraft().profiler.endSection();
//    }
}
