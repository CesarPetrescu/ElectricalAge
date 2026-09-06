package mods.eln.misc

import mods.eln.init.Items
import net.minecraft.world.entity.player.Player

fun Player?.isHoldingMeter(): Boolean {
    if (this == null) return false
    return heldEquipment.any {
        Items.multiMeterElement.checkSameItemStack(it)
            || Items.thermometerElement.checkSameItemStack(it)
            || Items.allMeterElement.checkSameItemStack(it)
    }
}
