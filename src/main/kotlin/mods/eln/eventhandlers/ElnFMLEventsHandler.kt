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

    /** Tags are bound when a world's packs load (and sent to the client): expand the machine recipes declared on dictionary names. */
    @SubscribeEvent
    fun onTagsUpdated(@Suppress("UNUSED_PARAMETER") event: net.neoforged.neoforge.event.TagsUpdatedEvent) {
        mods.eln.misc.RecipesList.resolveAllTags()
    }
}
