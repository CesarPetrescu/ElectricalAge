package mods.eln.node.transparent;

import mods.eln.Eln;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeBlock;
import mods.eln.node.NodeBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Random;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class TransparentNodeBlock extends NodeBlock {

    public static final IntegerProperty META = IntegerProperty.create("meta", 0, 15);

    public TransparentNodeBlock(Material material,
                                Class tileEntityClass) {
        super(material, tileEntityClass, 0);
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

	/*@Override
    public TileEntity createNewTileEntity(World world, int meta) {

		if((meta & 0x4) != 0)
			return new TransparentNodeEntityWithSiededInv();
		return super.createNewTileEntity(world, meta);
	}
*/

/*
    //@SideOnly(Side.CLIENT)
    public void getSubBlocks(Items par1, CreativeTabs tab, List subItems) {


        Eln.transparentNodeItem.getSubItems(par1, tab, subItems);
    }
*/

    @Override
    public boolean isOpaqueCube(BlockState state) {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }


    @Override
    public boolean removedByPlayer(BlockState state, Level world, BlockPos pos, Player entityPlayer, boolean willHarvest) {
        if (!world.isRemote) {
            NodeBlockEntity entity = (NodeBlockEntity) world.getTileEntity(pos);
            if (entity != null) {
                NodeBase nodeBase = entity.getNode();
                if (nodeBase instanceof TransparentNode) {
                    TransparentNode t = (TransparentNode) nodeBase;
                    t.removedByPlayer = (ServerPlayer) entityPlayer;
                }
            }
        }

        return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest);
    }

    // TOOD(1.10): Was this important?
//    @Override
//    public int getDamageValue(World world, BlockPos pos) {
//        if (world == null)
//            return 0;
//        TileEntity tile = world.getTileEntity(pos);
//        if (tile != null && tile instanceof TransparentNodeEntity)
//            return ((TransparentNodeEntity) world.getTileEntity(pos)).getDamageValue(world, pos);
//        return 0;
//    }

    @Nullable
    @Override
    public Item getItemDropped(BlockState state, Random rand, int fortune) {
        return null;
    }

    public int quantityDropped(Random par1Random) {
        return 0;
    }

    public void addCollisionBoxesToList(Level world, BlockPos pos, AABB par5AxisAlignedBB, List list, Entity entity) {
        BlockEntity tileEntity = world.getTileEntity(pos);
        if ((!(tileEntity instanceof TransparentNodeEntity))) {
            addCollisionBoxToList(pos, entity.getCollisionBoundingBox(), list, par5AxisAlignedBB);
        } else {
            ((TransparentNodeEntity) tileEntity).addCollisionBoxesToList(par5AxisAlignedBB, list, null);
        }
    }

    @Override
    public BlockEntity createTileEntity(Level var1, BlockState state) {
        try {
            for (EntityMetaTag tag : EntityMetaTag.values()) {
                if (tag.meta == state.getBlock().getMetaFromState(state)) {
                    return (BlockEntity) tag.cls.getConstructor().newInstance();
                }
            }
            // Sadly, this will happen a lot with pre-metatag worlds.
            // Only real fix is to replace the blocks, but there should be no
            // serious downside to getting the wrong subclass so long as they really
            // wanted the superclass.
            System.out.println("Unknown block meta-tag: " + state.getBlock().getMetaFromState(state));
            return (BlockEntity) EntityMetaTag.Basic.cls.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        throw new IllegalStateException("Failed to create tile entity.");
    }

    public String getNodeUuid() {

        return "t";
    }


}
