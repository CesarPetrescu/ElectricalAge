package mods.eln.transparentnode.autominer;

import mods.eln.misc.McBridge;
import mods.eln.Eln;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.item.ElectricalDrillDescriptor;
import mods.eln.item.MiningPipeDescriptor;
import mods.eln.lightblock.LightBlockEntity;
import mods.eln.misc.Coordinate;
import mods.eln.misc.INBTTReady;
import mods.eln.misc.Utils;
import mods.eln.ore.OreBlock;
import mods.eln.ore.OreColorMapping;
import mods.eln.sim.IProcess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.block.BlockOre;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;

import java.util.ArrayList;

public class AutoMinerSlowProcess implements IProcess, INBTTReady {

    private final AutoMinerElement miner;

    int pipeLength = 0;

    private double energyCounter = 0;
    private double energyTarget = 0;

    private boolean oneJobDone = true;
    boolean silkTouch = false;

    enum jobType {none, done, full, chestFull, ore, pipeAdd, pipeRemove}

    jobType job = jobType.none;
    private jobType oldJob = jobType.none;
    private final Coordinate jobCoord = new Coordinate();
    private int blinkCounter = 0;

    private int drillCount = 1;

    private ArrayList<ItemStack> itemsToDrop = new ArrayList<ItemStack>(4);

    public AutoMinerSlowProcess(AutoMinerElement autoMiner) {
        this.miner = autoMiner;
    }

    void toggleSilkTouch() {
        silkTouch = !silkTouch;
    }

    private boolean isReadyToDrill() {
        ElectricalDrillDescriptor drill = (ElectricalDrillDescriptor) GenericItemUsingDamageDescriptor.getDescriptor(
            miner.getInventory().getItem(AutoMinerContainer.electricalDrillSlotId), ElectricalDrillDescriptor.class);
        if (drill == null) return false;
        return isStorageReady();
    }

    private boolean isStorageReady() {
        Container i = getDropInventory();
        if (i == null) return false;
        for (int idx = 0; idx < i.getContainerSize(); idx++) {
            if (McBridge.isNothing(i.getItem(idx)))
                return true;
        }
        return false;
    }

