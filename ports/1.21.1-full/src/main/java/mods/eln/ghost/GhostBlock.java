package mods.eln.ghost;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import mods.eln.Eln;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.node.transparent.TransparentNodeEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;

public class GhostBlock extends Block {

    public static final IntegerProperty META = IntegerProperty.create("meta", 0, 15);

    public static final int tCube = 0;
    public static final int tFloor = 1;
    public static final int tLadder = 2;

    public GhostBlock() {
        super(Material.GLASS);
        setDefaultState(blockState.getBaseState().withProperty(META, 0));
    }

    @Override
    protected StateDefinition createBlockState() {
        return new StateDefinition(this, META);
    }

    @Override
    public BlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(META, meta);
    }

    @Override
    public int getMetaFromState(BlockState state) {
        return state.getValue(META);
    }

    @Nullable
    @Override
    public Item getItemDropped(BlockState state, Random rand, int fortune) {
        return null;
    }

    // TODO(1.10): Needs to be done by block states.
//    @Override
//    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn) {
//        int meta = world.getBlockMetadata(x, y, z);
//
//        switch (meta) {
//            case tFloor:
//                AxisAlignedBB axisalignedbb1 = AxisAlignedBB.getBoundingBox((double) x, (double) y, (double) z, (double) x + 1, (double) y + 0.0625, (double) z + 1);
//                if (axisalignedbb1 != null && par5AxisAlignedBB.intersects(axisalignedbb1)) {
//                    list.add(axisalignedbb1);
//                }
//                break;
//            case tLadder:
//
//                break;
//            default:
//                GhostElement element = getElement(world, x, y, z);
//                Coordinate coord = element == null ? null : element.observatorCoordinate;
//                TileEntity te = coord == null ? null : coord.getTileEntity();
//                if (te != null && te instanceof TransparentNodeEntity) {
//                    ((TransparentNodeEntity) te).addCollisionBoxesToList(par5AxisAlignedBB, list, element.elementCoordinate);
//                } else {
//                    super.addCollisionBoxesToList(world, x, y, z, par5AxisAlignedBB, list, entity);
//                }
//                break;
//        }
//    }

//    @Override
//    @SideOnly(Side.CLIENT)
//    public AxisAlignedBB getSelectedBoundingBoxFromPool(World w, int x, int y, int z) {
//        int meta = w.getBlockMetadata(x, y, z);
//
//        switch (meta) {
//            case tFloor:
//                return AxisAlignedBB.getBoundingBox((double) x, (double) y, (double) z, (double) x + 1, (double) y + 0.0625, (double) z + 1);
//            case tLadder:
//                return AxisAlignedBB.getBoundingBox((double) x, (double) y, (double) z, (double) x + 0, (double) y + 0.0, (double) z + 0);
//            default:
//                return super.getSelectedBoundingBoxFromPool(w, x, y, z);
//        }
//    }
//
//    @Override
//    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3d startVec, Vec3d endVec) {
//        int meta = world.getBlockMetadata(x, y, z);
//
//        switch (meta) {
//            case tFloor:
//                this.maxY = 0.0625;
//                break;
//            case tLadder:
//                this.maxX = 0.01;
//                this.maxY = 0.01;
//                this.maxZ = 0.01;
//                break;
//            default:
//                break;
//        }
//
//        MovingObjectPosition m = super.collisionRayTrace(world, x, y, z, startVec, endVec);
//
//        switch (meta) {
//            case tFloor:
//                this.maxY = 1;
//                break;
//            case tLadder:
//                this.maxX = 1;
//                this.maxY = 1;
//                this.maxZ = 1;
//                break;
//            default:
//                break;
//        }
//
//        return m;
//    }
//
//    @Override
//    public boolean isLadder(IBlockAccess world, int x, int y, int z, EntityLivingBase entity) {
//        return world.getBlockMetadata(x, y, z) == tLadder;
//    }


    @Override
    public int getLightOpacity(BlockState state) {
        return 0;
    }

    @Override
    public boolean isTranslucent(BlockState state) {
        return true;
    }

    @Override
    public boolean isOpaqueCube(BlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(BlockState state) {
        return false;
    }

    @Override
    public RenderShape getRenderType(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemStack getPickBlock(BlockState state, RayTraceResult target, Level world, BlockPos pos, Player player) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isTopSolid(BlockState state) {
        return false;
    }

    @Override
    public boolean isSideSolid(BlockState base_state, BlockGetter world, BlockPos pos, net.minecraft.core.Direction side) {
        return false;
    }

    @Override
    public void breakBlock(Level world, BlockPos pos, BlockState state) {
        if (!world.isRemote) {
            GhostElement element = getElement(world, pos);
            if (element != null) {
                element.breakBlock();
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(Level world, BlockPos pos, BlockState state, Player player, InteractionHand hand, net.minecraft.core.Direction facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            GhostElement element = getElement(world, pos);
            if (element != null)
                return element.onBlockActivated(player, Direction.fromFacing(facing), hitX, hitY, hitZ);
        }
        return true;
    }

    private GhostElement getElement(Level world, BlockPos pos) {
        return Eln.ghostManager.getGhost(new Coordinate(pos, world));
    }

    @Override
    public float getBlockHardness(BlockState blockState, Level worldIn, BlockPos pos) {
        return 0.5f;
    }

    public String getNodeUuid() {
        return "g";
    }
}
