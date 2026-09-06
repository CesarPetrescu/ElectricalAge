package mods.eln.misc

import mods.eln.i18n.I18N.tr
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.loading.FMLEnvironment

/**
 * The descriptor tooltip hooks build `List<String>` (1.7.10 shape); this is the one place that
 * turns them into 1.21 components and applies the shift/ctrl "hold for details" convention.
 * Side-safe: the client-only calls live in [ClientTooltips], which the server never loads.
 */
object Tooltips {
    /** The player looking at the tooltip - null on a dedicated server (tooltips are client-side anyway). */
    @JvmStatic
    fun viewer(): Player? = if (FMLEnvironment.dist.isClient) ClientTooltips.player() else null

    @JvmStatic
    fun showItemTooltip(details: List<String>, realismDetails: List<String>, realisticEnum: RealisticEnum?, dst: MutableList<Component>) {
        val out = ArrayList<String>()
        if (realisticEnum != null)
            out.add("§r${realisticEnum.color}${realisticEnum.name}§r")
        if (details.isNotEmpty()) {
            if (isShiftHeld()) {
                out.addAll(details)
            } else {
                out.add("§F§o${tr("Hold [shift] for details")}")
            }
        }
        if (realismDetails.isNotEmpty()) {
            if (isControlHeld()) {
                out.addAll(realismDetails)
            } else if (realisticEnum != null) {
                out.add("§F§o${tr("Hold [ctrl] for realism details")}")
            }
        }
        out.forEach { dst.add(Component.literal(it)) }
    }

    private fun isShiftHeld(): Boolean = FMLEnvironment.dist.isClient && ClientTooltips.shift()
    private fun isControlHeld(): Boolean = FMLEnvironment.dist.isClient && ClientTooltips.control()
}

private object ClientTooltips {
    // Declared as Player, not LocalPlayer, so the caller's bytecode never names a client class.
    fun player(): Player? = Minecraft.getInstance().player
    fun shift(): Boolean = Screen.hasShiftDown()
    fun control(): Boolean = Screen.hasControlDown()
}
