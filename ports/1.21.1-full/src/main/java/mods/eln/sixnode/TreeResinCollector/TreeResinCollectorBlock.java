package mods.eln.sixnode.TreeResinCollector;

import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.block.material.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class TreeResinCollectorBlock extends BaseEntityBlock {

    public TreeResinCollectorBlock(int id) {
        super(Material.WOOD);
        setRegistryName("TreeResinCollector");
    }

    @Override
    public boolean isOpaqueCube(BlockState state) {
        return false;
    }

    @NotNull
    @Override
    public BlockEntity createNewTileEntity(Level world, int a) {
        return new TreeResinCollectorTileEntity();
    }

//    @Override
//    public int onBlockPlaced(World world, int x, int y, int z, int side, float par6, float par7, float par8, int par9) {
//        //	world.setBlockMetadataWithNotify(x, y, z, side, 0);
//        //	((TreeResinCollectorTileEntity)world.getBlockTileEntity(x, y, z)).setWoodDirection(Direction.fromIntMinecraftSide(side));
//        //return super.onBlockPlaced(world, x, y, z, side, par6, par7, par8,
//        //		par9);
//        return side;
//    }


    @Override
    public boolean onBlockActivated(Level worldIn, BlockPos pos, BlockState state, Player playerIn, InteractionHand hand, Direction facing, float hitX, float hitY, float hitZ) {
        return ((TreeResinCollectorTileEntity) worldIn.getTileEntity(pos)).onBlockActivated();
    }

    @Override
    public void onNeighborChange(BlockGetter world, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(world, pos, neighbor);
        // TODO(1.10): Should implement this. (But it wasn't there in 1.7...)
//        if (!canPlaceBlockOnSide(world, x, y, z, world.getBlockMetadata(x, y, z))) {
//            //Utils.println("WOOOOOOD down");
//            dropBlockAsItem(world, x, y, z, new ItemStack(this));
//            world.setBlockToAir(x, y, z);
//        }
    }
}
