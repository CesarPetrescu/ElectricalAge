package mods.eln.sim.nbt;

import mods.eln.misc.INBTTReady;
import mods.eln.sim.FurnaceProcess;
import mods.eln.sim.ThermalLoad;
import net.minecraft.nbt.CompoundTag;

public class NbtFurnaceProcess extends FurnaceProcess implements INBTTReady {

    String name;

    public NbtFurnaceProcess(String name, ThermalLoad load) {
        super(load);
        this.name = name;
    }

    @Override
    public void readFromNBT(CompoundTag nbttagcompound, String str) {
        combustibleEnergy = nbttagcompound.getFloat(str + name + "Q");
        setGain(nbttagcompound.getDouble(str + name + "gain"));
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbttagcompound, String str) {
        nbttagcompound.putFloat(str + name + "Q", (float) combustibleEnergy);
        nbttagcompound.putDouble(str + name + "gain", getGain());
        return nbttagcompound;
    }
}
