package mods.eln.node

import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.item.ItemMovingHelper
import mods.eln.item.electricalinterface.IItemEnergyBattery
import mods.eln.sixnode.electricalcable.IUtilityCableInventory
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import mods.eln.misc.isNothing

class AutoAcceptInventoryProxy(val inventory: Container) {
    companion object {
        var creativeFreeInsert = false
    }
    interface ExistingItemHandler {
        fun handleExistingInventoryItem(itemStack: ItemStack)
    }

    class SimpleItemDropper(val node: NodeBase) : ExistingItemHandler {
        override fun handleExistingInventoryItem(itemStack: ItemStack) {
            node.dropItem(itemStack)
        }
    }

    private abstract class ItemAcceptor(val index: Int, val acceptedItems: Array<out Class<out Any>>) {
        abstract fun take(itemStack: ItemStack?, inventory: Container): Boolean
    }

    private open class ItemAcceptorIfEmpty(index: Int, acceptedItems: Array<out Class<out Any>>)
        : ItemAcceptor(index, acceptedItems) {
        override fun take(itemStack: ItemStack?, inventory: Container): Boolean {
            if (inventory.getItem(index).isNothing()) {
                if (!itemStack.isNothing() ) {
                    GenericItemUsingDamageDescriptor.getDescriptor(itemStack)?.let { desc ->
                        if (acceptedItems.any { it.isAssignableFrom(desc.javaClass) }) {
                            val newItemStack = desc.newItemStack()
                            (desc as? IItemEnergyBattery)?.let { it.setEnergy(newItemStack, it.getEnergy(itemStack)) }
                            if (!creativeFreeInsert) itemStack.count -= 1
                            inventory.setItem(index, newItemStack)
                            return true
                        }
                    }

                    GenericItemBlockUsingDamageDescriptor.getDescriptor(itemStack)?.let { desc ->
                        if (acceptedItems.any { it.isAssignableFrom(desc.javaClass) }) {
                            if (desc is UtilityCableDescriptor) {
                                return IUtilityCableInventory.trimCable(itemStack, inventory, index, creativeFreeInsert)
                            }
                            if (!creativeFreeInsert) itemStack.count -= 1
                            inventory.setItem(index, desc.newItemStack())
                            return true
                        }
                    }
                }
            }
            return false
        }
    }

    private open class ItemAcceptorIfIncrement(index: Int, val maxItems: Int, acceptedItems: Array<out Class<out Any>>)
        : ItemAcceptorIfEmpty(index, acceptedItems) {
        override fun take(itemStack: ItemStack?, inventory: Container): Boolean {
            if (super.take(itemStack, inventory)) return true

            if (itemStack.isNothing()) return false

            val existingStack = inventory.getItem(index)
            if (existingStack?.count ?: 0 >= maxItems) return false

            val existingItemDescriptor = GenericItemUsingDamageDescriptor.getDescriptor(existingStack)
            val itemDescriptor = GenericItemUsingDamageDescriptor.getDescriptor(itemStack)

            if (existingItemDescriptor != null && existingItemDescriptor == itemDescriptor) {
                if (!creativeFreeInsert) itemStack.count -= 1
                existingStack.count += 1
                return true
            }

            val existingItemBloackDescriptor = GenericItemBlockUsingDamageDescriptor.getDescriptor(existingStack)
            val itemBlockDescriptor = GenericItemBlockUsingDamageDescriptor.getDescriptor(itemStack)

            if (existingItemBloackDescriptor != null && existingItemBloackDescriptor == itemBlockDescriptor) {
                if (itemBlockDescriptor is UtilityCableDescriptor) {
                    // one more segment off the spool, not the whole spool
                    if (!IUtilityCableInventory.consumeFromSpool(itemStack, IUtilityCableInventory.requiredLengthOf(inventory), creativeFreeInsert)) return false
                } else if (!creativeFreeInsert) itemStack.count -= 1
                existingStack.count += 1
                return true
            }

            return false
        }
    }

