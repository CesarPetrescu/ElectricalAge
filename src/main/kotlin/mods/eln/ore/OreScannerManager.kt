package mods.eln.ore

import mods.eln.config.OreScannerConfigLoader
import mods.eln.item.electricalitem.OreScannerConfigElement

/**
 * Centralized manager for ore scanner state and logic.
 * Owns the ore scanner config list, color mapping cache, and factor regeneration.
 */
object OreScannerManager {

    @JvmField
    val oreScannerConfig: MutableList<OreScannerConfigElement> = ArrayList()

    /**
     * Regenerates ore scanner factors from config and auto-discovery,
     * then rebuilds the color mapping cache.
     */
    @JvmStatic
    fun regenOreScannerFactors() {
        oreScannerConfig.clear()

        // Load from config (block references + OreDictionary names)
        oreScannerConfig.addAll(OreScannerConfigLoader.loadOreScannerConfig())

        // Auto-discover from OreDictionary (if enabled) — only adds entries not already in config
        val existingKeys = LinkedHashMap<Int, Float>()
        for (e in oreScannerConfig) {
            existingKeys[e.blockKey] = e.factor
        }
        oreScannerConfig.addAll(OreScannerConfigLoader.loadOreDictionaryAutoDiscovery(existingKeys))

        // Build color mapping AFTER list is fully populated
        OreColorMapping.updateColorMapping()
    }
}

/**
 * Maps block keys to ore scanner factor values.
 * Used by both the Portable Ore Scanner (rendering) and Auto Miner (ore detection).
 */
object OreColorMapping {
    @get:JvmStatic
    val map: FloatArray
        get() = cache ?: updateColorMapping()

    @Volatile
    private var cache: FloatArray? = null

    @JvmStatic
    fun updateColorMapping(): FloatArray {
        // The block key is the block's registry id (16 bits; the 1.7.10 metadata nibble is gone).
        val blockKeyMapping = FloatArray(1024 * 64)

        for (c in OreScannerManager.oreScannerConfig) {
            if (c.blockKey >= 0 && c.blockKey < blockKeyMapping.size)
                blockKeyMapping[c.blockKey] = c.factor
        }

        cache = blockKeyMapping
        return blockKeyMapping
    }
}
