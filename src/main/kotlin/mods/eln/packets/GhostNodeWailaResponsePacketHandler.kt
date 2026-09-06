package mods.eln.packets

import mods.eln.network.IMessage
import mods.eln.network.IMessageHandler
import mods.eln.network.MessageContext
import mods.eln.integration.waila.GhostNodeWailaData
import mods.eln.integration.waila.WailaCache
import mods.eln.misc.Coordinate

class GhostNodeWailaResponsePacketHandler : IMessageHandler<GhostNodeWailaResponsePacket, IMessage> {

    private fun Coordinate.isNull() = this.x == 0 && this.y == 0 && this.z == 0 && this.dimension == 0

    override fun onMessage(message: GhostNodeWailaResponsePacket, ctx: MessageContext?): IMessage? {
        if (!message.realCoord.isNull()) {
            WailaCache.ghostNodes.put(message.coord, GhostNodeWailaData(message.realCoord, message.itemStack,
                message.type, message.realSide))
        }

        return null
    }
}
