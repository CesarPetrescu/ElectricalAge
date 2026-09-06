package mods.eln.node.transparent

import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils.readFromNBT
import mods.eln.misc.Utils.writeToNBT
import mods.eln.sixnode.electricalcable.IUtilityCableInventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import mods.eln.misc.writeToNBT

open class TransparentNodeElementInventory : WorldlyContainer, INBTTReady, IUtilityCableInventory {
    @JvmField
    protected var transparentNodeRender: TransparentNodeElementRender? = null
    @JvmField
    protected var transparentNodeElement: TransparentNodeElement? = null
    var stackLimit: Int
    override var requiredCableLength: Double = IUtilityCableInventory.DEFAULT_REQUIRED_LENGTH

    constructor(size: Int, stackLimit: Int, transparentNodeRender: TransparentNodeElementRender?) {
        inv = Array(size) { ItemStack.EMPTY }
        this.stackLimit = stackLimit
        this.transparentNodeRender = transparentNodeRender
    }

    constructor(size: Int, stackLimit: Int, transparentNodeElement: TransparentNodeElement?) {
        inv = Array(size) { ItemStack.EMPTY }
        this.stackLimit = stackLimit
        this.transparentNodeElement = transparentNodeElement
    }

    /**
     * Constructor for inventories which require a different length of utility cable than the default.
     * This is a separate constructor because Java sucks and does not allow for default values in constructors.
     */
    constructor(size: Int, stackLimit: Int, transparentNodeElement: TransparentNodeElement?, requiredCableLength: Double) {
        inv = Array(size) { ItemStack.EMPTY }
        this.stackLimit = stackLimit
        this.transparentNodeElement = transparentNodeElement
        this.requiredCableLength = requiredCableLength
    }

    private var inv: Array<ItemStack>
    override fun getContainerSize(): Int {
        return inv.size
    }

    override fun getItem(slot: Int): ItemStack {
        return inv[slot]
    }

    /** 1.11+: an inventory is empty when every slot holds ItemStack.EMPTY. */
    override fun isEmpty(): Boolean = inv.all { it.isEmpty }

    override fun removeItem(slot: Int, amt: Int): ItemStack {
        var stack = getItem(slot)
        if (!stack.isEmpty) {
            if (stack.count <= amt) {
                setItem(slot, ItemStack.EMPTY)
            } else {
                stack = stack.split(amt)
                if (stack.count == 0) {
                    setItem(slot, ItemStack.EMPTY)
                }
            }
        }
        return stack
    }

    /** 1.11 renamed getStackInSlotOnClosing; the semantics (take the slot's contents) are unchanged. */
    override fun removeItemNoUpdate(slot: Int): ItemStack {
        val stack = getItem(slot)
        if (!stack.isEmpty) {
            setItem(slot, ItemStack.EMPTY)
        }
        return stack
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        inv[slot] = stack
        if (!stack.isEmpty && stack.count > maxStackSize) {
            stack.count = maxStackSize
        }
    }

    override fun clearContent() {
        for (i in inv.indices) inv[i] = ItemStack.EMPTY
    }





    override fun getMaxStackSize(): Int {
        return stackLimit
    }

    override fun stillValid(player: Player): Boolean {
        return true
    }

    override fun startOpen(player: Player) {}
    override fun stopOpen(player: Player) {}
    override fun setChanged() {
        if (transparentNodeElement != null && !transparentNodeElement!!.node!!.isDestructing) {
            transparentNodeElement!!.inventoryChange(this)
        }
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        readFromNBT(nbt, str, this)
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        writeToNBT(nbt, str, this)
    }

    override fun canPlaceItem(i: Int, itemstack: ItemStack): Boolean {
        for (idx in 0..5) {
            val lol = getAccessibleSlotsFromSide(idx)
            for (hohoho in lol) {
                if (hohoho == i && canPlaceItemThroughFace(i, itemstack, idx)) {
                    return true
                }
            }
        }
        return false
    }

    // ---------------------------------------------------------------- sided access
    //
    // 1.8 changed WorldlyContainer's side parameter from an int to Direction. The mod's own
    // inventories index by the 0..5 side value everywhere (it matches Direction), and
    // Direction.get3DDataValue() preserves that ordering, so the interface methods adapt onto the
    // int-sided ones that subclasses actually override.

    final override fun getSlotsForFace(side: Direction): IntArray =
        getAccessibleSlotsFromSide(side.get3DDataValue())

    final override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction?): Boolean =
        canPlaceItemThroughFace(slot, stack, side?.get3DDataValue() ?: 0)

    final override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean =
        canTakeItemThroughFace(slot, stack, side.get3DDataValue())

    open fun getAccessibleSlotsFromSide(side: Int): IntArray = intArrayOf()

    open fun canPlaceItemThroughFace(slot: Int, stack: ItemStack?, side: Int): Boolean = false

    open fun canTakeItemThroughFace(slot: Int, stack: ItemStack?, side: Int): Boolean = false
}
