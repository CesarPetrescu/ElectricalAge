package mods.eln.misc

import net.minecraft.world.entity.player.Player
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component

class FakeSideInventory : WorldlyContainer {
    override fun getContainerSize(): Int {
        return 0
    }

    override fun isEmpty(): Boolean = true

    override fun getItem(var1: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun removeItem(var1: Int, var2: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun removeItemNoUpdate(var1: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun setItem(var1: Int, var2: ItemStack) {}



    override fun getMaxStackSize(): Int {
        return 0
    }

    override fun setChanged() {}
    override fun stillValid(var1: Player): Boolean {
        return false
    }

    override fun startOpen(player: Player) {}
    override fun stopOpen(player: Player) {}
    override fun canPlaceItem(var1: Int, var2: ItemStack): Boolean {
        return false
    }

    override fun clearContent() {}

    override fun getSlotsForFace(side: Direction): IntArray {
        return intArrayOf()
    }

    override fun canPlaceItemThroughFace(index: Int, stack: ItemStack, direction: Direction?): Boolean {
        return false
    }

    override fun canTakeItemThroughFace(index: Int, stack: ItemStack, direction: Direction): Boolean {
        return false
    }

    companion object {
        @JvmStatic
        val instance = FakeSideInventory()
    }
}
