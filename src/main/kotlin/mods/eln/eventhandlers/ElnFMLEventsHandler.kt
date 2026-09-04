package mods.eln.eventhandlers

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent
import mods.eln.Eln
import mods.eln.packets.AchievePacket

class ElnFMLEventsHandler {
    @SubscribeEvent
    fun onCraft(event: ItemCraftedEvent) {
        if (event.crafting.unlocalizedName.lowercase() == "48v_macerator") {
            Eln.elnNetwork.sendToServer(craft50VMaceratorPacket)
        }
    }

    companion object {
        private val craft50VMaceratorPacket = AchievePacket("craft50VMacerator")
    }
}
