package mods.eln.packets

import mods.eln.integration.waila.TransparentNodeWailaData
import mods.eln.integration.waila.WailaCache
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * Created by Gregory Maddra on 2016-06-27.
 */
class TransparentNodeResponsePacketHandler : IMessageHandler<TransparentNodeResponsePacket, IMessage> {
    override fun onMessage(message: TransparentNodeResponsePacket?, ctx: MessageContext?): IMessage? {
        val map = message!!.map
        val coord = message.coord
        val stack = message.stack
        WailaCache.nodes.put(coord, TransparentNodeWailaData(stack, map))
        return null
    }
}
