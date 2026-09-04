package mods.eln.eventhandlers

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import mods.eln.Eln
import mods.eln.packets.AchievePacket
import mods.eln.wiki.Root
import net.minecraftforge.client.event.GuiOpenEvent

class ElnForgeEventsHandler {
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    fun openGuide(event: GuiOpenEvent) {
        if (event.gui is Root) {
            Eln.elnNetwork.sendToServer(openWikiPacket)
        }
    }

    companion object {
        private val openWikiPacket = AchievePacket("openWiki")
    }
}
