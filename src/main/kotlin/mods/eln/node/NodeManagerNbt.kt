package mods.eln.node

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import mods.eln.misc.writeToNBT

class NodeManagerNbt(par1Str: String?) : SavedData(par1Str) {
    override fun isDirty(): Boolean {
        return true
    }

    override fun readFromNBT(nbt: CompoundTag) {
        NodeManager.instance!!.loadFromNbt(nbt)
    }

    override fun writeToNBT(nbt: CompoundTag): CompoundTag {
        //NodeManager.instance.saveToNbt(nbt, Integer.MIN_VALUE);
        return nbt
    }
}
