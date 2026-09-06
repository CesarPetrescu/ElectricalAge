package mods.eln.node.simple;

import mods.eln.misc.Coordinate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class SimpleNodeItem extends BlockItem {
    SimpleNodeBlock block;

    public SimpleNodeItem(Block b) {
        super(b);
        block = (SimpleNodeBlock) b;
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, Player player, Level world, BlockPos pos, Direction side, float hitX, float hitY, float hitZ, BlockState newState) {
        SimpleNode node = null;
        if (!world.isRemote) {
            node = block.newNode();
            node.setDescriptorKey(block.descriptorKey);
            node.onBlockPlacedBy(new Coordinate(pos, world), block.getFrontForPlacement(player), player, stack);
        }

        if (!world.setBlockState(pos, newState, 3)) {
            if (node != null) node.onBreakBlock();
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (state.getBlock() == this.block) {
            this.block.onBlockPlacedBy(world, pos, state, player, stack);
        }

        return true;
    }
}
