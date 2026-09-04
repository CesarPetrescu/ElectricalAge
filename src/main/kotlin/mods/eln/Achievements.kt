package mods.eln

import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.ResourceLocation

/**
 * 1.12.2 replaced achievements with data-driven advancements. The two Eln achievements live in
 * assets/eln/advancements/ with an `impossible` criterion, so they are only ever granted from here
 * (the same server-side triggers as before, via [mods.eln.packets.AchievePacketHandler]).
 */
object Achievements {
    @JvmField
    val openGuide = ResourceLocation(Eln.MODID, "root")
    @JvmField
    val craft50VMacerator = ResourceLocation(Eln.MODID, "craft_50v_macerator")

    @JvmStatic
    fun grant(player: EntityPlayerMP, id: ResourceLocation) {
        val advancement = player.server.advancementManager.getAdvancement(id) ?: return
        val progress = player.advancements.getProgress(advancement)
        if (progress.isDone) return
        for (criterion in progress.remaningCriteria.toList()) {
            player.advancements.grantCriterion(advancement, criterion)
        }
    }
}
