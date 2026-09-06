package mods.eln.misc

import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.neoforged.neoforge.server.ServerLifecycleHooks

/**
 * Dimensions are [ResourceKey]s since 1.16, but Electrical Age indexes its node graph, its packets
 * and its saved data by the 1.7.10 integer dimension id ([Coordinate.dimension], 130+ call sites).
 * This keeps the ints: the vanilla three get their historical ids, every other dimension gets a
 * stable id from the sorted key list. Both sides see the same key set once a player is logged in,
 * so the ids agree in packets; saved coordinates also carry the key's name (see [Coordinate]).
 */
object DimensionIds {
    private val toId = HashMap<ResourceKey<Level>, Int>()
    private val toKey = HashMap<Int, ResourceKey<Level>>()

    init {
        put(Level.OVERWORLD, 0)
        put(Level.NETHER, -1)
        put(Level.END, 1)
    }

    private fun put(key: ResourceKey<Level>, id: Int) {
        toId[key] = id
        toKey[id] = key
    }

    @JvmStatic
    @Synchronized
    fun id(key: ResourceKey<Level>): Int {
        toId[key]?.let { return it }
        // Non-vanilla dimension: allocate the next free id above the vanilla ones, deterministic by name.
        var id = 2
        while (toKey.containsKey(id)) id++
        put(key, id)
        return id
    }

    @JvmStatic
    @Synchronized
    fun key(id: Int): ResourceKey<Level>? = toKey[id]

    @JvmStatic
    fun id(level: Level): Int = id(level.dimension())

    /** The server world for an id, or null when there is no server or no such dimension. */
    @JvmStatic
    fun serverLevel(id: Int): ServerLevel? {
        val key = key(id) ?: return null
        return ServerLifecycleHooks.getCurrentServer()?.getLevel(key)
    }

    /** Called when a server starts so the datapack dimensions get their ids in a fixed order. */
    @JvmStatic
    @Synchronized
    fun learn(keys: Iterable<ResourceKey<Level>>) {
        keys.sortedBy { it.location().toString() }.forEach { id(it) }
    }
}
