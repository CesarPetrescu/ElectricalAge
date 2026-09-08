package mods.eln.devtest

import com.google.gson.JsonObject
import mods.eln.Eln
import mods.eln.ServerKeyHandler
import mods.eln.item.IConfigurable
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Utils
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.sixnode.electricalcable.ElectricalCableElement
import mods.eln.sixnode.electricaldatalogger.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import kotlin.math.abs

class MultiplayerServerProbe : MultiplayerProbe("server") {
    private lateinit var server: MinecraftServer
    private val world get() = server.overworld()
    private fun element(pos: BlockPos): SixNodeElement? = (NodeManager.instance?.getNodeFromCoordonate(
        Coordinate(pos.x, pos.y, pos.z, world)) as? SixNode)?.getElement(Direction.YN)
    private fun monitor() = element(MultiplayerScene.monitor) as ElectricalDataLoggerElement

    @SubscribeEvent fun onTick(event: ServerTickEvent.Post) { server = event.server; tick() }

    override fun begin(command: JsonObject) {
        when (action(command)) {
            "boot" -> {
                check(server.isDedicatedServer)
                world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                world.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server)
                world.gameRules.getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server)
                world.gameRules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, server)
                world.dayTime = 6000
                world.setDefaultSpawnPos(BlockPos(100, 66, 103), 180f)
            }
            "fixture" -> {
                for (x in 93..105) for (z in 93..105) world.setBlockAndUpdate(BlockPos(x, 64, z), Blocks.STONE.defaultBlockState())
                place("Electrical Source", MultiplayerScene.source)
                place("Low Voltage Cable", MultiplayerScene.source.offset(1, 0, 0))
                place("Low Voltage Cable", MultiplayerScene.source.offset(2, 0, 0))
                place("Creative Power Resistor", MultiplayerScene.source.offset(3, 0, 0), 90f)
                place("Ground Cable", MultiplayerScene.source.offset(4, 0, 0))
                val player = FakePlayerFactory.getMinecraft(world)
                (element(MultiplayerScene.source) as IConfigurable).readConfigTool(CompoundTag().apply { putDouble("voltage", 12.0) }, player)
                (element(MultiplayerScene.source.offset(3, 0, 0)) as IConfigurable).readConfigTool(CompoundTag().apply { putDouble("resistance", 12.0) }, player)
                val descriptor = Eln.sixNodeItem.subItemList.values.filterIsInstance<ElectricalDataLoggerDescriptor>().first { it.onFloor }
                place(descriptor.name, MultiplayerScene.monitor)
                MultiplayerMonitorFixture.prepare(monitor())
                if (ModList.get().isLoaded("create")) mods.eln.integration.create.MultiplayerCreateChecks.place(world)
            }
            "home", "away", "nether" -> {
                for (p in server.playerList.players) {
                    p.closeContainer()
                    p.setGameMode(GameType.CREATIVE)
                    val level = if (action(command) == "nether") server.getLevel(Level.NETHER)!! else world
                    val x = if (action(command) == "away") 1024.5 else 98.5
                    val z = if (action(command) == "away") 1024.5 else 102.5
                    level.setBlockAndUpdate(BlockPos.containing(x, 64.0, z), Blocks.STONE.defaultBlockState())
                    p.teleportTo(level, x, 65.0, z, 180f, 40f)
                    p.abilities.flying = true; p.onUpdateAbilities()
                }
            }
        }
    }

    private fun place(name: String, pos: BlockPos, yaw: Float = 0f) {
        check(world.isEmptyBlock(pos)) { "Fixture would overwrite $pos" }
        val player = FakePlayerFactory.getMinecraft(world)
        player.yRot = yaw; player.yHeadRot = yaw; player.isShiftKeyDown = false
        val stack = Eln.findItemStack(name, 1)
        check(!stack.isEmpty) { "Missing descriptor $name" }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        check(Eln.sixNodeItem.placeBlockAt(stack, player, world, pos, net.minecraft.core.Direction.UP, .5f, 1f, .5f)) { "Failed to place $name" }
        check(element(pos)?.sixNodeElementDescriptor?.name == name) { "Wrong descriptor at $pos" }
    }

    override fun observe(command: JsonObject): JsonObject? {
        val a = action(command)
        when (a) {
            "boot" -> return runtime().apply { addProperty("dedicated", server.isDedicatedServer) }
            "fixture" -> {
                if (ticks == 20 && ModList.get().isLoaded("create")) mods.eln.integration.create.MultiplayerCreateChecks.start(world)
                if (ticks < 40) return null
            }
            "players" -> {
                val expected = command.getAsJsonArray("names").map { it.asString }.toSet()
                val players = server.playerList.players
                if (players.map { it.gameProfile.name }.toSet() != expected) return null
                check(players.map { it.uuid }.toSet().size == players.size)
                return passed().apply { addProperty("players", players.joinToString { "${it.gameProfile.name}:${it.uuid}" }) }
            }
            "keys" -> {
                val expected = command.getAsJsonObject("keys")
                for ((name, value) in expected.entrySet()) {
                    val id = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
                    if (ServerKeyHandler.get(id, ServerKeyHandler.WRENCH) != value.asBoolean) return null
                    server.playerList.getPlayer(id)?.let {
                        check(Utils.isPlayerUsingWrench(it) == value.asBoolean) { "Interaction consumer differs for $name" }
                    }
                }
                return passed().apply { add("keys", expected.deepCopy()) }
            }
            "home", "away", "nether" -> if (ticks < 30) return null
            "unloaded" -> {
                // A real chunk-source observation: no force-load, getBlockEntity or NodeManager lookup here.
                for (pos in listOf(MultiplayerScene.source, MultiplayerScene.monitor, MultiplayerScene.adapter)) {
                    if (world.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4) != null) return null
                }
            }
            "circuit" -> {
                val voltage = command.get("voltage").asDouble
                val cable = element(MultiplayerScene.source.offset(1, 0, 0)) as? ElectricalCableElement ?: return null
                val load = cable.electricalLoad
                if (abs(load.voltage - voltage) > .15 || abs(abs(load.current) - voltage / 12.0) > .1) return null
                return passed().apply { addProperty("voltage", load.voltage); addProperty("current", load.current) }
            }
            "monitor" -> if (!MultiplayerMonitorFixture.matches(monitor())) return null
            "printed" -> {
                val e = monitor()
                val stack = e.inventory!!.getItem(1)
                if (!MultiplayerMonitorFixture.isPrint(stack)) return null
                check(e.inventory!!.getItem(0).isEmpty)
                MultiplayerMonitorFixture.verifyPrint(stack)
            }
            "one-print-total" -> {
                if (ticks < 40) return null // allow both concurrent requests to reach the server
                val e = monitor()
                val stacks = server.playerList.players.flatMap { it.inventory.items } + listOf(e.inventory!!.getItem(1))
                val prints = stacks.filter { MultiplayerMonitorFixture.isPrint(it) }
                check(e.inventory!!.getItem(0).isEmpty && prints.sumOf { it.count } == 1) { "Concurrent printing/transfer duplicated or lost output" }
                check(e.inventory!!.getItem(1).isEmpty) { "Output was not transferred to either real player" }
                prints.forEach { MultiplayerMonitorFixture.verifyPrint(it) }
            }
            "adapter" -> if (!mods.eln.integration.create.MultiplayerCreateChecks.matches(world, command.get("ratio").asInt, command.get("engaged").asBoolean)) return null
            "stop" -> Unit
            else -> error("Unknown server action $a")
        }
        return passed()
    }

    override fun finish(command: JsonObject) { if (action(command) == "stop") server.halt(false) }

    companion object {
        @JvmStatic fun registerIfRequested() {
            if (System.getProperty("eln.multiplayerTest") == "server") NeoForge.EVENT_BUS.register(MultiplayerServerProbe())
        }
    }
}
