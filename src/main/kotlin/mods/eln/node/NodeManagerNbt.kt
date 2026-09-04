package mods.eln.node

import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.storage.WorldSavedData

class NodeManagerNbt(par1Str: String?) : WorldSavedData(par1Str) {
    override fun isDirty(): Boolean {
        return true
    }

    override fun readFromNBT(nbt: NBTTagCompound) {
        NodeManager.instance!!.loadFromNbt(nbt)
    }

    override fun writeToNBT(nbt: NBTTagCompound): NBTTagCompound {
        //NodeManager.instance.saveToNbt(nbt, Integer.MIN_VALUE);
        return nbt
    }
}
