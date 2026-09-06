package mods.eln.server

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData

class ElnWorldStorage(str: String?) : SavedData(str) {
    private var dim = 0
    override fun readFromNBT(nbt: CompoundTag) {
        dim = nbt.getInt("dim")
        ServerEventListener.readFromEaWorldNBT(nbt, dim)
    }

    override fun writeToNBT(nbt: CompoundTag): CompoundTag {
        nbt.putInt("dim", dim)
        ServerEventListener.writeToEaWorldNBT(nbt, dim)
        return nbt
    }

    override fun isDirty(): Boolean {
        return true
    }

    companion object {
        const val key = "eln.worldStorage"
        @JvmStatic
        fun forWorld(world: Level): ElnWorldStorage {
            // Retrieves the MyWorldData instance for the given world, creating it if necessary
            val storage = world.perWorldStorage
            val dim = world.dimension()
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
