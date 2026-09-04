package mods.eln.node.transparent

import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils.readFromNBT
import mods.eln.misc.Utils.writeToNBT
import mods.eln.sixnode.electricalcable.IUtilityCableInventory
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.ISidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString

open class TransparentNodeElementInventory : ISidedInventory, INBTTReady, IUtilityCableInventory {
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
    override fun getSizeInventory(): Int {
        return inv.size
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        return inv[slot]
    }

    /** 1.11+: an inventory is empty when every slot holds ItemStack.EMPTY. */
    override fun isEmpty(): Boolean = inv.all { it.isEmpty }

    override fun decrStackSize(slot: Int, amt: Int): ItemStack {
        var stack = getStackInSlot(slot)
        if (!stack.isEmpty) {
            if (stack.count <= amt) {
                setInventorySlotContents(slot, ItemStack.EMPTY)
            } else {
                stack = stack.splitStack(amt)
                if (stack.count == 0) {
                    setInventorySlotContents(slot, ItemStack.EMPTY)
                }
            }
        }
        return stack
    }

    /** 1.11 renamed getStackInSlotOnClosing; the semantics (take the slot's contents) are unchanged. */
    override fun removeStackFromSlot(slot: Int): ItemStack {
        val stack = getStackInSlot(slot)
        if (!stack.isEmpty) {
            setInventorySlotContents(slot, ItemStack.EMPTY)
        }
        return stack
    }

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {
        inv[slot] = stack
        if (!stack.isEmpty && stack.count > inventoryStackLimit) {
            stack.count = inventoryStackLimit
        }
    }

    override fun clear() {
        for (i in inv.indices) inv[i] = ItemStack.EMPTY
    }

    // The mod exposes no synced GUI fields; IInventory requires the accessors regardless.
    override fun getField(id: Int): Int = 0
    override fun setField(id: Int, value: Int) {}
    override fun getFieldCount(): Int = 0

    /** IInventory extends IWorldNameable on 1.11+, replacing getInventoryName/hasCustomInventoryName. */
    override fun getName(): String = "tco.TransparentNodeInventory"

    override fun hasCustomName(): Boolean = false

    override fun getDisplayName(): ITextComponent = TextComponentString(name)

    override fun getInventoryStackLimit(): Int {
        return stackLimit
    }

    override fun isUsableByPlayer(player: EntityPlayer): Boolean {
        return true
    }

    override fun openInventory(player: EntityPlayer) {}
    override fun closeInventory(player: EntityPlayer) {}
    override fun markDirty() {
        if (transparentNodeElement != null && !transparentNodeElement!!.node!!.isDestructing) {
            transparentNodeElement!!.inventoryChange(this)
        }
    }

    override fun readFromNBT(nbt: NBTTagCompound, str: String) {
        readFromNBT(nbt, str, this)
    }

    override fun writeToNBT(nbt: NBTTagCompound, str: String) {
        writeToNBT(nbt, str, this)
    }

    override fun isItemValidForSlot(i: Int, itemstack: ItemStack): Boolean {
        for (idx in 0..5) {
            val lol = getAccessibleSlotsFromSide(idx)
            for (hohoho in lol) {
                if (hohoho == i && canInsertItem(i, itemstack, idx)) {
                    return true
                }
            }
        }
        return false
    }

    // ---------------------------------------------------------------- sided access
    //
    // 1.8 changed ISidedInventory's side parameter from an int to EnumFacing. The mod's own
    // inventories index by the 0..5 side value everywhere (it matches Direction), and
    // EnumFacing.getIndex() preserves that ordering, so the interface methods adapt onto the
    // int-sided ones that subclasses actually override.

    final override fun getSlotsForFace(side: EnumFacing): IntArray =
        getAccessibleSlotsFromSide(side.index)

    final override fun canInsertItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        canInsertItem(slot, stack, side.index)

    final override fun canExtractItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        canExtractItem(slot, stack, side.index)

    open fun getAccessibleSlotsFromSide(side: Int): IntArray = intArrayOf()

    open fun canInsertItem(slot: Int, stack: ItemStack?, side: Int): Boolean = false

    open fun canExtractItem(slot: Int, stack: ItemStack?, side: Int): Boolean = false
}