    private class ItemAcceptorAlways(index: Int, maxItems: Int, acceptedItems: Array<out Class<out Any>>,
                                     val existingItemHandler: ExistingItemHandler?)
        : ItemAcceptorIfIncrement(index, maxItems, acceptedItems) {
        override fun take(itemStack: ItemStack?, inventory: Container): Boolean {
            if (super.take(itemStack, inventory)) return true

            if (itemStack.isNothing()) return false

            // TODO: What do we do with the item that is actually in the slot? For the moment it just disappears.

            GenericItemUsingDamageDescriptor.getDescriptor(itemStack)?.let {
                if (acceptedItems.contains(it.javaClass)) {
                    if (!creativeFreeInsert) itemStack.count -= 1
                    existingItemHandler?.handleExistingInventoryItem(inventory.getItem(index))
                    inventory.setItem(index, it.newItemStack())
                    return true
                }
            }

            GenericItemBlockUsingDamageDescriptor.getDescriptor(itemStack)?.let {
                if (acceptedItems.contains(it.javaClass)) {
                    if (!creativeFreeInsert) itemStack.count -= 1
                    existingItemHandler?.handleExistingInventoryItem(inventory.getItem(index))
                    inventory.setItem(index, it.newItemStack())
                    return true
                }
            }

            return false
        }
    }

    private val itemAcceptors: Array<ItemAcceptor?> = arrayOfNulls(inventory.containerSize)

    fun acceptIfEmpty(index: Int, vararg types: Class<out Any>): AutoAcceptInventoryProxy {
        if (index >= 0 && index < itemAcceptors.count()) {
            itemAcceptors[index] = ItemAcceptorIfEmpty(index, types)
        }
        return this
    }

    fun acceptIfIncrement(index: Int, maxItems: Int, vararg types: Class<out Any>): AutoAcceptInventoryProxy {
        if (index >= 0 && index < itemAcceptors.count()) {
            itemAcceptors[index] = ItemAcceptorIfIncrement(index, maxItems, types)
        }
        return this
    }

    fun acceptAlways(index: Int, maxItems: Int, existingItemHandler: ExistingItemHandler?,
                     vararg types: Class<out Any>): AutoAcceptInventoryProxy {
        if (index >= 0 && index < itemAcceptors.count()) {
            itemAcceptors[index] = ItemAcceptorAlways(index, maxItems, types, existingItemHandler)
        }
        return this
    }

    fun take(itemStack: ItemStack?): Boolean {
        val accepted = itemAcceptors.filterNotNull().any { it.take(itemStack, inventory) }
        if (accepted) {
            inventory.setChanged()
        }
        return accepted
    }

    fun take(itemStack: ItemStack?, nodeElement: INodeElement?, publish: Boolean = false,
             notifyInventoryChange: Boolean = false) =
        if (take(itemStack)) {
            if (publish) {
                nodeElement?.needPublish()
            }
            if (notifyInventoryChange) {
                nodeElement?.inventoryChange(inventory)
            }
            true
        } else
            false

    fun takeFrom(inv: Inventory, nodeElement: INodeElement?, publish: Boolean = false, notifyInventoryChange: Boolean = false, matchDescriptor: GenericItemUsingDamageDescriptor? = null): Boolean {
        var ret = false
        for(idx in 0 until inv.containerSize) {
            val stack = inv.getItem(idx).takeUnless { it.isEmpty } ?: continue
            if(matchDescriptor != null) {
                val desc = GenericItemUsingDamageDescriptor.getDescriptor(stack)
                if(matchDescriptor != desc) continue
            }
            ret = ret || take(stack)
        }
        if(ret) {
            if(publish) nodeElement?.needPublish()
            if(notifyInventoryChange) nodeElement?.inventoryChange(inventory)
            ItemMovingHelper.syncEntireInventory(inv.player)
        }
        return ret
    }
}
