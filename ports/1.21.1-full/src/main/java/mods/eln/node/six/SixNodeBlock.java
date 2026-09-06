package mods.eln.node.six;

import mods.eln.Eln;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

public class SixNodeBlock extends NodeBlock {
    // public static ArrayList<Integer> repertoriedItemStackId = new ArrayList<Integer>();

    // private IIcon icon;
    public SixNodeBlock(Material material, Class tileEntityClass) {
        super(material, tileEntityClass, 0);

        // setBlockTextureName("eln:air");
    }

    @Override
    public ItemStack getPickBlock(BlockState state, RayTraceResult target, Level world, BlockPos pos, Player player) {
        SixNodeEntity entity = (SixNodeEntity) world.getTileEntity(pos);
        if (entity != null) {
            SixNodeElementRender render = entity.elementRenderList[Direction.fromFacing(target.sideHit).getInt()];
            if (render != null) {
                return render.sixNodeDescriptor.newItemStack();
            }
        }
        return super.getPickBlock(state, target, world, pos, player);
    }

    // TODO(1.10): Fix item render.
//    @Override
//    public void registerBlockIcons(IIconRegister r) {
//        super.registerBlockIcons(r);
//        this.blockIcon = r.registerIcon("eln:air");
//    }

    public AABB getCollisionBoundingBoxFromPool(Level par1World, BlockPos pos) {
        if (nodeHasCache(par1World, pos) || hasVolume(par1World, pos))
            return super.getCollisionBoundingBox(par1World.getBlockState(pos), par1World, pos);
        else
            return null;
    }


    public boolean hasVolume(BlockGetter world, BlockPos pos) {
        SixNodeEntity entity = getEntity(world, pos);
        if (entity == null) return false;
        return entity.hasVolume((Level) world, pos.getX(), pos.getY(), pos.getZ());

    }

    @Override
    public float getBlockHardness(BlockState blockState, Level worldIn, BlockPos pos)  {
        return 0.3f;
    }

    //@Override
    public int getDamageValue(Level world, BlockPos pos) {
        if (world == null)
            return 0;
        SixNodeEntity entity = getEntity(world, pos);
        return entity == null ? 0 : entity.getDamageValue(world, pos.getX(), pos.getY(), pos.getZ());
    }

    SixNodeEntity getEntity(BlockGetter world, BlockPos pos) {
        BlockEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity != null && tileEntity instanceof SixNodeEntity)
            return (SixNodeEntity) tileEntity;
        return null;

    }

    // TODO(1.12) Whatever this was, it's broken now.
    // @SideOnly(Side.CLIENT)
