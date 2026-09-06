package mods.eln.node;

import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
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
    public float getBlockHardness(IBlockState blockState, World worldIn, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public int getWeakPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
        NodeBlockEntity entity = (NodeBlockEntity) blockAccess.getTileEntity(pos);
        if (entity == null) return 0;
        return entity.isProvidingWeakPower(Direction.fromFacing(side));
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
        if (entity == null) return false;
        return entity.canConnectRedstone(Direction.fromFacing(side));
    }

    @Override
    public boolean canProvidePower(IBlockState state) {

        return super.canProvidePower(state);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return true;
    }

    //@Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }


    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        final TileEntity entity = world.getTileEntity(pos);
        if (entity == null || !(entity instanceof NodeBlockEntity)) return 0;
        NodeBlockEntity tileEntity = (NodeBlockEntity) entity;
        return tileEntity.getLightValue();
    }


    //client server
    public boolean onBlockPlacedBy(World world, BlockPos pos, Direction front, EntityLivingBase entityLiving, IBlockState state) {

        NodeBlockEntity tileEntity = (NodeBlockEntity) world.getTileEntity(pos);

        tileEntity.onBlockPlacedBy(front, entityLiving, state);
        return true;
    }

    @SideOnly(Side.SERVER)
    public void onBlockAdded(World par1World, BlockPos pos) {
        if (!par1World.isRemote) {
            NodeBlockEntity entity = (NodeBlockEntity) par1World.getTileEntity(pos);
            entity.onBlockAdded();
        }
    }


    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
            if (entity != null) {
                entity.onBreakBlock();
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
        if (!Utils.isRemote(world)) {
            NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
            entity.onNeighborBlockChange();
        }
    }


    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    //@SideOnly(Side.CLIENT)
    public void getSubBlocks(int par1, CreativeTabs tab, List subItems) {
        for (int ix = 0; ix < blockItemNbr; ix++) {
            subItems.add(new ItemStack(this, 1, ix));
        }
    }

    //client server
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityPlayer, EnumHand hand, EnumFacing side, float vx, float vy, float vz) {
        NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
//    	entityPlayer.openGui( Eln.instance, 0,world,x ,y, z);
        return entity.onBlockActivated(entityPlayer, Direction.fromFacing(side), vx, vy, vz);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true; // All NodeBlocks have tile entities
    }

    @Override
    public TileEntity createTileEntity(World var1, IBlockState state) {
        try {
            TileEntity entity = (TileEntity) tileEntityClass.getConstructor().newInstance();
            return entity;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}




