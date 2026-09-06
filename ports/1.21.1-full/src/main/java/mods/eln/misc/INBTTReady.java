package mods.eln.misc;

import net.minecraft.nbt.CompoundTag;

public interface INBTTReady {
    public abstract void readFromNBT(CompoundTag nbt, String str);

    public abstract CompoundTag writeToNBT(CompoundTag nbt, String str);
}
