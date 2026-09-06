package mods.eln.packets

import mods.eln.network.IMessage
import mods.eln.network.IMessageHandler
import mods.eln.network.MessageContext
import mods.eln.integration.waila.WailaCache

/**
 * Created by Gregory Maddra on 2016-06-27.
 */
class TransparentNodeResponsePacketHandler : IMessageHandler<TransparentNodeResponsePacket, IMessage> {
    override fun onMessage(message: TransparentNodeResponsePacket, ctx: MessageContext?): IMessage? {
        val entries = message.entries
        val coord = message.coord
        WailaCache.nodes.put(coord, entries)
        return null
    }
}
