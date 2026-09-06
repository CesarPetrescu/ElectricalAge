package mods.eln.gui

import mods.eln.gui.ISlotSkin.SlotSkin
import net.minecraft.world.Container
import net.minecraft.world.inventory.Slot

open class SlotWithSkin(
    inventory: Container?,
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
