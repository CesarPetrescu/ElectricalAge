package mods.eln.server

import mods.eln.misc.DimensionIds
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData

/**
 * The legacy per-dimension world storage of the node graph (the fallback when the mod's own
 * `electricalAgeWorld<dim>.dat` files cannot be read). 1.21: a SavedData per server level.
 */
class ElnWorldStorage private constructor(private val dim: Int) : SavedData() {
    override fun save(nbt: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        nbt.putInt("dim", dim)
        ServerEventListener.writeToEaWorldNBT(nbt, dim)
        return nbt
    }

    override fun isDirty(): Boolean {
        return true
    }

    companion object {
        const val key = "eln.worldStorage"

        private fun factory(dim: Int) = Factory({ ElnWorldStorage(dim) }, { nbt, _ ->
            val d = if (nbt.contains("dim")) nbt.getInt("dim") else dim
            ServerEventListener.readFromEaWorldNBT(nbt, d)
            ElnWorldStorage(d)
        }, null)

        @JvmStatic
        fun forWorld(world: Level): ElnWorldStorage? {
            val level = world as? ServerLevel ?: return null
            val dim = DimensionIds.id(level)
            return level.dataStorage.computeIfAbsent(factory(dim), key + dim)
        }
    }
}
