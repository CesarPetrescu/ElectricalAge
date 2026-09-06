package mods.eln.node.simple;

import mods.eln.misc.DescriptorBase;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.block.material.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public abstract class SimpleNodeBlock extends BaseEntityBlock {

    protected SimpleNodeBlock(Material material) {
        super(material);
    }

    String descriptorKey;

    public SimpleNodeBlock setDescriptorKey(String descriptorKey) {
        this.descriptorKey = descriptorKey;
        return this;
    }

    public SimpleNodeBlock setDescriptor(DescriptorBase descriptor) {
        this.descriptorKey = descriptor.descriptorKey;
        return this;
    }


    Direction getFrontForPlacement(LivingEntity e) {
        return Utils.entityLivingViewDirection(e).getInverse();
    }

	/*@Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase e, ItemStack stack) {
		if(w.isRemote == false){
			SimpleNode node = newNode();
			node.setDescriptorKey(descriptorKey);
			node.onBlockPlacedBy(new Coordinate(x,y,z,w), getFrontForPlacement(e), e, stack);
		}
	}*/

    protected abstract SimpleNode newNode();


    SimpleNode getNode(Level world, BlockPos pos) {
        SimpleNodeEntity entity = (SimpleNodeEntity) world.getTileEntity(pos);
        if (entity != null) {
            return entity.getNode();
        }
        return null;
    }

    public SimpleNodeEntity getEntity(Level world, BlockPos pos) {
        SimpleNodeEntity entity = (SimpleNodeEntity) world.getTileEntity(pos);
        return entity;
    }

    @Override
    public boolean removedByPlayer(BlockState state, Level world, BlockPos pos, Player entityPlayer, boolean willHarvest) {
        if (!world.isClientSide) {
            SimpleNode node = getNode(world, pos);
            if (node != null) {
                node.removedByPlayer = (ServerPlayer) entityPlayer;
            }
        }
        return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest);
    }

    // client server
	/*onblockplaced
	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, Direction front, EntityLivingBase entityLiving, int metadata)
	{
		SimpleNodeEntity tileEntity = (SimpleNodeEntity) world.getTileEntity(x, y, z);
		tileEntity.onBlockPlacedBy(front, entityLiving, metadata);
	}*/

    // server
    @Override
    public void onBlockAdded(Level par1World, BlockPos pos, BlockState state) {
        if (!par1World.isClientSide) {
            SimpleNodeEntity entity = (SimpleNodeEntity) par1World.getTileEntity(pos);
            entity.onBlockAdded();
        }
    }

    // server
    @Override
    public void breakBlock(Level par1World, BlockPos pos, BlockState state) {
        SimpleNodeEntity entity = (SimpleNodeEntity) par1World.getTileEntity(pos);
        entity.onBreakBlock();
        super.breakBlock(par1World, pos, state);

    }

    @Override
    public void onNeighborChange(BlockGetter world, BlockPos pos, BlockPos neighbor) {
        if (!Utils.isClientSide(world)) {
            SimpleNodeEntity entity = (SimpleNodeEntity) world.getTileEntity(pos);
            entity.onNeighborBlockChange();
        }
    }

    // client server

    @Override
    public boolean onBlockActivated(Level world, BlockPos pos, BlockState state, Player playerIn, InteractionHand hand, net.minecraft.core.Direction facing, float hitX, float hitY, float hitZ) {
        SimpleNodeEntity entity = (SimpleNodeEntity) world.getTileEntity(pos);
        return entity.onBlockActivated(playerIn, Direction.fromFacing(facing), hitX, hitY, hitZ);
    }

}
