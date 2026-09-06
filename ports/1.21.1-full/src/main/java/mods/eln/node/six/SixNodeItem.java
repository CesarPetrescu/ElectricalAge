package mods.eln.node.six;

import mods.eln.generic.GenericItemBlockUsingDamage;
import mods.eln.ghost.GhostGroup;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class SixNodeItem extends GenericItemBlockUsingDamage<SixNodeDescriptor> {

    public SixNodeItem(Block b) {
        super(b);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damageValue) {
        return damageValue;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        Block block = world.getBlockState(pos).getBlock();
        int side = facing.getIndex();
        
        // IMPORTANT: Don't adjust position if clicking on another SixNodeBlock!
        // This allows placing cables on multiple faces of the same block
        if (block != this.block && 
            (block == Blocks.SNOW_LAYER) && ((Utils.getMetaFromPos(world, pos) & 0x7) < 1)) {
            side = 1;
        } else if (block != this.block &&
                   (block != Blocks.VINE) && (block != Blocks.TALLGRASS) && (block != Blocks.DEADBUSH) && (!block.isReplaceable(world, pos))) {
            if (side == 0)
                pos = pos.add(0,-1,0);
            if (side == 1)
                pos = pos.add(0,1,0);
            if (side == 2)
                pos = pos.add(0,0,-1);
            if (side == 3)
                pos = pos.add(0,0,1);
            if (side == 4)
                pos = pos.add(-1,0,0);
            if (side == 5)
                pos = pos.add(1,0,0);
        }
        
        if (stack.isEmpty()) {
            return EnumActionResult.FAIL;
        }
        if (!player.canPlayerEdit(pos, facing, stack)) {
            return EnumActionResult.FAIL;
        }
        if ((pos.getY() == 255) && (this.block.getMaterial(world.getBlockState(pos)).isSolid())) {
            return EnumActionResult.FAIL;
        }

        // Place the block
        int i1 = getMetadata(stack.getItemDamage());
        IBlockState state = this.block.getStateFromMeta(i1);
        
        // Use the actual facing, not the hit vector
        if (placeBlockAt(stack, player, world, pos, facing, hitX, hitY, hitZ, state)) {
            SoundType soundtype = this.block.getSoundType(state, world, pos, player);
            world.playSound(player, new BlockPos(pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F),
                soundtype.getPlaceSound(), SoundCategory.BLOCKS,
                (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
            stack.shrink(1);
            return EnumActionResult.SUCCESS;
        }
        
        return EnumActionResult.FAIL;
    }

    /**
     * Returns true if the given ItemBlock can be placed on the given side of the given block position.
     */

    // func_150936_a <= canPlaceItemBlockOnSide
    @Override
    public boolean canPlaceBlockOnSide(World par1World, BlockPos pos, EnumFacing side, EntityPlayer par6EntityPlayer, ItemStack par7ItemStack) {
        if (!isStackValidToPlace(par7ItemStack))
            return false;

        SixNodeDescriptor descriptor = getDescriptor(par7ItemStack);
        if (descriptor == null)
            return false;

        // Check if descriptor allows placement on this side
        if (!descriptor.canBePlacedOnSide(par6EntityPlayer, new Coordinate(pos, par1World), Direction.fromFacing(side).getInverse()))
            return false;

        // For cables, allow placement on any side (like redstone)
        return true;
    }

    public boolean isStackValidToPlace(ItemStack stack) {
        SixNodeDescriptor descriptor = getDescriptor(stack);
        return descriptor != null;
    }

    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, IBlockState state) {
        if (world.isRemote) return false;
        if (!isStackValidToPlace(stack)) return false;

        Direction direction = Direction.fromFacing(side).getInverse();
        Block blockOld = world.getBlockState(pos).getBlock();
        SixNodeBlock block = (SixNodeBlock) Block.getBlockFromItem(this);

        if (world.isAirBlock(pos) || blockOld.isReplaceable(world, pos)) {
            // Require solid block behind for new placement
            if (!block.getIfOtherBlockIsSolid(world, pos, direction)) {
                return false;
            }
            
            Coordinate coord = new Coordinate(pos, world);
            SixNodeDescriptor descriptor = getDescriptor(stack);

            String error;
            if ((error = descriptor.checkCanPlace(coord, direction, LRDU.Up)) != null) {
                Utils.sendMessage(player, error);
                return false;
            }

            GhostGroup ghostgroup = descriptor.getGhostGroup(direction, LRDU.Up);
            if (ghostgroup != null)
                ghostgroup.plot(coord, coord, descriptor.getGhostGroupUuid());

            SixNode sixNode = new SixNode();
            sixNode.onBlockPlacedBy(new Coordinate(pos, world), direction, player, stack);
            boolean created = sixNode.createSubBlock(stack, direction, player);
            if (!created) return false;

            IBlockState oldState = world.getBlockState(pos);
            boolean blockSet = world.setBlockState(pos, block.getStateFromMeta(block.getMetaFromState(state) & 0x03));
            IBlockState newState = world.getBlockState(pos);

            // Mark TileEntity dirty for save
            TileEntity te = world.getTileEntity(pos);
            if (te != null) {
                te.markDirty();
                // Force immediate client update
                world.notifyBlockUpdate(pos, oldState, newState, 3);
            }

            // Reconnect and notify neighbors
            sixNode.reconnect();
            notifyNeighborsAndUpdate(world, pos, block, sixNode, oldState, newState);

            // Play stone placement sound (like original mod)
            world.playSound(player, pos, SoundType.STONE.getPlaceSound(), SoundCategory.BLOCKS,
                (SoundType.STONE.getVolume() + 1.0F) / 2.0F, SoundType.STONE.getPitch() * 0.8F);

            block.onBlockPlacedBy(world, pos, direction, player, state);
            return true;
            
        } else if (blockOld == block) {
            SixNodeEntity tileEntity = (SixNodeEntity) world.getTileEntity(pos);
            if (tileEntity == null) {
                world.setBlockToAir(pos);
                return false;
            }
            SixNode sixNode = (SixNode) tileEntity.getNode();
            if (sixNode == null) {
                world.setBlockToAir(pos);
                return false;
            }

            // Check if target face is already occupied
            if (sixNode.getSideEnable(direction)) {
                return false;
            }

            // Check for solid block behind
            if (!block.getIfOtherBlockIsSolid(world, pos, direction)) {
                return false;
            }

            boolean created = sixNode.createSubBlock(stack, direction, player);
            if (!created) return false;

            // Mark TileEntity dirty, re-read neighbors, and reconnect
            tileEntity.markDirty();
            sixNode.neighborBlockRead();
            sixNode.reconnect();
            
            IBlockState oldState = world.getBlockState(pos);
            notifyNeighborsAndUpdate(world, pos, block, sixNode, oldState, oldState);

            block.onBlockPlacedBy(world, pos, direction, player, state);
            return true;
        }
        return false;
    }

    /**
     * Helper method to notify neighbors and trigger updates for cable placement/breaking.
     */
    private void notifyNeighborsAndUpdate(World world, BlockPos pos, SixNodeBlock block, SixNode sixNode, IBlockState oldState, IBlockState newState) {
        // Use consolidated 3x3x3 notification for wrappable/corner connections
        Utils.notifyNodeNeighbors(world, pos);

        world.notifyBlockUpdate(pos, oldState, newState, 3);
        sixNode.setNeedPublish(true);
        sixNode.publishToAllPlayer();
    }

    // TODO(1.10): Fix item rendering.
//    @Override
//    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
//        if (getDescriptor(item) == null)
//            return false;
//        return getDescriptor(item).handleRenderType(item, type);
//    }
//
//    @Override
//    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
//        if (!isStackValidToPlace(item))
//            return false;
//        return getDescriptor(item).shouldUseRenderHelper(type, item, helper);
//    }
//
//    public boolean shouldUseRenderHelperEln(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
//        if (!isStackValidToPlace(item))
//            return false;
//        return getDescriptor(item).shouldUseRenderHelperEln(type, item, helper);
//    }
//
//    @Override
//    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
//        if (!isStackValidToPlace(item))
//            return;
//
//        Minecraft.getMinecraft().profiler.startSection("SixNodeItem");
//        if (shouldUseRenderHelperEln(type, item, null)) {
//            switch (type) {
//
//                case ENTITY:
//                    GL11.glRotatef(90, 0, 0, 1);
//                    // GL11.glTranslatef(0, 1, 0);
//                    break;
//
//                case EQUIPPED_FIRST_PERSON:
//                    GL11.glRotatef(160, 0, 1, 0);
//                    GL11.glTranslatef(-0.70f, 1, -0.7f);
//                    GL11.glScalef(1.8f, 1.8f, 1.8f);
//                    GL11.glRotatef(-90, 1, 0, 0);
//                    break;
//                case EQUIPPED:
//                    GL11.glRotatef(180, 0, 1, 0);
//                    GL11.glTranslatef(-0.70f, 1, -0.7f);
//                    GL11.glScalef(1.5f, 1.5f, 1.5f);
//                    break;
//                case FIRST_PERSON_MAP:
//                    // GL11.glTranslatef(0, 1, 0);
//                    break;
//                case INVENTORY:
//                    GL11.glRotatef(-90, 0, 1, 0);
//                    GL11.glRotatef(-90, 1, 0, 0);
//                    break;
//                default:
//                    break;
//            }
//        }
//        // GL11.glTranslatef(0, 1, 0);
//        getDescriptor(item).renderItem(type, item, data);
//        Minecraft.getMinecraft().profiler.endSection();
//    }
}
