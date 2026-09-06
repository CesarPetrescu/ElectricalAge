package mods.eln.misc;

import net.minecraft.nbt.CompoundTag;

public interface INBTTReady2 {
    public abstract void readFromNBT(CompoundTag nbt);

    public abstract void writeToNBT(CompoundTag nbt);
}
