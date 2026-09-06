package mods.eln.node

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData

/** The world-saved hook that loads the node manager (1.7.10's WorldSavedData). */
class NodeManagerNbt : SavedData() {
    override fun isDirty(): Boolean {
        return true
    }

    override fun save(nbt: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        //NodeManager.instance.saveToNbt(nbt, Integer.MIN_VALUE);
        return nbt
    }

    companion object {
        @JvmField
        val FACTORY = Factory({ NodeManagerNbt() }, { nbt, _ -> NodeManagerNbt().also { NodeManager.instance!!.loadFromNbt(nbt) } }, null)
    }
}
