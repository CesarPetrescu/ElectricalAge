package mods.eln.devtest

import com.google.gson.Gson
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import java.nio.file.Files

/** Nightly inspection gallery, NOT a golden-image comparison or an artistic-quality assertion. */
class BlockGallery {
    private var entries: List<BlockContracts.Entry>? = null
    private var index = 0
    private var ticks = 0
    private val report = ContractReport("blocks-gallery")
    private var initialized = false
    private var finished = false
    private fun filename(e: BlockContracts.Entry) = "smoke-block-${e.id.replace(':', '-')}.png"

    /** Client thread. Screenshot callbacks finish asynchronously; completion validates every file. */
    fun tick(mc: Minecraft): Boolean {
        if (finished) return true
        if (!initialized) {
            initialized = true
            report.write(false)
            val file = mc.singleplayerServer!!.getWorldPath(LevelResource.ROOT).resolve("eln-contracts.json")
            report.test("gallery", "manifest") {
                entries = Files.newBufferedReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonArray.map { Gson().fromJson(it, BlockContracts.Entry::class.java) }
                }
                check(entries!!.isNotEmpty())
                // These exact named screenshots belong to this test, never arbitrary user screenshots.
                entries!!.forEach { Files.deleteIfExists(mc.gameDirectory.toPath().resolve("screenshots").resolve(filename(it))) }
            }
        }
        val fixtures = entries.orEmpty()
        if (index >= fixtures.size) {
            if (ticks++ < 100) return false
            fixtures.forEach { e -> report.test(e.id, "screenshot-written") {
                val path = mc.gameDirectory.toPath().resolve("screenshots").resolve(filename(e))
                check(Files.isRegularFile(path) && Files.size(path) > 1024) { "Screenshot not written: $path" }
            } }
            report.write(true)
            check(report.failures == 0) { "Per-block gallery failed" }
            finished = true
            return true
        }
        val entry = fixtures[index]
        if (ticks == 0) {
            mc.setScreen(null)
            mc.options.hideGui = true
            mc.singleplayerServer!!.execute {
                val server = mc.singleplayerServer!!
                val player = server.playerList.players.first()
                val world = player.serverLevel()
                world.setDayTime(6000)
                world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                world.setWeatherParameters(100000, 0, false, false)
                val target = Vec3(entry.x + .5, entry.y + .5, entry.z + .5)
                val camera = target.add(3.5, 2.5, 3.5)
                player.teleportTo(world, camera.x, camera.y - player.eyeHeight, camera.z, 0f, 0f)
                player.lookAt(EntityAnchorArgument.Anchor.EYES, target)
            }
        }
        if (ticks++ < 30) return false
        Screenshot.grab(mc.gameDirectory, filename(entry), mc.mainRenderTarget) { }
        index++
        ticks = 0
        return false
    }
}
