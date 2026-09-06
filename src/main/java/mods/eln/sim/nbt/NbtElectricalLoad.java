package mods.eln.sim.nbt;

import mods.eln.misc.INBTTReady;
import mods.eln.sim.ElectricalLoad;
import net.minecraft.nbt.CompoundTag;

public class NbtElectricalLoad extends ElectricalLoad implements INBTTReady {

    String name;

    public NbtElectricalLoad(String name) {
        super();
        this.name = name;
    }

    public void readFromNBT(CompoundTag nbttagcompound, String str) {
        setVoltage(nbttagcompound.getFloat(str + name + "Uc"));
        if (Double.isNaN(getVoltage())) setVoltage(0);
        if (getVoltage() == Float.NEGATIVE_INFINITY) setVoltage(0);
        if (getVoltage() == Float.POSITIVE_INFINITY) setVoltage(0);
    }

    public void writeToNBT(CompoundTag nbttagcompound, String str) {
        nbttagcompound.putFloat(str + name + "Uc", (float) getVoltage());
    }
}
