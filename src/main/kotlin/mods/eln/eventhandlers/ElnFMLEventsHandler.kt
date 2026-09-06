package mods.eln.eventhandlers

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent
import mods.eln.Achievements
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import net.minecraft.server.level.ServerPlayer

class ElnFMLEventsHandler {
    /** Crafting the 48V macerator grants its advancement; the event fires on both sides, the server grants. */
    @SubscribeEvent
    fun onCraft(event: ItemCraftedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val descriptor = GenericItemBlockUsingDamageDescriptor.getDescriptor(event.crafting) ?: return
        if (descriptor.name.lowercase().replace(' ', '_') == "48v_macerator") {
            Achievements.grant(player, Achievements.craft50VMacerator)
        }
    }
}
