package mods.eln.server

import mods.eln.Eln
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData

/** The mod's per-world settings (the wind state), as world-saved data. */
class SaveConfig : SavedData() {
    override fun save(nbt: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        Eln.wind.writeToNBT(nbt, "wind")
        return nbt
    }

    override fun isDirty(): Boolean {
        return true
    }

    companion object {
        @JvmField
        var instance: SaveConfig? = null

        @JvmField
        val FACTORY = Factory({ SaveConfig() }, { nbt, _ -> SaveConfig().also { Eln.wind.readFromNBT(nbt, "wind") } }, null)
    }

    init {
        instance = this
    }
}
