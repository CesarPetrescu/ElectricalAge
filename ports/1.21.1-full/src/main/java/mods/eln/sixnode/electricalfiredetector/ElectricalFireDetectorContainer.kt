package mods.eln.sixnode.electricalfiredetector

import mods.eln.generic.GenericItemUsingDamageSlot
import mods.eln.gui.ISlotSkin.SlotSkin
import mods.eln.i18n.I18N.tr
import mods.eln.item.electricalitem.BatteryItem
import mods.eln.misc.BasicContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container

class ElectricalFireDetectorContainer(player: Player, inventory: Container) :
    BasicContainer(player, inventory, arrayOf(
        GenericItemUsingDamageSlot(inventory, ElectricalFireDetectorContainer.BatteryId, 184 / 2 - 12, 8, 1,
            BatteryItem::class.java, SlotSkin.medium, arrayOf(tr("Portable battery slot"))))) {
    companion object {
        val BatteryId = 0
    }
}
