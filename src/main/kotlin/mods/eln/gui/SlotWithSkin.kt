package mods.eln.gui

import mods.eln.gui.ISlotSkin.SlotSkin
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.Slot

open class SlotWithSkin(
    inventory: IInventory?,
    slotIndex: Int,
    xPos: Int,
    yPos: Int,
    var skin: SlotSkin
): Slot(
    inventory,
    slotIndex,
    xPos,
    yPos
), ISlotSkin {
    override fun getSlotSkin(): SlotSkin {
        return skin
    }
}
