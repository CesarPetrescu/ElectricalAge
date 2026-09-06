package mods.eln.simplenode.test;

import mods.eln.node.simple.SimpleNode;
import mods.eln.node.simple.SimpleNodeBlock;
import net.minecraft.block.material.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;

public class TestBlock extends SimpleNodeBlock {

    public TestBlock() {
        super(Material.PACKED_ICE);
    }

    @Override
    public BlockEntity createNewTileEntity(Level var1, int meta) {
        return new TestEntity();
    }

    @Override
    protected SimpleNode newNode() {
        return new TestNode();
    }
}
