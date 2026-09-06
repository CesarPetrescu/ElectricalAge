package mods.eln.node.transparent;

import mods.eln.generic.GenericItemBlockUsingDamage;
import mods.eln.ghost.GhostGroup;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.block.SoundType;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TransparentNodeItem extends GenericItemBlockUsingDamage<TransparentNodeDescriptor> {


    public TransparentNodeItem(Block b) {
        super(b);
        setHasSubtypes(true);
    }


    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.FAIL;

        IBlockState iblockstate = world.getBlockState(pos);
        Block block = iblockstate.getBlock();

        if (!block.isReplaceable(world, pos)) {
            pos = pos.offset(facing);
        }

        if (player.canPlayerEdit(pos, facing, stack) && world.mayPlace(this.block, pos, false, facing, null)) {
            TransparentNodeDescriptor descriptor = getDescriptor(stack);
            if (descriptor == null) return EnumActionResult.FAIL;

            Direction direction = Direction.fromFacing(facing).getInverse();
            Direction front = descriptor.getFrontFromPlace(direction, player);

            // Apply spawn delta
            int[] v = new int[]{descriptor.getSpawnDeltaX(), descriptor.getSpawnDeltaY(), descriptor.getSpawnDeltaZ()};
            front.rotateFromXN(v);
            BlockPos adjustedPos = pos.add(v[0], v[1], v[2]);

            if (!world.getBlockState(adjustedPos).getBlock().isReplaceable(world, adjustedPos)) {
                return EnumActionResult.FAIL;
            }

            Coordinate coord = new Coordinate(adjustedPos, world);
            String error = descriptor.checkCanPlace(coord, front);
            if (error != null) {
                if (!world.isRemote) Utils.sendMessage(player, error);
                return EnumActionResult.FAIL;
            }

            if (world.isRemote) return EnumActionResult.SUCCESS;

            // Plot ghosts
            GhostGroup ghostgroup = descriptor.getGhostGroup(front);
            if (ghostgroup != null) ghostgroup.plot(coord, coord, descriptor.getGhostGroupUuid());

            // Create Node
            TransparentNode node = new TransparentNode();
            node.onBlockPlacedBy(coord, front, player, stack);

            // Set block state
            int metadata = node.getBlockMetadata();
            IBlockState newState = this.block.getStateFromMeta(metadata);
            if (world.setBlockState(adjustedPos, newState, 3)) {
                // Play placement sound
                SoundType soundtype = this.block.getSoundType(newState, world, adjustedPos, player);
                world.playSound(player, adjustedPos, soundtype.getPlaceSound(), SoundCategory.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
                
                // Notify block
                ((NodeBlock) this.block).onBlockPlacedBy(world, adjustedPos, front, player, newState);
                
                stack.shrink(1);
                node.checkCanStay(true);
                return EnumActionResult.SUCCESS;
            }
        }

        return EnumActionResult.FAIL;
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, IBlockState state) {
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
