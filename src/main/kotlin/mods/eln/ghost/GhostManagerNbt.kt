package mods.eln.ghost

import mods.eln.Eln
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData

/** The world-saved hook that loads the ghost manager (1.7.10's WorldSavedData). */
class GhostManagerNbt : SavedData() {
    override fun isDirty(): Boolean {
        return true
    }

    override fun save(nbt: CompoundTag, registries: HolderLookup.Provider): CompoundTag = nbt

    companion object {
        @JvmField
        val FACTORY = Factory({ GhostManagerNbt() }, { nbt, _ -> GhostManagerNbt().also { Eln.ghostManager.loadFromNBT(nbt) } }, null)
    }
}
