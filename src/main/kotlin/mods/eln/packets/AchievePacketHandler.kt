package mods.eln.packets

import mods.eln.network.IMessage
import mods.eln.network.IMessageHandler
import mods.eln.network.MessageContext
import mods.eln.Achievements

class AchievePacketHandler : IMessageHandler<AchievePacket?, IMessage?> {

    override fun onMessage(message: AchievePacket?, ctx: MessageContext?): IMessage? {
        if (message == null || ctx == null) return null
        //System.out.println("Got message: " + message.text);
        if (message.text == "openWiki") {
            Achievements.grant(ctx.serverHandler.player, Achievements.openGuide)
        } else if (message.text == "craft50VMacerator") {
            Achievements.grant(ctx.serverHandler.player, Achievements.craft50VMacerator)
        } else {
            println("[ELN]: ELN Wiki Achievement Handler has received an invalid message/packet: " + message.text)
        }
        return null
    }
}
