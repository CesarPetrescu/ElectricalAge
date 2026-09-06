package mods.eln

import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceLocation

/**
 * 1.12.2 replaced achievements with data-driven advancements. The two Eln achievements live in
 * data/eln/advancement/ with an `impossible` criterion, so they are only ever granted from here
 * (the same server-side triggers as before, via [mods.eln.packets.AchievePacketHandler]).
 */
object Achievements {
    @JvmField
    val openGuide = ResourceLocation.fromNamespaceAndPath(Eln.MODID, "root")
    @JvmField
    val craft50VMacerator = ResourceLocation.fromNamespaceAndPath(Eln.MODID, "craft_50v_macerator")

    @JvmStatic
    fun grant(player: ServerPlayer, id: ResourceLocation) {
        val advancement = player.server.advancements.get(id) ?: return
        val progress = player.advancements.getOrStartProgress(advancement)
        if (progress.isDone) return
        for (criterion in progress.remainingCriteria.toList()) {
            player.advancements.award(advancement, criterion)
        }
    }
}
