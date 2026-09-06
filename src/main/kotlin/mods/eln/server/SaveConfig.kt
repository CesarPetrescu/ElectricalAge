package mods.eln.server

import mods.eln.Eln
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData

class SaveConfig(par1Str: String) : SavedData(par1Str) {
    override fun readFromNBT(nbt: CompoundTag) {
        Eln.wind.readFromNBT(nbt, "wind")
    }

    override fun writeToNBT(nbt: CompoundTag): CompoundTag {
        Eln.wind.writeToNBT(nbt, "wind")
        return nbt
    }

    override fun isDirty(): Boolean {
        return true
    }

    companion object {
        @JvmField
        var instance: SaveConfig? = null
    }

    init {
        instance = this
    }
}
