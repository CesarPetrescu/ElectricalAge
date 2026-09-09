package mods.eln.misc;
import net.minecraft.nbt.CompoundTag;
public interface INBTTReady { void readFromNBT(CompoundTag nbt,String prefix); void writeToNBT(CompoundTag nbt,String prefix); }
