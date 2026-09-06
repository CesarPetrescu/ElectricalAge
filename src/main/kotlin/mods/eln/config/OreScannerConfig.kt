package mods.eln.config

import mods.eln.Eln
import mods.eln.item.electricalitem.OreScannerConfigElement
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.Tags

/**
 * Loads ore scanner configuration from JsonConfig and resolves ore entries
 * to block keys for use by the ore scanner and auto miner.
 */
object OreScannerConfigLoader {

    private const val ORE_FACTORS_PATH = "tools.xrayScanner.oreFactors"
    private const val AUTO_DISCOVERY_KEY = "tools.xrayScanner.autoDiscoverOreDictionaryOres"
    private const val AUTO_DISCOVERY_FACTOR_KEY = "tools.xrayScanner.autoDiscoveryOreFactor"
    private const val DEFAULT_OTHER_MOD_FACTOR = 0.15

    /**
     * Loads ore scanner entries from config. Each config key is either:
     * - A block reference (contains ':'): "modid:name" or "modid:name:meta"
     * - An OreDictionary name (no ':'): "oreCopper"
     *
     * Returns deduplicated list where later entries override earlier by blockKey.
     */
    fun loadOreScannerConfig(): List<OreScannerConfigElement> {
        val config = Eln.config
        val oreFactors = config.getStringDoubleMap(ORE_FACTORS_PATH)
        val blockKeyMap = linkedMapOf<Int, Float>()

        for ((key, factor) in oreFactors) {
            val f = factor.toFloat()
            if (key.contains(':')) {
                resolveBlockReference(key, f, blockKeyMap)
            } else {
                resolveOreDictionaryName(key, f, blockKeyMap)
            }
        }

        return blockKeyMap.map { (blockKey, factor) ->
            OreScannerConfigElement(blockKey, factor)
        }
    }

    /**
     * Auto-discovers ores from OreDictionary that are not already in configEntries.
     * Only runs if autoDiscoverOreDictionaryOres is enabled.
     * Uses autoDiscoveryOreFactor for all auto-discovered ores.
     */
    fun loadOreDictionaryAutoDiscovery(existingBlockKeys: Map<Int, Float>): List<OreScannerConfigElement> {
        val config = Eln.config
        if (!config.getBooleanOrElse(AUTO_DISCOVERY_KEY, true)) {
            return emptyList()
        }

        val otherModFactor = config.getDoubleOrElse(AUTO_DISCOVERY_FACTOR_KEY, DEFAULT_OTHER_MOD_FACTOR).toFloat()
        val results = mutableListOf<OreScannerConfigElement>()

        // 1.13+: the ore dictionary's "ore*" names are the `c:ores` block tag.
        for (holder in BuiltInRegistries.BLOCK.getTagOrEmpty(Tags.Blocks.ORES)) {
            val blockKey = BuiltInRegistries.BLOCK.getId(holder.value())
            if (blockKey !in existingBlockKeys) {
                results.add(OreScannerConfigElement(blockKey, otherModFactor))
            }
        }

        return results
    }

    private fun resolveBlockReference(key: String, factor: Float, blockKeyMap: MutableMap<Int, Float>) {
        val parts = key.split(':')
        if (parts.size < 2) {
            return
        }
        val modid = parts[0].lowercase()
        val name = parts[1]
        // "Eln:Eln.Ore:4" (a 1.7.10 config): the metadata picked the ore descriptor, which owns a block now.
        val block: Block? = if (modid == Eln.MODID && name.equals("Eln.Ore", ignoreCase = true) && parts.size >= 3) {
            parts[2].toIntOrNull()?.let { Eln.oreItem.getDescriptor(it)?.block }
        } else {
            val id = ResourceLocation.tryParse("$modid:${name.lowercase()}")
            if (id == null) {
                Eln.LOGGER.warn("{}: '{}' is not a block id", ORE_FACTORS_PATH, key)
                return
            }
            BuiltInRegistries.BLOCK.getOptional(id).orElse(null)
        }
        if (block == null || block === Blocks.AIR) {
            return
        }

        val blockKey = BuiltInRegistries.BLOCK.getId(block)
        blockKeyMap[blockKey] = factor
    }

    /**
     * An ore-dictionary name ("oreCopper") is the conventional block tag `c:ores/copper`; a name
     * that already looks like a tag id ("c:ores/copper" is caught by the ':' branch, so this is
     * only the 1.7.10 spelling).
     */
    private fun resolveOreDictionaryName(key: String, factor: Float, blockKeyMap: MutableMap<Int, Float>) {
        val path = if (key.startsWith("ore") && key.length > 3) "ores/" + key.substring(3).lowercase() else key.lowercase()
        val tag: TagKey<Block> = BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", path))
        for (holder in BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
            blockKeyMap[BuiltInRegistries.BLOCK.getId(holder.value())] = factor
        }
    }
}
