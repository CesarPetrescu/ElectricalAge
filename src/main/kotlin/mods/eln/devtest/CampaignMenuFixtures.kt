package mods.eln.devtest

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files

@EventBusSubscriber(modid = Eln.MODID)
object CampaignMenuFixtures {
    private var ticks = 0
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (System.getProperty("eln.campaign") != "menu-setup") return
        if (++ticks != 20) return
        val report = ContractReport("all-menu-fixtures")
        report.write(false)
        try {
            val world = event.server.overworld()
            FakePlayerFactory.getMinecraft(world).setGameMode(GameType.CREATIVE)
            check(BlockContracts.place(world) == 0)
            val root = event.server.getWorldPath(LevelResource.ROOT)
            val entries = Files.newBufferedReader(root.resolve("eln-contracts.json")).use { reader -> JsonParser.parseReader(reader).asJsonArray.map { Gson().fromJson(it, BlockContracts.Entry::class.java) } }
            val targets = mutableListOf<BlockContracts.Entry>()
            for (entry in entries) {
                if (entry.kind !in listOf("six", "transparent")) continue
                report.test(entry.id, "advertised-gui-enumeration") {
                    val node = checkNotNull(NodeManager.instance!!.getNodeFromCoordonate(Coordinate(entry.x,entry.y,entry.z,world)))
                    if (node.hasGui(checkNotNull(Direction.fromInt(entry.side)))) targets.add(entry)
                }
            }
            check(targets.isNotEmpty())
            Files.writeString(root.resolve("menu-targets.json"),GsonBuilder().setPrettyPrinting().create().toJson(targets))
            println("NATIVE_MENU_TARGETS ${targets.size}")
            report.write(true)
        } catch (t: Throwable) {
            report.test("campaign","unexpected") { throw t }
            report.write(false)
        }
        if (report.failures > 0) {
            val thread = event.server.runningThread
            Thread({thread.join();System.exit(1)},"menu-fixture-exit").start()
        }
        event.server.halt(false)
    }
}
