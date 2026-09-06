package mods.eln.misc

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer

/**
 * Contains utilities for dealing with player entities.
 */

fun Player.totalItemsCarried(stack: ItemStack): Int {
    return inventory.items
        .filter { ItemStack.isSameItem(it, stack) }
        .sumOf { it.count }
}

fun Player.removeMultipleItems(stack: ItemStack, count: Int) {
    if(Utils.isCreative(this as ServerPlayer)) return
    assert(count <= totalItemsCarried(stack))
    var left = count
    try {
        inventory.items.indices.reversed().forEach { i ->
            val invStack = inventory.items[i]
            if (ItemStack.isSameItem(invStack, stack)) {
                left -= invStack.split(invStack.count.coerceAtMost(left)).count
                assert(invStack.count >= 0)
                if (left == 0) return
            }
        }
    } finally {
        inventory.setChanged()
        // Synchronize immediately with the client (1.7.10 hand-built a slot packet per slot).
        inventoryMenu.broadcastChanges()
    }
}
