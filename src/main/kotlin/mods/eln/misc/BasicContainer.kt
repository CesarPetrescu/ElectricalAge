package mods.eln.misc

import mods.eln.gui.ISlotSkin.SlotSkin
import mods.eln.gui.SlotWithSkin
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.Container
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * Base of the mod's 37 containers. 1.21 wants a MenuType and the container id vanilla assigned;
 * the id is read from [mods.eln.GuiHandler.pendingContainerId], which both sides set right
 * before constructing the container, so the subclasses keep their (player, inventory, slots) shape.
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

    override fun addSlotToContainer(slot: Slot): Slot {
        return super.addSlot(slot)
    }

    override fun quickMoveStack(player: Player, slotId: Int): ItemStack? {
        val slot = inventorySlots[slotId] as Slot?
        if (slot != null && slot.hasItem) {
            val itemstack1 = slot.stack
            val invSize = inventory.getContainerSize()
            if (slotId < invSize) {
                mergeItemStack(itemstack1, invSize, inventorySlots.size, true)
            } else {
                if (!mergeItemStack(itemstack1, 0, invSize, true)) {
                    if (slotId < invSize + 27) {
                        mergeItemStack(itemstack1, invSize + 27, inventorySlots.size, false)
                    } else {
                        mergeItemStack(itemstack1, invSize, invSize + 27, false)
                    }
                }
            }

            if (itemstack1.count == 0) {
                slot.set(null as ItemStack?)
            } else {
                slot.setChanged()
            }
        }

        return null
    }

    override fun mergeItemStack(par1ItemStack: ItemStack, par2: Int, par3: Int, par4: Boolean): Boolean {
        var flag1 = false
        var k = par2
        if (par4) {
            k = par3 - 1
        }
        var slot: Slot
        var itemstack1: ItemStack?
        if (par1ItemStack.isStackable) {
            while (par1ItemStack.count > 0 && (!par4 && k < par3 || par4 && k >= par2)) {
                slot = inventorySlots[k] as Slot
                itemstack1 = slot.stack
                if (slot.isItemValid(par1ItemStack) && !itemstack1.isNothing() && itemstack1.item === par1ItemStack.item && (!par1ItemStack.hasSubtypes || par1ItemStack.itemDamage == itemstack1.itemDamage) && ItemStack.areItemStackTagsEqual(
                        par1ItemStack,
                        itemstack1
                    )
                ) {
                    val l = itemstack1.count + par1ItemStack.count
                    val maxSize = min(slot.maxStackSize.toDouble(), par1ItemStack.maxStackSize.toDouble())
                        .toInt()
                    if (l <= maxSize) {
                        par1ItemStack.count = 0
                        itemstack1.count = l
                        slot.setChanged()
                        flag1 = true
                    } else if (itemstack1.count < maxSize) {
                        par1ItemStack.count -= maxSize - itemstack1.count
                        itemstack1.count = maxSize
                        slot.setChanged()
                        flag1 = true
                    }
                }
                if (par4) {
                    --k
                } else {
                    ++k
                }
            }
        }
        if (par1ItemStack.count > 0) {
            k = if (par4) {
                par3 - 1
            } else {
                par2
            }
            while (!par4 && k < par3 || par4 && k >= par2) {
                slot = inventorySlots[k] as Slot
                itemstack1 = slot.stack
                if (itemstack1.isNothing() && slot.isItemValid(par1ItemStack)) {
                    val l = par1ItemStack.count
                    val maxSize = min(slot.maxStackSize.toDouble(), par1ItemStack.maxStackSize.toDouble())
                        .toInt()
                    if (l <= maxSize) {
                        slot.set(par1ItemStack.copy())
                        slot.setChanged()
                        par1ItemStack.count = 0
                        flag1 = true
                        break
                    } else {
                        par1ItemStack.count -= maxSize
                        val newItemStack = par1ItemStack.copy()
                        newItemStack.count = maxSize
                        slot.set(newItemStack)
                        slot.setChanged()
                        flag1 = true
                        break
                    }
                }
                if (par4) {
                    --k
                } else {
                    ++k
                }
            }
        }
        return flag1
    }
}