//    public void getSubBlocks(Items par1, CreativeTabs tab, List subItems) {
//        Eln.sixNodeItem.getSubItems(par1, tab, subItems);
//    }

    @Override
    public boolean isOpaqueCube(BlockState state) {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public RenderShape getRenderType(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
    
    @Override
    public boolean isFullCube(BlockState state) {
        return false;
    }
    
    @Override
    public boolean isFullBlock(BlockState state) {
        return false;
    }

	/*
	 * @Override public int getLightOpacity(World world, int x, int y, int z) {
	 * 
	 * return 255; }
	 */

    @Override
    public Item getItemDropped(BlockState state, Random rand, int fortune) {

        return null;
    }

    public int quantityDropped(Random par1Random) {
        return 0;
    }

    // TODO(1.10): Fix item rendering.
//    @Override
//    @SideOnly(Side.CLIENT)
//    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int side) {
//        TileEntity e = w.getTileEntity(x, y, z);
//        if (e == null) return blockIcon;
//        SixNodeEntity sne = (SixNodeEntity) e;
//        Block b = sne.sixNodeCacheBlock;
//        if (b == ModBlock.air) return blockIcon;
//        // return b.getIcon(w, x, y, z, side);
//        try {
//            return b.getIcon(side, sne.sixNodeCacheBlockMeta);
//        } catch (Exception e2) {
//            return blockIcon;
//        }
//
//        // return ModBlock.sand.getIcon(p_149673_1_, p_149673_2_, p_149673_3_, p_149673_4_, p_149673_5_);
//        // return ModBlock.stone.getIcon(w, x, y, z, side);
//    }

    @Override
    public boolean isReplaceable(BlockGetter world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canPlaceBlockOnSide(Level par1World, BlockPos pos, net.minecraft.core.Direction facing) {
		/* see canPlaceBlockAt; it needs changing if this method is fixed */
        return true;/*
					 * if(par1World.isRemote) return true; SixNodeEntity tileEntity = (SixNodeEntity) par1World.getBlockTileEntity(par2, par3, par4); if(tileEntity == null || (tileEntity instanceof SixNodeEntity) == false) return true; Direction direction = Direction.fromIntMinecraftSide(par5); SixNode node = (SixNode) tileEntity.getNode(); if(node == null) return true; if(node.getSideEnable(direction))return false;
					 * 
					 * return true;
					 */
    }

    @Override
    public boolean canPlaceBlockAt(Level par1World, BlockPos pos) {
		/* This should probably call canPlaceBlockOnSide with each
		 * appropriate side to see if it can go somewhere.
		 * (cf. BlockLever, BlockTorch, etc)

		 * Currently, canPlaceBlockOnSide returns true and defers
		 * check to other code.  The rest of the sixnode code isn't
		 * expecting blind canPlaceBlockAt to work, so things that
		 * call it (e.g. Rannuncarpus) confuse it terribly and leak
		 * cables and nodepieces.

		 * So for now, make the Rannuncarpus et al ignore it.
		 */
		return false;
    }

    @Override
    public void onBlockPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity entityLiving, ItemStack stack) {

    }

    @Override
    public boolean onBlockActivated(Level world, BlockPos pos, BlockState state, Player entityPlayer, InteractionHand hand, net.minecraft.core.Direction side, float vx, float vy, float vz) {
        if (hand != InteractionHand.MAIN_HAND) {
            return false;
        }
        if (world.isRemote) {
            return true; // Let server handle it
        }

        SixNodeEntity tileEntity = (SixNodeEntity) world.getTileEntity(pos);
        if (tileEntity == null) {
            return false;
        }

        return tileEntity.onBlockActivated(entityPlayer, Direction.fromFacing(side), vx, vy, vz);
    }

    @Override
    public boolean removedByPlayer(BlockState state, Level world, BlockPos pos, Player entityPlayer, boolean willHarvest) {
        
        if (world.isRemote) {
            return false;
        }

        SixNodeEntity tileEntity = (SixNodeEntity) world.getTileEntity(pos);
        if (tileEntity == null) {
            return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest);
        }

        SixNode sixNode = (SixNode) tileEntity.getNode();
        if (sixNode == null) {
            return true;
        }
        

        // Get the hit face from raytrace
        RayTraceResult raytrace = collisionRayTrace(world, pos, entityPlayer);
        Direction hitDirection;
        
        if (raytrace != null) {
            hitDirection = Direction.fromIntMinecraftSide(raytrace.sideHit.getIndex());
        } else {
            // Fallback: find any enabled side
            hitDirection = null;
            for (Direction dir : Direction.values()) {
                if (sixNode.getSideEnable(dir)) {
                    hitDirection = dir;
                    break;
                }
            }
            if (hitDirection == null) {
                return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest);
            }
        }

        // If there's a cached block on top, break that first
        if (sixNode.sixNodeCacheBlock != Blocks.AIR) {
            if (!(Utils.isCreative((ServerPlayer) entityPlayer))) {
                ItemStack stack = new ItemStack(sixNode.sixNodeCacheBlock, 1, sixNode.sixNodeCacheBlockMeta);
                sixNode.dropItem(stack);
            }
            sixNode.sixNodeCacheBlock = Blocks.AIR;
            LevelChunk chunk = world.getChunk(pos);
            Utils.generateHeightMap(chunk);
            sixNode.setNeedPublish(true);
            return false;
        }

        // Break the cable on the hit face
        if (!sixNode.playerAskToBreakSubBlock((ServerPlayer) entityPlayer, hitDirection)) {
            return false;
        }

        // Reconnect and notify neighbors of changes
        sixNode.reconnect();
        notifyNeighborsAndUpdate(world, pos, sixNode, state);

        // If sides remain, keep the block
        if (sixNode.getIfSideRemain()) {
            return true;
        }

        return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest);
    }

    /**
     * Helper method to notify neighbors and trigger updates when breaking cables.
     */
    private void notifyNeighborsAndUpdate(Level world, BlockPos pos, SixNode sixNode, BlockState state) {
        // Use consolidated 3x3x3 notification for wrappable/corner connections
        Utils.notifyNodeNeighbors(world, pos);

        SixNodeEntity tileEntity = (SixNodeEntity) world.getTileEntity(pos);
        if (tileEntity != null) tileEntity.markDirty();

        world.notifyBlockUpdate(pos, state, state, 3);
        sixNode.setNeedPublish(true);
        sixNode.publishToAllPlayer();
    }

    @Override
    public void breakBlock(Level world, BlockPos pos, BlockState state) {

        if (!world.isRemote) {
            SixNodeEntity tileEntity = (SixNodeEntity) world.getTileEntity(pos);
            if (tileEntity != null) {
                SixNode sixNode = (SixNode) tileEntity.getNode();
                if (sixNode != null) {
                    // Disconnect once before removing all sides
                    sixNode.disconnect();

                    // Remove all sides without individual disconnect/connect calls
                    for (Direction direction : Direction.values()) {
                        if (sixNode.getSideEnable(direction)) {
                            sixNode.sideElementList[direction.getInt()] = null;
                            sixNode.sideElementIdList[direction.getInt()] = 0;
                        }
                    }

                    // Notify neighboring blocks (3x3x3) to update their connections
                    Utils.notifyNodeNeighbors(world, pos);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (worldIn.isRemote) return;

        SixNodeEntity tileEntity = (SixNodeEntity) worldIn.getTileEntity(pos);
        if (tileEntity == null) return;

        SixNode sixNode = (SixNode) tileEntity.getNode();
        if (sixNode == null) return;

        boolean changed = false;
        for (Direction direction : Direction.values()) {
            if (sixNode.getSideEnable(direction)) {
                if (!getIfOtherBlockIsSolid(worldIn, pos, direction)) {
                    sixNode.deleteSubBlock(null, direction);
                    changed = true;
                }
            }
        }

        if (!sixNode.getIfSideRemain()) {
            worldIn.setBlockToAir(pos);
        } else if (changed) {
            sixNode.reconnect();
            notifyNeighborsAndUpdate(worldIn, pos, sixNode, worldIn.getBlockState(pos));
        }
    }

    @Override
    public void onNeighborChange(BlockGetter world, BlockPos pos, BlockPos neighbor) {
        // Also handle TileEntity neighbor changes
        if (((Level) world).isRemote) return;

        SixNodeEntity tileEntity = (SixNodeEntity) world.getTileEntity(pos);
        if (tileEntity == null) return;

        SixNode sixNode = (SixNode) tileEntity.getNode();
        if (sixNode == null) return;

        boolean changed = false;
        for (Direction direction : Direction.values()) {
            if (sixNode.getSideEnable(direction)) {
                if (!getIfOtherBlockIsSolid(world, pos, direction)) {
                    sixNode.deleteSubBlock(null, direction);
                    changed = true;
                }
            }
        }

        if (!sixNode.getIfSideRemain()) {
            ((Level) world).setBlockToAir(pos);
        } else {
            // Trigger reconnection and notify neighbors if changed
            if (changed) {
                sixNode.reconnect();
                notifyNeighborsAndUpdate((Level) world, pos, sixNode, world.getBlockState(pos));
            }
            super.onNeighborChange(world, pos, neighbor);
        }
    }

    // Thickness of the thin slab for each cable face
    private static final double SLAB_THICKNESS = 0.2;

    // AABBs for each face's thin slab (relative to block pos)
    private static final AABB[] FACE_AABBS = {
        new AABB(0, 0, 0, SLAB_THICKNESS, 1, 1),           // XN (0)
        new AABB(1 - SLAB_THICKNESS, 0, 0, 1, 1, 1),       // XP (1)
        new AABB(0, 0, 0, 1, SLAB_THICKNESS, 1),           // YN (2)
        new AABB(0, 1 - SLAB_THICKNESS, 0, 1, 1, 1),       // YP (3)
        new AABB(0, 0, 0, 1, 1, SLAB_THICKNESS),           // ZN (4)
        new AABB(0, 0, 1 - SLAB_THICKNESS, 1, 1, 1),       // ZP (5)
    };

    private static final Direction[] FACE_DIRECTIONS = {
        Direction.XN, Direction.XP, Direction.YN, Direction.YP, Direction.ZN, Direction.ZP
    };

    // Cached last-hit AABB from collisionRayTrace, used by getSelectedBoundingBox (Mekanism pattern)
    private AABB lastHitBounds = FACE_AABBS[2]; // default to YN

    private boolean[] getSideEnabled(Level world, BlockPos pos) {
        boolean[] sides = new boolean[6];
        BlockEntity te = world.getTileEntity(pos);
        if (!(te instanceof SixNodeEntity)) return sides;
        SixNodeEntity tileEntity = (SixNodeEntity) te;

        if (world.isRemote) {
            for (int i = 0; i < 6; i++) {
                sides[i] = tileEntity.getSyncronizedSideEnable(Direction.fromInt(i));
            }
        } else {
            SixNode sixNode = (SixNode) tileEntity.getNode();
            if (sixNode == null) return sides;
            for (int i = 0; i < 6; i++) {
                sides[i] = sixNode.getSideEnable(Direction.fromInt(i));
            }
        }
        return sides;
    }

    @Nullable
    @Override
    public RayTraceResult collisionRayTrace(BlockState blockState, Level world, BlockPos pos, Vec3 start, Vec3 end) {
        if (nodeHasCache(world, pos)) {
            return super.collisionRayTrace(blockState, world, pos, start, end);
        }

        boolean[] sides = getSideEnabled(world, pos);

        RayTraceResult closest = null;
        double closestDist = Double.MAX_VALUE;
        AABB hitBounds = null;

        for (int i = 0; i < 6; i++) {
            if (!sides[i]) continue;

            AABB aabb = FACE_AABBS[i].offset(pos);
            RayTraceResult hit = aabb.calculateIntercept(start, end);
            if (hit != null) {
                double dist = hit.hitVec.squareDistanceTo(start);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = new RayTraceResult(hit.hitVec, FACE_DIRECTIONS[i].toForge(), pos);
                    hitBounds = FACE_AABBS[i];
                }
            }
        }

        // Cache the hit AABB for getSelectedBoundingBox (Mekanism pattern)
        if (hitBounds != null) {
            lastHitBounds = hitBounds;
        }

        return closest;
    }

    private RayTraceResult collisionRayTrace(Level world, BlockPos pos, Player player) {
        double distanceMax = 5.0;
        Vec3 start = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLook(1.0f);
        Vec3 end = start.add(look.x * distanceMax, look.y * distanceMax, look.z * distanceMax);
        return collisionRayTrace(world.getBlockState(pos), world, pos, start, end);
    }

    boolean getIfOtherBlockIsSolid(BlockGetter world, BlockPos pos, Direction direction) {
        pos = direction.applied(pos, 1);

        BlockState state = world.getBlockState(pos);
        if (state.getBlock().isAir(state, world, pos)) return false;
        return state.isOpaqueCube();
    }

    private boolean nodeHasCache(BlockGetter world, BlockPos pos) {
        if (Utils.isRemote(world)) {
            BlockEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity != null && tileEntity instanceof SixNodeEntity)
                return ((SixNodeEntity) tileEntity).sixNodeCacheBlock != Blocks.AIR;

        } else {
            SixNodeEntity tileEntity = (SixNodeEntity) world.getTileEntity(pos);
            SixNode sixNode = (SixNode) tileEntity.getNode();
            if (sixNode != null)
                return sixNode.sixNodeCacheBlock != Blocks.AIR;
        }
        return false;
    }

    // Get selection bounding box (highlight box when looking at cable)
    // Uses cached lastHitBounds from collisionRayTrace (Mekanism pattern)
    @Override
    public AABB getSelectedBoundingBox(BlockState state, Level worldIn, BlockPos pos) {
        if (hasVolume(worldIn, pos)) return super.getSelectedBoundingBox(state, worldIn, pos);
        return lastHitBounds.offset(pos);
    }

    // No collision - cables are like redstone wire, no physical collision
    @Override
    public void addCollisionBoxToList(BlockState state, Level worldIn, BlockPos pos, AABB entityBox, List<AABB> collidingBoxes, @Nullable Entity collidingEntity, boolean p_185477_7_) {
        // Cables have no collision boxes - entities pass through them like redstone wire
    }

    // TODO(1.10): This has to be done with block-states now.
//    @Override
//    public int getLightOpacity(IBlockAccess w, int x, int y, int z) {
//
//        TileEntity e = w.getTileEntity(x, y, z);
//        if (e == null) return 0;
//        SixNodeEntity sne = (SixNodeEntity) e;
//        Block b = sne.sixNodeCacheBlock;
//        if (b == ModBlock.air) return 0;
//        // return b.getIcon(w, x, y, z, side);
//        try {
//            return b.getLightOpacity();
//        } catch (Exception e2) {
//            return 255;
//        }
//    }

    public String getNodeUuid() {
        return "s";
    }

    // TODO(1.10): Should probably be done by block states.
//    @Override
//    @SideOnly(Side.CLIENT)
//    public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World world, BlockPos pos) {
//        if (hasVolume(w, x, y, z)) return super.getSelectedBoundingBoxFromPool(w, x, y, z);
//        MovingObjectPosition col = collisionRayTrace(w, x, y, z, Minecraft.getMinecraft().player);
//        double h = 0.2;
//        double hn = 1 - h;
//
//        double b = 0.02;
//        double bn = 1 - 0.02;
//        if (col != null) {
//            // Utils.println(Direction.fromIntMinecraftSide(col.sideHit));
//            switch (Direction.fromIntMinecraftSide(col.sideHit)) {
//                case XN:
//                    return AxisAlignedBB.getBoundingBox((double) x + b, (double) y, (double) z, (double) x + h, (double) y + 1, (double) z + 1);
//                case XP:
//                    return AxisAlignedBB.getBoundingBox((double) x + hn, (double) y, (double) z, (double) x + bn, (double) y + 1, (double) z + 1);
//                case YN:
//                    return AxisAlignedBB.getBoundingBox((double) x, (double) y + b, (double) z, (double) x + 1, (double) y + h, (double) z + 1);
//                case YP:
//                    return AxisAlignedBB.getBoundingBox((double) x, (double) y + hn, (double) z, (double) x + 1, (double) y + bn, (double) z + 1);
//                case ZN:
//                    return AxisAlignedBB.getBoundingBox((double) x, (double) y, (double) z + b, (double) x + 1, (double) y + 1, (double) z + h);
//                case ZP:
//                    return AxisAlignedBB.getBoundingBox((double) x, (double) y, (double) z + hn, (double) x + 1, (double) y + 1, (double) z + bn);
//
//            }
//        }
//        return AxisAlignedBB.getBoundingBox(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);//super.getSelectedBoundingBoxFromPool(w, x, y, z);
//        // return AxisAlignedBB.getBoundingBox((double)p_149633_2_ , (double)p_149633_3_ , (double)p_149633_4_ + this.minZ+0.2, (double)p_149633_2_ + this.maxX, (double)p_149633_3_ + this.maxY, (double)p_149633_4_ + this.maxZ);
//        // return super.getSelectedBoundingBoxFromPool(w, x, y, z);
//    }
}
