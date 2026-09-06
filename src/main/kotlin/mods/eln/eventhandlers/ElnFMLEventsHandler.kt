package mods.eln.eventhandlers

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent
import mods.eln.Eln
import mods.eln.packets.AchievePacket

class ElnFMLEventsHandler {
    @SubscribeEvent
    fun onCraft(event: ItemCraftedEvent) {
        if (event.crafting.translationKey.lowercase() == "48v_macerator") {
            Eln.elnNetwork.sendToServer(craft50VMaceratorPacket)
        }
    }

    companion object {
        private val craft50VMaceratorPacket = AchievePacket("craft50VMacerator")
    }
}