    @Override
    public void process(double time) {
        ElectricalDrillDescriptor drill = (ElectricalDrillDescriptor) GenericItemUsingDamageDescriptor.getDescriptor(
            miner.getInventory().getItem(AutoMinerContainer.electricalDrillSlotId), ElectricalDrillDescriptor.class);

        if (++blinkCounter >= 9) {
            blinkCounter = 0;
            if ((miner.inPowerLoad.getVoltage() / miner.descriptor.nominalVoltage - 0.5) * 3 > Math.random()) {
                miner.setPowerOk(true);
                LightBlockEntity.addLight(miner.lightCoordinate, 12, 11);
            } else {
                miner.setPowerOk(false);
            }
        }

        energyCounter += miner.powerResistor.getPower() * time;

        if (job != jobType.none && job != jobType.full && job != jobType.chestFull && job != jobType.done) {
            if (energyCounter >= energyTarget || (job == jobType.ore && !isReadyToDrill()) || !miner.powerOk) {
                setupJob();
            }

            if (energyCounter >= energyTarget) {
                oneJobDone = true;
                switch (job) {
                    case ore:
                        drillCount++;

                        Block block = McBridge.getBlock(jobCoord.world(), jobCoord.x, jobCoord.y, jobCoord.z);
                        int meta = McBridge.getBlockMetadata(jobCoord.world(), jobCoord.x, jobCoord.y, jobCoord.z);
                        if (silkTouch) {
                            itemsToDrop.add(new ItemStack(block, 1, meta));
                        } else {
                            NonNullList<ItemStack> drops = NonNullList.create();
                            block.getDrops(drops, jobCoord.world(), jobCoord.getBlockPos(),
                                McBridge.getBlockState(jobCoord.world(), jobCoord.x, jobCoord.y, jobCoord.z), 0);
                            itemsToDrop.addAll(drops);
                        }

                        // Use cobblestone instead of air, everywhere except the mining shaft.
                        // This is so mobs won't spawn excessively.
                        int xDist = jobCoord.x - miner.node.coordinate.x, zDist = jobCoord.z - miner.node.coordinate.z;
                        if (xDist * xDist + zDist * zDist > 25) {
                            McBridge.setBlock(jobCoord.world(), jobCoord.x, jobCoord.y, jobCoord.z, Blocks.COBBLESTONE);
                        } else {
                            McBridge.setBlockToAir(jobCoord.world(), jobCoord.x, jobCoord.y, jobCoord.z);
                        }

                        energyCounter -= energyTarget;
                        setupJob();
                        break;
                    case pipeAdd:
                        // miner.pushLog("Pipe " + (pipeLength + 1) + " added");
                        Eln.ghostManager.createGhost(jobCoord, miner.node.coordinate, jobCoord.y);
                        miner.getInventory().removeItem(AutoMinerContainer.MiningPipeSlotId, 1);

                        pipeLength++;
                        miner.needPublish();

                        energyCounter -= energyTarget;
                        setupJob();
                        break;
                    case pipeRemove:
                        // miner.pushLog("Pipe " + pipeLength + " removed");
                        Eln.ghostManager.removeGhostAndBlock(jobCoord);
                        if (miner.getInventory().getItem(AutoMinerContainer.MiningPipeSlotId) == null) {
                            miner.getInventory().setItem(AutoMinerContainer.MiningPipeSlotId, Eln.miningPipeDescriptor.newItemStack(1));
                            miner.getInventory().setChanged();
                        } else {
                            miner.getInventory().removeItem(AutoMinerContainer.MiningPipeSlotId, -1);
                        }

                        pipeLength--;
                        miner.needPublish();

                        energyCounter -= energyTarget;
                        setupJob();
                        break;
                    default:
                        break;
                }
            }
        } else {
            setupJob();
        }

        switch (job) {
            default:
                miner.powerResistor.highImpedance();
                break;
            case ore:
                if (drill == null) {
                    miner.powerResistor.highImpedance();
                } else {
                    double p = drill.nominalPower;
                    if (silkTouch) p *= 3;
                    miner.powerResistor.setResistance(Math.pow(miner.descriptor.nominalVoltage, 2.0) / p);
                }
                break;
            case pipeAdd:
                miner.powerResistor.setResistance(miner.descriptor.pipeOperationRp);
                break;
            case pipeRemove:
                miner.powerResistor.setResistance(miner.descriptor.pipeOperationRp);
                break;
        }

        if (oldJob != job) {
            miner.needPublish();
        }

        if (oneJobDone || oldJob != job) {
            switch (job) {
                case chestFull:
                    miner.pushLog("* Storage full!");
                    break;
                case done:
                    miner.pushLog("- SLEEP");
                    break;
                case full:
                    miner.pushLog("* Pipe stack full!");
                    break;
                case none:
                    miner.pushLog("* Waiting opcode.");
                    break;
                case ore:
                    miner.pushLog("- DRILL #" + drillCount);
                    break;
                case pipeAdd:
                    miner.pushLog("- ADD PIPE #" + (pipeLength + 1));
                    break;
                case pipeRemove:
                    miner.pushLog("- REMOVE PIPE #" + (pipeLength));
                    break;
                default:
                    break;
            }
        }
        oneJobDone = false;
        oldJob = job;
    }
    private Container getDropInventory() {
        Container inventoryEntity = null;
        Coordinate outputLocation = new Coordinate(1, -1, 0, miner.world());
        outputLocation.applyTransformation(miner.front, miner.coordinate());
        if (outputLocation.getBlockEntity() instanceof Container) {
            inventoryEntity = (Container) outputLocation.getBlockEntity();
            Block inventoryBlock = McBridge.getBlock(miner.world(), outputLocation.x, outputLocation.y, outputLocation.z);
            if(inventoryBlock instanceof ChestBlock) {
                Container possibleDoubleInventoryEntity = ((ChestBlock)inventoryBlock).getLockableContainer(miner.world(), outputLocation.getBlockPos());
                if (possibleDoubleInventoryEntity != null) {
                    inventoryEntity = possibleDoubleInventoryEntity;
                }
            }
        }
        return inventoryEntity;
    }

