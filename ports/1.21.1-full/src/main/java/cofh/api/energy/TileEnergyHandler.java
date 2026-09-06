package cofh.api.energy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

/**
 * Reference implementation of {@link IEnergyReceiver} and {@link IEnergyProvider}. Use/extend this or implement your own.
 *
 * This class is really meant to summarize how each interface is properly used.
 *
 * @author King Lemming
 */
public class TileEnergyHandler extends BlockEntity implements IEnergyReceiver, IEnergyProvider {

	protected EnergyStorage storage = new EnergyStorage(32000);

	@Override
	public void readFromNBT(CompoundTag nbt) {

		super.readFromNBT(nbt);
		storage.readFromNBT(nbt);
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag nbt) {

		super.writeToNBT(nbt);
		storage.writeToNBT(nbt);
		return nbt;
	}

	/* IEnergyConnection */
	@Override
	public boolean canConnectEnergy(Direction from) {

		return true;
	}

	/* IEnergyReceiver */
	@Override
	public int receiveEnergy(Direction from, int maxReceive, boolean simulate) {

		return storage.receiveEnergy(maxReceive, simulate);
	}

	/* IEnergyProvider */
	@Override
	public int extractEnergy(Direction from, int maxExtract, boolean simulate) {

		return storage.extractEnergy(maxExtract, simulate);
	}

	/* IEnergyHandler */
	@Override
	public int getEnergyStored(Direction from) {

		return storage.getEnergyStored();
	}

	@Override
	public int getMaxEnergyStored(Direction from) {

		return storage.getMaxEnergyStored();
	}

}
