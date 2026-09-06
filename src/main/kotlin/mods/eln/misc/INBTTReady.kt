package mods.eln.misc

import net.minecraft.nbt.CompoundTag

interface INBTTReady {
    fun readFromNBT(nbt: CompoundTag, str: String)
    fun writeToNBT(nbt: CompoundTag, str: String)
}
