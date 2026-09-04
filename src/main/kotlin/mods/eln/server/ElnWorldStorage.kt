package mods.eln.server

import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraft.world.storage.WorldSavedData

class ElnWorldStorage(str: String?) : WorldSavedData(str) {
    private var dim = 0
    override fun readFromNBT(nbt: NBTTagCompound) {
        dim = nbt.getInteger("dim")
        ServerEventListener.readFromEaWorldNBT(nbt, dim)
    }

    override fun writeToNBT(nbt: NBTTagCompound): NBTTagCompound {
        nbt.setInteger("dim", dim)
        ServerEventListener.writeToEaWorldNBT(nbt, dim)
        return nbt
    }

    override fun isDirty(): Boolean {
        return true
    }

    companion object {
        const val key = "eln.worldStorage"
        @JvmStatic
        fun forWorld(world: World): ElnWorldStorage {
            // Retrieves the MyWorldData instance for the given world, creating it if necessary
            val storage = world.perWorldStorage
            val dim = world.provider.dimension
            var result = storage.getOrLoadData(ElnWorldStorage::class.java, key + dim) as ElnWorldStorage?
            if (result == null) {
                result = storage.getOrLoadData(ElnWorldStorage::class.java, key + dim + "back") as ElnWorldStorage?
            }
            if (result == null) {
                result = ElnWorldStorage(key + dim)
                result.dim = dim
                storage.setData(key + dim, result)
            }
            return result
        }
    }
}
