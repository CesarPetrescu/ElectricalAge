package mods.eln.eventhandlers

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.Eln
import mods.eln.packets.AchievePacket
import mods.eln.wiki.Root
import net.neoforged.neoforge.client.event.ScreenEvent.Opening

class ElnForgeEventsHandler {
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    fun openGuide(event: Opening) {
        if (event.gui is Root) {
            Eln.elnNetwork.sendToServer(openWikiPacket)
        }
    }

    companion object {
        private val openWikiPacket = AchievePacket("openWiki")
    }
}
