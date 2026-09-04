package mods.eln.node.six

import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils.readFromNBT
import mods.eln.misc.Utils.writeToNBT
import mods.eln.sixnode.electricalcable.IUtilityCableInventory
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString

class SixNodeElementInventory : IInventory, INBTTReady, IUtilityCableInventory {
    var sixNodeRender: SixNodeElementRender? = null
    var sixNodeElement: SixNodeElement? = null
    var stackLimit: Int
    override var requiredCableLength = IUtilityCableInventory.DEFAULT_REQUIRED_LENGTH

    constructor(size: Int, stackLimit: Int, sixNodeRender: SixNodeElementRender?) {
        inv = Array(size) { ItemStack.EMPTY }
        this.stackLimit = stackLimit
        this.sixNodeRender = sixNodeRender
    }

    constructor(size: Int, stackLimit: Int, sixNodeElement: SixNodeElement?) {
        inv = Array(size) { ItemStack.EMPTY }
        this.stackLimit = stackLimit
        this.sixNodeElement = sixNodeElement
    }

    /**
     * Constructor for inventories which require a different length of utility cable than the default.
     * This is a separate constructor because Java sucks and does not allow for default values in constructors.
     */
    constructor(size: Int, stackLimit: Int, sixNodeElement: SixNodeElement?, requiredCableLength: Double) {
        inv = Array(size) { ItemStack.EMPTY }
        this.stackLimit = stackLimit
        this.sixNodeElement = sixNodeElement
        this.requiredCableLength = requiredCableLength
    }

    private var inv: Array<ItemStack>
    override fun getSizeInventory(): Int {
        return inv.size
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        return if (slot >= inv.size) ItemStack.EMPTY else inv[slot]
    }

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

    /** 1.11 renamed getStackInSlotOnClosing; the semantics are unchanged. */
    override fun removeStackFromSlot(slot: Int): ItemStack {
        val stack = getStackInSlot(slot)
        if (!stack.isEmpty) {
            setInventorySlotContents(slot, ItemStack.EMPTY)
        }
        return stack
    }

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {
        try {
            inv[slot] = stack
            if (!stack.isEmpty && stack.count > inventoryStackLimit) {
                stack.count = inventoryStackLimit
            }
        } catch (e: Exception) {
            // TODO: handle exception
        }
    }

    override fun clear() {
        for (i in inv.indices) inv[i] = ItemStack.EMPTY
    }

    override fun getField(id: Int): Int = 0
    override fun setField(id: Int, value: Int) {}
    override fun getFieldCount(): Int = 0

    override fun getDisplayName(): ITextComponent = TextComponentString(name)

    /** IInventory extends IWorldNameable on 1.11+. */
    override fun getName(): String {
        return "tco.SixNodeInventory"
    }

    override fun getInventoryStackLimit(): Int {
        return stackLimit
    }

    override fun isUsableByPlayer(player: EntityPlayer): Boolean {
        return true
    }

    override fun openInventory(player: EntityPlayer) {}
    override fun closeInventory(player: EntityPlayer) {}
    override fun markDirty() {
        if (sixNodeElement != null && !sixNodeElement!!.sixNode!!.isDestructing) {
            sixNodeElement!!.inventoryChanged()
        }
    }

    override fun readFromNBT(nbt: NBTTagCompound, str: String) {
        readFromNBT(nbt, str, this)
    }

    override fun writeToNBT(nbt: NBTTagCompound, str: String) {
        writeToNBT(nbt, str, this)
    }

    override fun isItemValidForSlot(i: Int, itemstack: ItemStack): Boolean {
        return false
    }

    override fun hasCustomName(): Boolean {
        return false
    }
}