    private boolean drop(ItemStack stack) {
        return Utils.tryPutStackInInventory(stack, getDropInventory());
    }

    private boolean isMinable(Block block) {
        return block != Blocks.AIR
            && (block) != Blocks.FLOWING_WATER && (block) != Blocks.WATER
            && (block) != Blocks.FLOWING_LAVA && (block) != Blocks.LAVA
            && (block) != Blocks.OBSIDIAN && (block) != Blocks.BEDROCK;
    }

    private void setupJob() {
        ElectricalDrillDescriptor drill = (ElectricalDrillDescriptor) GenericItemUsingDamageDescriptor.getDescriptor(
            miner.getInventory().getItem(AutoMinerContainer.electricalDrillSlotId), ElectricalDrillDescriptor.class);
        // OreScanner scanner = (OreScanner) ElectricalDrillDescriptor.getDescriptor(miner.inventory.getItem(AutoMinerContainer.OreScannerSlotId));
        MiningPipeDescriptor pipe = (MiningPipeDescriptor) GenericItemUsingDamageDescriptor.getDescriptor(
            miner.getInventory().getItem(AutoMinerContainer.MiningPipeSlotId), MiningPipeDescriptor.class);

        int scannerRadius = Eln.config.getIntOrElse("machines.autominer.maxRangeBlocks", 10);
        double scannerEnergy = 0;

        jobCoord.dimension = miner.node.coordinate.dimension;
        jobCoord.x = miner.node.coordinate.x;
        jobCoord.y = miner.node.coordinate.y - pipeLength;
        jobCoord.z = miner.node.coordinate.z;

        // Attempt to drop items. This might not be successful.
        while (itemsToDrop.size() > 0) {
            int index = itemsToDrop.size() - 1;
            if (drop(itemsToDrop.get(index))) {
                itemsToDrop.remove(index);
            }
        }

        boolean jobFind = false;
        if (!miner.node.coordinate.getBlockExist()) {
            setJob(jobType.none);
        } else if (!miner.powerOk) {
            setJob(jobType.none);
        } else if (drill == null) {
            if (jobCoord.y != miner.node.coordinate.y) {
                ItemStack pipeStack = miner.getInventory().getItem(AutoMinerContainer.MiningPipeSlotId);
                if (McBridge.isNothing(pipeStack) || (pipeStack.getCount() != pipeStack.getMaxStackSize() && pipeStack.getCount() != miner.getInventory().getMaxStackSize())) {
                    jobFind = true;
                    setJob(jobType.pipeRemove);
                } else {
                    jobFind = true;
                    setJob(jobType.full);
                }
            }
        } else if (!isStorageReady() || itemsToDrop.size() != 0) {
            setJob(jobType.chestFull);
            jobFind = true;
        } else if (pipe != null) {
            if (jobCoord.y < miner.node.coordinate.y - 2) {
                int depth = (miner.node.coordinate.y - jobCoord.y);
                double miningRay = depth / 10.0 + 0.1;
                miningRay = Math.min(miningRay, 2);
                if (depth < scannerRadius) scannerRadius = depth + 1;
                miningRay = Math.min(miningRay, scannerRadius - 2);
                for (jobCoord.z = miner.node.coordinate.z - scannerRadius; jobCoord.z <= miner.node.coordinate.z + scannerRadius; jobCoord.z++) {
                    for (jobCoord.x = miner.node.coordinate.x - scannerRadius; jobCoord.x <= miner.node.coordinate.x + scannerRadius; jobCoord.x++) {
                        double dx = jobCoord.x - miner.node.coordinate.x;
                        double dy = 0;
                        double dz = jobCoord.z - miner.node.coordinate.z;
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        Block block = McBridge.getBlock(jobCoord.world(), jobCoord.x, jobCoord.y, jobCoord.z);
                        if (checkIsOre(jobCoord) || (distance > 0.1 && distance < miningRay && isMinable(block))) {
                            jobFind = true;
                            setJob(jobType.ore);
                            break;
                        }
                    }
                    if (jobFind) break;
                }
            }

            if (!jobFind) {
                if (jobCoord.y < 3) {
                    jobFind = true;
                    setJob(jobType.done);
                } else {
                    jobCoord.x = miner.node.coordinate.x;
                    jobCoord.y--;
                    jobCoord.z = miner.node.coordinate.z;

                    Block block = McBridge.getBlock(jobCoord.world(), jobCoord.x, jobCoord.y, jobCoord.z);
                    if (block != Blocks.AIR
                        && block != Blocks.FLOWING_WATER && block != Blocks.WATER
                        && block != Blocks.FLOWING_LAVA && block != Blocks.LAVA) {
                        if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) {
                            jobFind = true;
                            setJob(jobType.ore);
                        } else {
                            jobFind = true;
                            setJob(jobType.done);
                        }
                    } else {
                        jobFind = true;
                        setJob(jobType.pipeAdd);
                    }
                }
            }
        }
        if (!jobFind) setJob(jobType.none);

