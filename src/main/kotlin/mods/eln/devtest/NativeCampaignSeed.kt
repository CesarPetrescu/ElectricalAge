package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import java.nio.file.Files
import java.security.MessageDigest

/** Optional receipt for a seed made by the same packaged production JAR as the clients. */
@EventBusSubscriber(modid=Eln.MODID,value=[Dist.DEDICATED_SERVER])
object NativeCampaignSeed {
    private const val FILE = "native-production-seed.json"
    fun registry(): List<String> =
        (Eln.sixNodeItem.subItemList.values + Eln.transparentNodeItem.subItemList.values)
            .filterNotNull().map { "${BuiltInRegistries.ITEM.getKey(it.parentItem)}#${it.parentItemDamage}" }.sorted()

    private fun checkRequested() {
        if (System.getProperty("eln.nativeSeedJarSha256") == null) return
        check(FMLEnvironment.production && !Eln.instance.isDevelopmentRun) { "Native seed must use a production launcher, not Gradle runServer" }
        val jar = ModList.get().getModFileById("eln").file.filePath
        check(Files.isRegularFile(jar)) { "Seed must run a packaged ELN JAR" }
        val sha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)).joinToString("") { "%02x".format(it) }
        check(sha == System.getProperty("eln.nativeSeedJarSha256")) { "Seed JAR differs from the requested artifact" }
        check(Eln.findItemStack("Isolation Transformer", 1) == null) { "Development-only transformer leaked into production" }
    }

    @SubscribeEvent fun serverStarted(event: ServerStartedEvent) {
        val sha = System.getProperty("eln.nativeSeedJarSha256") ?: return
        checkRequested()
        val world = event.server.overworld()
        val data = mapOf("schema" to 1, "production" to true, "jarSha256" to sha,
            "registryVerified" to true, "pid" to ProcessHandle.current().pid(), "registry" to registry(),
            "developmentOnly" to listOf("Isolation Transformer: excluded from production; not a passed test"))
        Files.writeString(world.server.getWorldPath(LevelResource.ROOT).resolve(FILE), GsonBuilder().setPrettyPrinting().create().toJson(data))
    }

    fun verify(world: ServerLevel, jarSha: String) {
        check(FMLEnvironment.production && !Eln.instance.isDevelopmentRun)
        val data = JsonParser.parseString(Files.readString(world.server.getWorldPath(LevelResource.ROOT).resolve(FILE))).asJsonObject
        check(data["schema"].asInt == 1 && data["production"].asBoolean && data["registryVerified"].asBoolean)
        check(data["jarSha256"].asString == jarSha) { "Seed world was made with another JAR" }
        check(data["registry"].asJsonArray.map { it.asString } == registry()) { "Seed and client registries differ" }
    }
}
