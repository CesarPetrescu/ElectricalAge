package mods.eln.node

import mods.eln.sim.IProcess
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer

/*
 * Each tick, publishes all nodes that ask for it.
 *
 * Additionally, publish all nodes with an inventory that are opened by a player, to that player.
 */
class NodePublishProcess : IProcess {
    var counter = 0

    override fun process(time: Double) {
        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return

        val nodesToPublish = NodeManager.instance.nodesToPublish
        if (nodesToPublish.isNotEmpty()) {
            val toPublish = nodesToPublish.toList() // Copy to avoid concurrent modification
            NodeManager.instance.clearNodesToPublish()
            for (node in toPublish) {
                if (node.needPublish) {
                    node.publishToAllPlayer()
                }
            }
        }

        for (player in server.playerList.players) {
            if (player.openContainer is INodeContainer) {
                val container = player.openContainer as INodeContainer
                val openContainerNode = container.node
                if (openContainerNode != null && counter % (1 + container.refreshRateDivider) == 0) {
                    openContainerNode.publishToPlayer(player)
                }
            }
        }

        counter++
    }
}
