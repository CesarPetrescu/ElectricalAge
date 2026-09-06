package mods.eln.item

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils.sendMessage
import mods.eln.server.ElnDestroyHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

private const val NOPE_WAND_RADIUS = 10

class NopeWandDescriptor(name: String) : GenericItemUsingDamageDescriptor(name, "nopewand") {
    override fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
        if (w.isClientSide || p !is ServerPlayer) return s

        val summary = ElnDestroyHelper.destroyAroundPlayer(w, p, NOPE_WAND_RADIUS)
        if (summary == null) {
            sendMessage(p, tr("The Nope Wand fizzles: node manager unavailable."))
            return s
        }

        sendMessage(
            p,
            tr(
                "The Nope Wand removed %1$ ELN nodes and cleared %2$ ELN blocks in a %3$-block radius.",
                summary.nodesDestroyed,
                summary.blocksCleared,
                NOPE_WAND_RADIUS
            )
        )
        return s
    }

    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        list.add(tr("Right click to immediately remove ELN nodes and blocks in a 10-block radius."))
        list.add(tr("Removes them without dropping items, much like /eln zonedestroy."))
    }
}