        switch (job) {
            case ore:
                energyTarget = drill.OperationEnergy + scannerEnergy;
                // Copied from Mekanism. Note that the power demand is tripled, so in effect this doubles time.
                if (silkTouch) energyTarget *= 6;
                break;
            case pipeAdd:
                energyTarget = miner.descriptor.pipeOperationEnergy;
                break;
            case pipeRemove:
                energyTarget = miner.descriptor.pipeOperationEnergy;
                break;
            default:
                energyTarget = 0;
                break;
        }
    }

    private void setJob(jobType job) {
        if (job != this.job) {
            miner.needPublish();
            energyCounter = 0;
        }
        this.job = job;
    }

    private boolean checkIsOre(Coordinate coordinate) {
        Block block = McBridge.getBlock(coordinate.world(), coordinate.x, coordinate.y, coordinate.z);
        if (block instanceof BlockOre) return true;
        if (block instanceof OreBlock) return true;
        if (block instanceof RedStoneOreBlock) return true;
        return OreColorMapping.getMap()[Block.getIdFromBlock(block) + (McBridge.getBlockMetadata(coordinate.world(), coordinate.x, coordinate.y, coordinate.z) << 12)] != 0;
    }

    public void onBreakElement() {
        destroyPipe();
    }

    private void destroyPipe() {
        dropPipe();
        Eln.ghostManager.removeGhostAndBlockWithObserverAndNotUuid(miner.node.coordinate, miner.descriptor.getGhostGroupUuid());
        pipeLength = 0;
        miner.needPublish();
    }

    private void dropPipe() {
        Coordinate coord = new Coordinate(miner.node.coordinate);
        for (coord.y = miner.node.coordinate.y - 1; coord.y >= miner.node.coordinate.y - pipeLength; coord.y--) {
            Utils.dropItem(Eln.miningPipeDescriptor.newItemStack(1), coord);
        }
    }

    void ghostDestroyed() {
        destroyPipe();
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {
        pipeLength = nbt.getInt(str + "AMSP" + "pipeLength");
        drillCount = nbt.getInt(str + "AMSP" + "drillCount");
        if (drillCount == 0) drillCount++;
    }

    @Override
    public void writeToNBT(CompoundTag nbt, String str) {
        nbt.putInt(str + "AMSP" + "pipeLength", pipeLength);
        nbt.putInt(str + "AMSP" + "drillCount", drillCount);
    }
}
