package mods.eln.devtest

import com.google.gson.JsonObject
import mods.eln.Eln
import mods.eln.ServerKeyHandler
import mods.eln.misc.Direction
import mods.eln.network.ElnNetwork
import mods.eln.node.six.SixNodeEntity
import mods.eln.sixnode.electricalcable.ElectricalCableRender
import mods.eln.sixnode.electricaldatalogger.*
import mods.eln.sixnode.electricalsource.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.abs

class MultiplayerClientProbe(role: String) : MultiplayerProbe(role) {
    private val mc get() = Minecraft.getInstance()
    private fun render(pos: BlockPos) = (mc.level?.getBlockEntity(pos) as? SixNodeEntity)?.elementRenderList?.get(Direction.YN.int)

    @SubscribeEvent fun onTick(event: ClientTickEvent.Post) { tick() }

    override fun begin(command: JsonObject) {
        when (action(command)) {
            "join" -> ConnectScreen.startConnecting(TitleScreen(), mc, ServerAddress.parseString("127.0.0.1:25565"),
                ServerData("ELN CI", "127.0.0.1:25565", ServerData.Type.OTHER), false, null)
            "disconnect" -> { mc.level?.disconnect(); mc.disconnect(TitleScreen()) }
            "key" -> {
                // Real production packet; two headless windows cannot both own native keyboard focus.
                val bytes = ByteArrayOutputStream()
                DataOutputStream(bytes).use { it.writeByte(Eln.packetPlayerKey.toInt()); it.writeUTF(ServerKeyHandler.WRENCH); it.writeBoolean(command.get("pressed").asBoolean) }
                ElnNetwork.sendToServer(bytes)
            }
            "source-set" -> (render(MultiplayerScene.source) as ElectricalSourceRender)
                .clientSetFloat(ElectricalSourceElement.setVoltageId, command.get("voltage").asFloat)
            "open-monitor" -> use(MultiplayerScene.monitor)
            "print" -> MultiplayerMonitorClient.clickPrint(mc.screen as ElectricalDataLoggerGui)
            "print-request" -> (render(MultiplayerScene.monitor) as ElectricalDataLoggerRender).clientSend(ElectricalDataLoggerElement.printId.toInt())
            "take-print" -> {
                val gui = mc.screen as ElectricalDataLoggerGui
                mc.gameMode!!.handleInventoryMouseClick(gui.menu.containerId, 1, 0, ClickType.QUICK_MOVE, mc.player!!)
            }
            "close" -> mc.player!!.closeContainer()
            "open-adapter" -> use(MultiplayerScene.adapter)
            "adapter-command" -> mc.gameMode!!.handleInventoryButtonClick(mc.player!!.containerMenu.containerId, command.get("button").asInt)
        }
    }

    private fun use(pos: BlockPos) {
        check(mc.player!!.distanceToSqr(pos.center) < 36) { "Fixture out of interaction range" }
        val empty = (0..8).firstOrNull { mc.player!!.inventory.getItem(it).isEmpty }
        if (empty != null) mc.player!!.inventory.selected = empty
        mc.gameMode!!.useItemOn(mc.player!!, InteractionHand.MAIN_HAND,
            BlockHitResult(Vec3(pos.x + .5, pos.y + 1.0, pos.z + .5), net.minecraft.core.Direction.UP, pos, false))
    }

    override fun observe(command: JsonObject): JsonObject? {
        val a = action(command)
        when (a) {
            "boot" -> {
                if (mc.screen !is TitleScreen || mc.overlay != null) return null
                return runtime().apply { addProperty("integratedServer", mc.singleplayerServer != null) }
            }
            "join" -> {
                if (mc.level == null || mc.player == null || mc.screen != null) return null
                check(mc.singleplayerServer == null && !mc.isLocalServer) { "Not a real dedicated-server connection" }
                return passed().apply { addProperty("name", mc.player!!.gameProfile.name); addProperty("uuid", mc.player!!.uuid.toString()); addProperty("integratedServer", false) }
            }
            "disconnect" -> if (mc.level != null || mc.player != null) return null
            "key", "source-set", "print", "print-request", "take-print", "close", "adapter-command" -> if (ticks < 3) return null
            "source" -> {
                val r = render(MultiplayerScene.source) as? ElectricalSourceRender ?: return null
                val voltage = MultiplayerSourceClient.voltage(r)
                if (abs(voltage - command.get("voltage").asDouble) > .001) return null
                val cable = render(MultiplayerScene.source.offset(1, 0, 0)) as? ElectricalCableRender ?: return null
                if (Integer.bitCount(cable.connectedSide.mask) != 2) return null
                return passed().apply { addProperty("voltage", voltage); addProperty("connections", cable.connectedSide.mask) }
            }
            "monitor" -> {
                val r = render(MultiplayerScene.monitor) as? ElectricalDataLoggerRender ?: return null
                if (!MultiplayerMonitorClient.matches(r)) return null
            }
            "open-monitor" -> {
                if (mc.screen !is ElectricalDataLoggerGui) return null
                val gui = mc.screen as ElectricalDataLoggerGui
                if (gui.menu.getSlot(0).item.count != 1 || !gui.menu.getSlot(1).item.isEmpty || ticks < 10) return null
            }
            "printed" -> {
                val gui = mc.screen as? ElectricalDataLoggerGui ?: return null
                if (!MultiplayerMonitorFixture.isPrint(gui.menu.getSlot(1).item)) return null
                check(gui.menu.getSlot(0).item.isEmpty)
                MultiplayerMonitorFixture.verifyPrint(gui.menu.getSlot(1).item)
            }
            "inventory" -> {
                if (ticks < 30) return null
                val prints = mc.player!!.inventory.items.filter { MultiplayerMonitorFixture.isPrint(it) }
                prints.forEach { MultiplayerMonitorFixture.verifyPrint(it) }
                return passed().apply { addProperty("prints", prints.sumOf { it.count }) }
            }
            "dimension" -> if (mc.level?.dimension()?.location()?.toString() != command.get("dimension").asString || mc.screen != null) return null
            "unloaded" -> if (mc.level?.getChunkSource()?.hasChunk(MultiplayerScene.monitor.x shr 4, MultiplayerScene.monitor.z shr 4) != false) return null
            "open-adapter" -> if (!mods.eln.integration.create.MultiplayerCreateClient.menuReady()) return null
            "adapter" -> if (!mods.eln.integration.create.MultiplayerCreateClient.matches(command.get("ratio").asInt, command.get("engaged").asBoolean)) return null
            "stop" -> Unit
            else -> error("Unknown client action $a")
        }
        return passed()
    }

    override fun capture(id: String) {
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(directory.resolve("${System.getProperty("eln.multiplayerTest")}-${id}.png")) }
    }
    override fun finish(command: JsonObject) { if (action(command) == "stop") mc.stop() }

    companion object {
        @JvmStatic fun registerIfRequested() {
            val role = System.getProperty("eln.multiplayerTest")
            if (role == "alpha" || role == "beta") NeoForge.EVENT_BUS.register(MultiplayerClientProbe(role))
        }
    }
}
