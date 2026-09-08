package mods.eln.worldgen

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mods.eln.ore.OreBlock
import net.minecraft.core.registries.BuiltInRegistries
import org.junit.Assert.*
import org.junit.Test

class OreWorldgenDataTest {
    private fun data(path: String): JsonObject = javaClass.getResourceAsStream("/data/eln/$path.json")!!.bufferedReader().use {
        JsonParser.parseReader(it).asJsonObject
    }
    private fun ores() = BuiltInRegistries.BLOCK.filterIsInstance<OreBlock>()

    @Test fun everyRegisteredOreHasACanonicalConfigGateAndPositiveUnconditionalRate() {
        assertEquals(setOf("copper_ore", "lead_ore", "tungsten_ore", "cinnabar_ore"), ores().map { BuiltInRegistries.BLOCK.getKey(it).path }.toSet())
        for (block in ores()) {
            val path = BuiltInRegistries.BLOCK.getKey(block).path
            val descriptor = block.descriptor
            val modifier = data("neoforge/biome_modifier/$path")
            assertEquals("worldgen.ores.${path.removeSuffix("_ore")}.enabled", descriptor.configKey)
            assertEquals(descriptor.configKey, modifier["config"].asString)
            assertEquals("eln:ores", modifier["type"].asString)
            assertEquals("eln:$path", modifier["feature"].asString)
            assertTrue(descriptor.configDefault)
            assertTrue("$path must not bake a disabled build-time config into its rate", descriptor.spawnRate > 0)
        }
    }

    @Test fun shippedPlacementMatchesDescriptorsAndUsesBiomeFiltering() {
        for (block in ores()) {
            val path = BuiltInRegistries.BLOCK.getKey(block).path
            val descriptor = block.descriptor
            val placed = data("worldgen/placed_feature/$path")
            assertEquals("eln:$path", placed["feature"].asString)
            val modifiers = placed["placement"].asJsonArray.map { it.asJsonObject }.associateBy { it["type"].asString }
            assertEquals(setOf("minecraft:count", "minecraft:in_square", "minecraft:height_range", "minecraft:biome"), modifiers.keys)
            assertEquals(descriptor.spawnRate, modifiers.getValue("minecraft:count")["count"].asInt)
            val height = modifiers.getValue("minecraft:height_range")["height"].asJsonObject
            assertEquals("minecraft:uniform", height["type"].asString)
            assertEquals(descriptor.spawnHeightMin, height["min_inclusive"].asJsonObject["absolute"].asInt)
            assertEquals(descriptor.spawnHeightMax, height["max_inclusive"].asJsonObject["absolute"].asInt)
        }
    }

    @Test fun shippedFeaturesReplaceBothStoneAndDeepslateWithTheCorrectOre() {
        for (block in ores()) {
            val path = BuiltInRegistries.BLOCK.getKey(block).path
            val feature = data("worldgen/configured_feature/$path")
            assertEquals("minecraft:ore", feature["type"].asString)
            val config = feature["config"].asJsonObject
            assertEquals((block.descriptor.spawnSizeMin + block.descriptor.spawnSizeMax) / 2, config["size"].asInt)
            val targets = config["targets"].asJsonArray.map { it.asJsonObject }
            assertEquals(setOf("minecraft:stone_ore_replaceables", "minecraft:deepslate_ore_replaceables"), targets.map { it["target"].asJsonObject["tag"].asString }.toSet())
            targets.forEach { assertEquals("eln:$path", it["state"].asJsonObject["Name"].asString) }
        }
    }
}
