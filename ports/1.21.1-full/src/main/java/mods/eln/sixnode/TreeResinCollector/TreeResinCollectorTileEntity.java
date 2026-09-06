package mods.eln.sixnode.TreeResinCollector;

import mods.eln.Eln;
import mods.eln.init.Items;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.ITickable;
import net.minecraft.core.BlockPos;

public class TreeResinCollectorTileEntity extends BlockEntity implements ITickable {

    float occupancy = 0f;
    final float occupancyMax = 2f;
    final float occupancyProductPerSecondPerTreeBlock = 1f / 5f / 5f;
    final float timeRandom = 0.2f;

    float timeTarget = (float) (Math.random() * timeRandom);
    float timeCounter = 0;

    boolean onBlockActivated() {
        if (world.isClientSide) return true;
        while (occupancy >= 1f) {
            Utils.dropItem(Items.treeResin.newItemStack(1), new Coordinate( this.pos.getX(), this.pos.getY(), this.pos.getZ(), world));
            occupancy -= 1f;
        }
        return true;
    }

    @Override
    public void update() {
        if (world.isClientSide) return;
        timeCounter += 1f / 20f;
        if (timeCounter > timeTarget) {
            int[] posWood = new int[3];
            int[] posCollector = new int[3];
            Direction woodDirection = Direction.fromIntMinecraftSide(getBlockMetadata()).getInverse();
            posWood = Utils.posToArray(pos);
            //Tried not using the function again
            posCollector = posWood;
            woodDirection.applyTo(posWood, 1);

            int yStart, yEnd;

            net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunkProvider().getLoadedChunk(posWood[0] >> 4, posWood[2] >> 4);
            if (chunk == null || chunk.isEmpty()) return;

            while (chunk.getBlockState(new BlockPos(posWood[0], posWood[1] - 1, posWood[2])).getBlock() == Blocks.LOG) {
                posWood[1]--;
            }
            yStart = posWood[1];

            posWood[1] = pos.getY();
            timeCounter -= timeTarget;
            while (chunk.getBlockState(new BlockPos(posWood[0], posWood[1] + 1, posWood[2])).getBlock() == Blocks.LOG) {
                posWood[1]++;
            }
            yEnd = posWood[1];

            int collectorCount = 0;
            posCollector[1] = yStart;
            for (posCollector[1] = yStart; posCollector[1] <= yEnd; posCollector[1]++) {
                //////	if (world.getBlockId(posCollector[0], posCollector[1] + 1, posCollector[2]) == Eln.treeResinCollectorBlock.blockID)
                {
                    //////		collectiorCount++;
                }
            }

            occupancy += occupancyProductPerSecondPerTreeBlock * (yEnd - yStart + 1) * timeTarget / collectorCount;

            if (occupancy > occupancyMax) occupancy = occupancyMax;

            Utils.println("Occupancy : " + occupancy);
            timeTarget = (float) (Math.random() * timeRandom);
        }
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt) {
        super.writeToNBT(nbt);
        nbt.putFloat("occupancy", occupancy);
        //	woodDirection.writeToNBT(nbt, "woodDirection");
        return nbt;
    }

    @Override
    public void readFromNBT(CompoundTag nbt) {
        super.readFromNBT(nbt);
        occupancy = nbt.getFloat("occupancy");
        //	woodDirection = Direction.readFromNBT(nbt, "woodDirection");
    }
}
