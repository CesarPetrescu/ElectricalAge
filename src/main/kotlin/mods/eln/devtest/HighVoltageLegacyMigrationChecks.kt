package mods.eln.devtest

import com.google.gson.Gson
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.OneWayDcDcElement
import mods.eln.transparentnode.VariableDcDcElement
import mods.eln.transparentnode.floodlight.FloodlightElement
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path

/** Loads the archived pre-HV world without replacing or repopulating any fixture. */
@EventBusSubscriber(modid = Eln.MODID)
object HighVoltageLegacyMigrationChecks {
    private const val BASELINE = "4ff68e073b588e4711cd1575620994efc5acd33d"
    private var ticks = 0
    private var finished = false
    private var entries = emptyList<BlockContracts.Entry>()
    private val report = ContractReport("hv-legacy-migration")

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        if (System.getProperty("eln.campaign") != "hv-legacy-migration" || finished) return
        ticks++
        val server = event.server
        val world = server.overworld()
        try {
            if (ticks == 20) {
                report.write(false)
                check(Files.readString(Path.of("hv-baseline-commit.txt")).trim() == BASELINE) {
                    "The migration fixture was not produced by the pinned pre-HV build"
                }
                val path = server.getWorldPath(LevelResource.ROOT).resolve("eln-contracts.json")
                entries = Files.newBufferedReader(path).use { reader ->
                    JsonParser.parseReader(reader).asJsonArray.map { Gson().fromJson(it, BlockContracts.Entry::class.java) }
                }
                check(entries.size == 412 && entries.map { it.id }.distinct().size == 412) {
                    "The archived baseline must retain all 412 unique block identities"
                }
                entries.forEach { e ->
                    for (x in ((e.x - 4) shr 4)..((e.x + 4) shr 4)) {
                        for (z in ((e.z - 4) shr 4)..((e.z + 4) shr 4)) world.setChunkForced(x, z, true)
                    }
                }
                return
            }
            if (ticks != 100) return
            var legacyControls = 0
            report.test("baseline", "pinned-pre-HV-world-loaded-without-replacement") {
                check(entries.size == 412)
            }
            entries.forEach { e ->
                report.test(e.id, "legacy-saved-block-identity") {
                    val p = BlockPos(e.x, e.y, e.z)
                    val n = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(e.x, e.y, e.z, world))
                    when (e.kind) {
                        "six" -> {
                            val six = n as? SixNode ?: error("Missing saved six-node at $p")
                            check(six.sideElementIdList[e.side] == e.descriptor) { "Changed saved numeric descriptor at $p" }
                            val descriptor = Eln.sixNodeItem.subItemList[e.descriptor] ?: error("Missing legacy descriptor")
                            check(BuiltInRegistries.ITEM.getKey(descriptor.parentItem).toString() == e.id) { "Changed legacy registry identity" }
                            check(world.getBlockEntity(p) != null)
                        }
                        "transparent" -> {
                            val transparent = n as? TransparentNode ?: error("Missing saved transparent node at $p")
                            check(transparent.elementId == e.descriptor) { "Changed saved numeric descriptor at $p" }
                            val descriptor = Eln.transparentNodeItem.subItemList[e.descriptor] ?: error("Missing legacy descriptor")
                            check(BuiltInRegistries.ITEM.getKey(descriptor.parentItem).toString() == e.id)
                            val element = transparent.element ?: error("Missing saved element")
                            val facing = if (element is FloodlightElement) element.blockFacing.toStandardDirection() else element.front
                            check(facing.int == e.front) { "Changed saved orientation at $p" }
                            check(world.getBlockEntity(p) != null)
                            val controls = when (element) {
                                is OneWayDcDcElement -> element.settings
                                is VariableDcDcElement -> element.settings
                                else -> null
                            }
                            if (controls != null) {
                                legacyControls++
                                check(controls.version == 1 && controls.mode == "SIGNAL") { "Legacy converter control meaning changed at $p" }
                            }
                        }
                        else -> {
                            val block = world.getBlockState(p).block
                            check(BuiltInRegistries.BLOCK.getKey(block).toString() == e.id)
                            check(block !is EntityBlock || world.getBlockEntity(p) != null)
                        }
                    }
                }
            }
            report.test("eln:converters", "legacy-control-fixtures-were-actually-exercised") {
                check(legacyControls >= 5) { "Only $legacyControls legacy converter controls were loaded" }
            }
        } catch (t: Throwable) {
            report.test("baseline", "unexpected-migration-error") { throw t }
        }
        if (ticks == 100 || report.failures > 0) {
            finished = true
            report.write(true)
            if (report.failures > 0) {
                Thread({ server.runningThread.join(); kotlin.system.exitProcess(1) }, "hv-legacy-migration-failed").start()
            }
            server.halt(false)
        }
    }
}
