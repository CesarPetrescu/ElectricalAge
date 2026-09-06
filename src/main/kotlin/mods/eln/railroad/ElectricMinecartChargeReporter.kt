package mods.eln.railroad

import net.neoforged.bus.api.SubscribeEvent
import mods.eln.Eln
import mods.eln.i18n.I18N
import mods.eln.misc.Utils
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

class ElectricMinecartChargeReporter {

    // 1.12.2 splits PlayerInteractEvent into per-action subclasses.
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) = report(event)

    @SubscribeEvent
    fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) = report(event)

    private fun report(event: PlayerInteractEvent) {
        val player = event.entity
        val world = player.level()
        if (world.isClientSide) return

        val minecart = player.vehicle as? EntityElectricMinecart ?: return
        val heldItem = player.mainHandItem
        val multiMeter = Eln.multiMeterElement
        val allMeter = Eln.allMeterElement
        val holdingMeter = (multiMeter != null && multiMeter.checkSameItemStack(heldItem)) ||
                (allMeter != null && allMeter.checkSameItemStack(heldItem))
        if (!holdingMeter) {
            return
        }
        val message = I18N.tr("Cart Energy: ") + Utils.plotEnergy(minecart.energyBufferJoules)
        Utils.sendMessage(player, message)
    }
}
