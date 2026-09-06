package mods.eln.node

import net.neoforged.neoforge.common.NeoForge

import net.minecraftforge.fml.common.FMLCommonHandler
import net.neoforged.bus.api.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import mods.eln.environment.RoomThermalManager
import net.minecraft.server.level.ServerPlayer

class NodeServer {
    fun init() {
        //	NodeBlockEntity.nodeAddedList.clear();
    }

    fun stop() {
        //	NodeBlockEntity.nodeAddedList.clear();
        RoomThermalManager.clear()
    }

    var counter = 0
    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        if (event.phase != TickEvent.Phase.START) return
        val server = FMLCommonHandler.instance().minecraftServerInstance
        if (server != null) {
            RoomThermalManager.tick(server)
            for (node in NodeManager.instance!!.nodeList) {
                if (node.needPublish) {
                    node.publishToAllPlayer()
                }
            }
            for (obj in server.playerList.players) {
                val player = obj as ServerPlayer?
                var openContainerNode: NodeBase? = null
                var container: INodeContainer? = null
                if (player!!.containerMenu != null && player.containerMenu is INodeContainer) {
                    container = player.containerMenu as INodeContainer
                    openContainerNode = container.node
                }
                for (node in NodeManager.instance!!.nodeList) {
                    if (node === openContainerNode) {
                        if (counter % (1 + container!!.refreshRateDivider) == 0) node.publishToPlayer(player)
                    }
                }
            }
            counter++
        }
    }

    init {
        NeoForge.EVENT_BUS.register(this)
    }
}
