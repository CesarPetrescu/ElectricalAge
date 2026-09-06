package mods.eln.misc

import mods.eln.gui.ISlotSkin.SlotSkin
import mods.eln.gui.SlotWithSkin
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.Container
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Base of the mod's 37 containers. 1.21 wants a MenuType and the container id vanilla assigned;
 * the id is read from [mods.eln.GuiHandler.pendingContainerId], which both sides set right
 * before constructing the container, so the subclasses keep their (player, inventory, slots) shape.
 *
 * The 1.7.10 shift-click merge (`mergeItemStack`) honoured each slot's own stack limit, which
 * vanilla's `moveItemStackTo` does too since 1.17; the copy is gone.
 */
open class BasicContainer(player: Player, protected var inventory: Container, slot: Array<Slot>) :
    AbstractContainerMenu(mods.eln.GuiHandler.MENU.get(), mods.eln.GuiHandler.pendingContainerId) {
    init {
        for (i in slot.indices) {
            addSlotToContainer(slot[i])
        }
        bindPlayerInventory(player.inventory)
    }

    override fun stillValid(player: Player): Boolean {
        return inventory.stillValid(player)
    }

    private fun bindPlayerInventory(inventoryPlayer: Inventory?) {
        for (i in 0..2) {
            for (j in 0..8) {
                addSlotToContainer(SlotWithSkin(inventoryPlayer, j + i * 9 + 9, j * 18, i * 18, SlotSkin.medium))
            }
        }
        for (i in 0..8) {
            addSlotToContainer(SlotWithSkin(inventoryPlayer, i, i * 18, 58, SlotSkin.medium))
        }
    }

    /** 1.7.10's name for [addSlot]. */
    open fun addSlotToContainer(slot: Slot): Slot {
        return super.addSlot(slot)
    }

    override fun quickMoveStack(player: Player, slotId: Int): ItemStack {
        val slot = slots[slotId]
        if (slot.hasItem()) {
            val itemstack1 = slot.item
            val invSize = inventory.containerSize
            if (slotId < invSize) {
                mergeItemStack(itemstack1, invSize, slots.size, true)
            } else {
                if (!mergeItemStack(itemstack1, 0, invSize, true)) {
                    if (slotId < invSize + 27) {
                        mergeItemStack(itemstack1, invSize + 27, slots.size, false)
                    } else {
                        mergeItemStack(itemstack1, invSize, invSize + 27, false)
                    }
                }
            }

            if (itemstack1.count == 0) {
                slot.set(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }
        }

        // Nothing is returned: vanilla would loop while the source slot still has items and a
        // returned stack, and the 1.7.10 code did one pass.
        return ItemStack.EMPTY
    }

    /** 1.7.10's name for [moveItemStackTo]. */
    fun mergeItemStack(stack: ItemStack, startIndex: Int, endIndex: Int, reverse: Boolean): Boolean =
        moveItemStackTo(stack, startIndex, endIndex, reverse)
}
