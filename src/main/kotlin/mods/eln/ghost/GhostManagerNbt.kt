package mods.eln.ghost

import mods.eln.Eln
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import mods.eln.misc.writeToNBT

class GhostManagerNbt(par1Str: String?) : SavedData(par1Str) {
    override fun isDirty(): Boolean {
        return true
    }

    override fun readFromNBT(nbt: CompoundTag) {
        Eln.ghostManager.loadFromNBT(nbt)
    }

    override fun writeToNBT(nbt: CompoundTag): CompoundTag = nbt
}
