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

/** Controlled inspection galleries, NOT golden-image comparisons or artistic-quality assertions. */
class BlockGallery(private val lightingOnly: Boolean = false) {
    private var entries: List<BlockContracts.Entry>? = null
    private var index = 0
    private var ticks = 0
    private var settledFrames = 0
    private val report = ContractReport(if (lightingOnly) "lighting-gallery" else "blocks-gallery")
    private var initialized = false
    private var finished = false
    private var preparation: java.util.concurrent.CompletableFuture<*>? = null
    private fun filename(e: BlockContracts.Entry) = "smoke-${if (lightingOnly) "lighting" else "block"}-${e.id.replace(':', '-')}.png"

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
                        .filter { !lightingOnly || when (it.kind) {
                            "six" -> mods.eln.Eln.sixNodeItem.getDescriptor(it.descriptor) is mods.eln.sixnode.lampsocket.LampSocketDescriptor
                            "transparent" -> mods.eln.Eln.transparentNodeItem.getDescriptor(it.descriptor) is mods.eln.transparentnode.floodlight.FloodlightDescriptor
                            else -> false
                        } }
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
            preparation = mc.singleplayerServer!!.submit(Runnable {
                val server = mc.singleplayerServer!!
                val player = server.playerList.players.first()
                val world = player.serverLevel()
                if (lightingOnly) LightingChecks.prepareGallery(world, player, entry)
                world.setDayTime(6000)
                world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                world.setWeatherParameters(100000, 0, false, false)
                var target = Vec3(entry.x + .5, entry.y + .5, entry.z + .5)
                val camera = if (lightingOnly && entry.kind == "six") {
                    val side = mods.eln.misc.Direction.fromInt(entry.side)!!.toFacing()
                    val outward = Vec3.atLowerCornerOf(side.normal).scale(-1.0)
                    target = target.add(outward.scale(-.45))
                    val offset = if (side.axis == net.minecraft.core.Direction.Axis.Y) Vec3(.75, 0.0, .5)
                        else if (side.axis == net.minecraft.core.Direction.Axis.X) Vec3(0.0, .5, .75) else Vec3(.75, .5, 0.0)
                    target.add(outward.scale(2.4)).add(offset)
                } else target.add(3.5, 2.5, 3.5)
                player.teleportTo(world, camera.x, camera.y - player.eyeHeight, camera.z, 0f, 0f)
                player.lookAt(EntityAnchorArgument.Anchor.EYES, target)
            })
        }
        if (ticks++ < 30) return false
        if (preparation?.isDone == false && ticks < 600) return false
        val clientFixture = if (lightingOnly && entry.kind == "six")
            runCatching { LightingClientChecks.checkWiredFixture(mc, entry) } else Result.success(Unit)
        if (clientFixture.isFailure && ticks < 200) {
            settledFrames = 0
            return false
        }
        // A newly synchronized renderer can arrive after the previous frame was drawn.
        if (clientFixture.isSuccess && settledFrames++ < 10) return false
        report.test(entry.id, "fixture-prepared") {
            check(preparation?.isDone == true) { "Fixture preparation timed out" }
            preparation!!.join()
        }
        if (lightingOnly && entry.kind == "six") report.test(entry.id, "wired-client-fixture") { clientFixture.getOrThrow() }
        Screenshot.grab(mc.gameDirectory, filename(entry), mc.mainRenderTarget) { }
        index++
        ticks = 0
        settledFrames = 0
        return false
    }
}
