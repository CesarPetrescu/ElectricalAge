package mods.eln.node;

import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import net.minecraft.world.level.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public abstract class NodeBlock extends Block {//BlockContainer
    public int blockItemNbr;
    Class tileEntityClass;

    public NodeBlock(Material material, Class tileEntityClass, int blockItemNbr) {
        super(material);
        setTranslationKey("NodeBlock");
        this.tileEntityClass = tileEntityClass;
        useNeighborBrightness = true;
        this.blockItemNbr = blockItemNbr;
        setHardness(1.0f);
        setResistance(1.0f);
    }

    @Override
    public float getBlockHardness(BlockState blockState, Level worldIn, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public int getWeakPower(BlockState blockState, BlockGetter blockAccess, BlockPos pos, net.minecraft.core.Direction side) {
        NodeBlockEntity entity = (NodeBlockEntity) blockAccess.getTileEntity(pos);
        if (entity == null) return 0;
        return entity.isProvidingWeakPower(Direction.fromFacing(side));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.core.Direction side) {
        NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
        if (entity == null) return false;
        return entity.canConnectRedstone(Direction.fromFacing(side));
    }

    @Override
    public boolean canProvidePower(BlockState state) {

        return super.canProvidePower(state);
    }

    @Override
    public boolean isOpaqueCube(BlockState state) {
        return true;
    }

    //@Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public RenderShape getRenderType(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }


    @Override
    public int getLightValue(BlockState state, BlockGetter world, BlockPos pos) {
        final BlockEntity entity = world.getTileEntity(pos);
        if (entity == null || !(entity instanceof NodeBlockEntity)) return 0;
        NodeBlockEntity tileEntity = (NodeBlockEntity) entity;
        return tileEntity.getLightValue();
    }


    //client server
    public boolean onBlockPlacedBy(Level world, BlockPos pos, Direction front, LivingEntity entityLiving, BlockState state) {

        NodeBlockEntity tileEntity = (NodeBlockEntity) world.getTileEntity(pos);

        tileEntity.onBlockPlacedBy(front, entityLiving, state);
        return true;
    }

    @SideOnly(Side.SERVER)
    public void onBlockAdded(Level par1World, BlockPos pos) {
        if (!par1World.isRemote) {
            NodeBlockEntity entity = (NodeBlockEntity) par1World.getTileEntity(pos);
            entity.onBlockAdded();
        }
    }


    @Override
    public void breakBlock(Level world, BlockPos pos, BlockState state) {
        if (!world.isRemote) {
            NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
            if (entity != null) {
                entity.onBreakBlock();
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void onNeighborChange(BlockGetter world, BlockPos pos, BlockPos neighbor) {
        if (!Utils.isRemote(world)) {
            NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
            entity.onNeighborBlockChange();
        }
    }


    @Override
    public int damageDropped(BlockState state) {
        return getMetaFromState(state);
    }

    //@SideOnly(Side.CLIENT)
    public void getSubBlocks(int par1, CreativeModeTab tab, List subItems) {
        for (int ix = 0; ix < blockItemNbr; ix++) {
            subItems.add(new ItemStack(this, 1, ix));
        }
    }

    //client server
    @Override
    public boolean onBlockActivated(Level world, BlockPos pos, BlockState state, Player entityPlayer, InteractionHand hand, net.minecraft.core.Direction side, float vx, float vy, float vz) {
        NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
//    	entityPlayer.openGui( Eln.instance, 0,world,x ,y, z);
        return entity.onBlockActivated(entityPlayer, Direction.fromFacing(side), vx, vy, vz);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true; // All NodeBlocks have tile entities
    }

    @Override
    public BlockEntity createTileEntity(Level var1, BlockState state) {
        try {
            BlockEntity entity = (BlockEntity) tileEntityClass.getConstructor().newInstance();
            return entity;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}




