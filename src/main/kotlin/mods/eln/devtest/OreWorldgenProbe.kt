package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.ore.OreBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.GenerationStep
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Observes ordinary chunk generation: never places ore or calls a feature's place() method. */
class OreWorldgenProbe private constructor(private val restart: Boolean) {
    companion object {
        @JvmStatic fun registerIfRequested() {
            val mode = System.getProperty("eln.oreWorldgenTest") ?: return
            require(mode == "generate" || mode == "restart")
            NeoForge.EVENT_BUS.register(OreWorldgenProbe(mode == "restart"))
        }
    }
    private val output = Path.of(System.getProperty("eln.oreWorldgenOutput"))
    private val phase = if (restart) "restart" else "generate"
    private val profile = System.getProperty("eln.oreWorldgenProfile")
    private val report = ContractReport("ore-$phase", output)
    // The hook is registered during mod construction, before deferred block registration finishes.
    private val ores by lazy { BuiltInRegistries.BLOCK.filterIsInstance<OreBlock>().associateBy { BuiltInRegistries.BLOCK.getKey(it).toString() }.toSortedMap() }
    private data class Region(val name: String, val level: ServerLevel, val start: Int, val width: Int)
    private data class OreCount(var count: Int = 0, var minY: Int? = null, var maxY: Int? = null, val positions: MutableList<Long> = mutableListOf())
    private data class Census(val chunks: Int, val terrain: Int, val biomes: Set<String>, val ores: Map<String, Map<String, Any?>>)
    private val census = linkedMapOf<String, Census>()
    private var regions = emptyList<Region>()
    private var regionIndex = 0
    private var chunkIndex = 0
    private var counts: Map<String, OreCount> = emptyMap()
    private var terrain = 0
    private val biomes = sortedSetOf<String>()
    private var started = false
    private var finished = false
    private fun enabled(id: String) = when (profile) {
        "default", "default-alt" -> true
        "disabled" -> false
        "mixed" -> id in setOf("eln:lead_ore", "eln:cinnabar_ore")
        else -> error("Unknown ore profile $profile")
    }

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (finished) return
        val server = event.server
        try {
            if (!started) {
                started = true
                report.write(false)
                if (!report.test("runtime", "packaged-dedicated-seed-profile") { setup(server) }) { finish(server); return }
                report.test("registry", "all-registered-ores-covered") {
                    check(ores.keys == setOf("eln:copper_ore", "eln:lead_ore", "eln:tungsten_ore", "eln:cinnabar_ore"))
                }
                for ((id, block) in ores) {
                    report.test(id, "configuration") {
                        check(block.descriptor.spawnRate > 0)
                        check(block.descriptor.configKey == "worldgen.ores.${id.substringAfter(':').removeSuffix("_ore")}.enabled")
                        check(Eln.config.getBooleanOrElse(block.descriptor.configKey!!, false) == enabled(id))
                    }
                    report.test(id, "biome-attachment") {
                        for (biome in server.registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
                            val features = biome.value().generationSettings.features().getOrNull(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal)
                            val attached = features?.stream()?.filter { it.unwrapKey().orElseThrow().location().toString() == id }?.count() ?: 0L
                            val expected = if (biome.`is`(BiomeTags.IS_OVERWORLD) && enabled(id)) 1L else 0L
                            check(attached == expected) { "$id in ${biome.key().location()}: $attached, expected $expected" }
                        }
                    }
                }
            }
            val region = regions[regionIndex]
            scanChunk(region, region.start + chunkIndex % region.width, region.start + chunkIndex / region.width)
            chunkIndex++
            if (chunkIndex == region.width * region.width) {
                finishRegion(region)
                regionIndex++; chunkIndex = 0; terrain = 0; biomes.clear(); counts = ores.mapValues { OreCount() }
                if (regionIndex == regions.size) finish(server)
            }
        } catch (t: Throwable) {
            report.test("runtime", "unexpected-error") { throw t }
            finish(server)
        }
    }

    private fun setup(server: MinecraftServer) {
        counts = ores.mapValues { OreCount() }
        check(server.isDedicatedServer && FMLEnvironment.production)
        check(server.overworld().chunkSource.generator !is FlatLevelSource) { "Superflat is not an ore-generation test" }
        check(server.overworld().seed == System.getProperty("eln.oreWorldgenSeed").toLong())
        enabled("eln:copper_ore") // Validate profile before scanning.
        val jar = ModList.get().getModFileById("eln").file.filePath
        check(Files.isRegularFile(jar))
        val sha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)).joinToString("") { "%02x".format(it) }
        check(sha == System.getProperty("eln.oreWorldgenJarSha256"))
        Files.createDirectories(output)
        Files.writeString(output.resolve("runtime-$phase.json"), GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "pid" to ProcessHandle.current().pid(), "jarSha256" to sha, "seed" to server.overworld().seed,
            "profile" to profile, "dedicated" to true, "production" to true)))
        regions = listOf(Region("overworld", server.overworld(), 64, 4),
            Region("nether", checkNotNull(server.getLevel(Level.NETHER)), 64, 2),
            Region("end", checkNotNull(server.getLevel(Level.END)), 0, 2)) +
            if (restart) listOf(Region("fresh-after-restart", server.overworld(), 128, 4)) else emptyList()
    }

    private fun scanChunk(region: Region, x: Int, z: Int) {
        val chunk = region.level.getChunk(x, z) // Normal generator pipeline, including biome modifiers.
        val pos = BlockPos.MutableBlockPos()
        for (y in region.level.minBuildHeight until region.level.maxBuildHeight) for (dx in 0..15) for (dz in 0..15) {
            pos.set(x * 16 + dx, y, z * 16 + dz)
            val block = chunk.getBlockState(pos).block
            if (block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.NETHERRACK || block == Blocks.END_STONE) terrain++
            if (block !is OreBlock) continue
            val count = counts.getValue(BuiltInRegistries.BLOCK.getKey(block).toString())
            count.count++; count.minY = minOf(count.minY ?: y, y); count.maxY = maxOf(count.maxY ?: y, y)
            count.positions.add(pos.asLong())
        }
        biomes.add(region.level.getBiome(BlockPos(x * 16 + 8, 16, z * 16 + 8)).unwrapKey().orElseThrow().location().toString())
    }

    private fun finishRegion(region: Region) {
        report.test(region.name, "normal-terrain-scanned") { check(terrain > 1000) { "No meaningful natural terrain: $terrain" } }
        val samples = counts.mapValues { (_, count) ->
            val bytes = count.positions.sorted().joinToString(",").toByteArray(Charsets.UTF_8)
            mapOf("count" to count.count, "minY" to count.minY, "maxY" to count.maxY,
                "positionsSha256" to MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
        census[region.name] = Census(region.width * region.width, terrain, biomes.toSet(), samples)
        for ((id, block) in ores) report.test(id, "${region.name}/count-and-height") {
            val count = counts.getValue(id)
            val shouldGenerate = region.level.dimension() == Level.OVERWORLD && enabled(id)
            if (shouldGenerate) {
                check(count.count > 0) { "$id never generated in ${region.width * region.width} normal chunks" }
                // Vanilla veins extend around their sampled origin; this is not a strict block-height cutoff.
                val margin = block.descriptor.spawnSizeMax
                check(count.minY!! >= block.descriptor.spawnHeightMin - margin && count.maxY!! <= block.descriptor.spawnHeightMax + margin)
            } else check(count.count == 0) { "$id generated despite config/dimension exclusion: ${count.count}" }
        }
        if (restart && region.name != "fresh-after-restart") report.test(region.name, "persisted-ore-positions") {
            val old = JsonParser.parseString(Files.readString(output.resolve("census-generate.json"))).asJsonObject
            val current = GsonBuilder().create().toJsonTree(samples)
            check(old[region.name].asJsonObject["ores"] == current) { "Ore counts/heights/positions changed after save and restart" }
        }
        report.write(false)
    }

    private fun finish(server: MinecraftServer) {
        if (finished) return
        finished = true
        Files.createDirectories(output)
        Files.writeString(output.resolve("census-$phase.json"), GsonBuilder().setPrettyPrinting().create().toJson(census))
        report.write(true)
        if (report.failures > 0) Thread({ server.runningThread.join(); kotlin.system.exitProcess(1) }, "ore-worldgen-failed").start()
        server.halt(false)
    }
}
