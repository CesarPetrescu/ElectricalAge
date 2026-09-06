package mods.eln.server;

import mods.eln.Eln;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class SaveConfig extends SavedData {

    public static SaveConfig instance;

    public boolean heatFurnaceFuel = true;
    public boolean electricalLampAging = true;
    public boolean batteryAging = true;
    public boolean infinitePortableBattery = false;
    public boolean reGenOre = false;
    public double cableRsFactor_lastUsed = 1.0;

    public SaveConfig(String par1Str) {
        super(par1Str);
        instance = this;
    }

    @Override
    public void readFromNBT(CompoundTag nbt) {
        heatFurnaceFuel = nbt.getBoolean("heatFurnaceFuel");
        electricalLampAging = nbt.getBoolean("electricalLampAging");
        batteryAging = nbt.getBoolean("batteryAging");
        infinitePortableBattery = nbt.getBoolean("infinitPortableBattery");
        reGenOre = nbt.getBoolean("reGenOre");
        cableRsFactor_lastUsed = nbt.getDouble("cableRsFactor_lastUsed");
        Eln.windProcess.readFromNBT(nbt, "wind");
    }

    @Override
        public CompoundTag writeToNBT(CompoundTag nbt) {
        nbt.putBoolean("heatFurnaceFuel", heatFurnaceFuel);
        nbt.putBoolean("electricalLampAging", electricalLampAging);
        nbt.putBoolean("batteryAging", batteryAging);
        nbt.putBoolean("infinitPortableBattery", infinitePortableBattery);
        nbt.putBoolean("reGenOre", reGenOre);
        Eln.windProcess.writeToNBT(nbt, "wind");
        return nbt;
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
