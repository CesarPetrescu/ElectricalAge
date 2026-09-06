package mods.eln.eventhandlers

import net.neoforged.bus.api.SubscribeEvent
import mods.eln.network.ElnNetwork
import mods.eln.packets.AchievePacket
import mods.eln.wiki.Root
import net.neoforged.neoforge.client.event.ScreenEvent.Opening

/** Client only: opening the wiki grants the "open guide" advancement (registered from ClientSetup). */
class ElnForgeEventsHandler {
    @SubscribeEvent
    fun openGuide(event: Opening) {
        if (event.newScreen is Root) {
            ElnNetwork.sendToServer(openWikiPacket)
        }
    }

    companion object {
        private val openWikiPacket = AchievePacket("openWiki")
    }
}
