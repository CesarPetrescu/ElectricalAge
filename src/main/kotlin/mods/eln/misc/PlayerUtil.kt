package mods.eln.misc

import mods.eln.Eln
import net.minecraft.world.entity.player.Player

fun Player?.isHoldingMeter(): Boolean {
    if (this == null) return false
    val equippedItem = heldItemMainhand
    return (Eln.multiMeterElement.checkSameItemStack(equippedItem)
        || Eln.thermometerElement.checkSameItemStack(equippedItem)
        || Eln.allMeterElement.checkSameItemStack(equippedItem))
}
