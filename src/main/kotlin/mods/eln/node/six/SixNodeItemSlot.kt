package mods.eln.node.six

import mods.eln.Eln
import mods.eln.gui.ISlotSkin.SlotSkin
import mods.eln.gui.SlotWithSkinAndComment
import mods.eln.sixnode.electricalcable.IUtilityCableInventory
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

/**
 * A slot for six-node items (cables, mostly). Since the Flattening every descriptor is its own
 * item, so the stack is recognised by its descriptor, and a descriptor of a subclass of a listed
 * class is accepted - the utility cable spools are electrical cables, and the right-click path
 * has always taken them as such.
 *
 * A spool never goes into a device whole: inserting one through the GUI (drop or shift-click)
 * cuts the segment the device needs off it, like right-clicking the block does, and hands the
 * rest of the spool back.
 */
open class SixNodeItemSlot(
    inventory: Container?, slot: Int,
    x: Int, y: Int,
    var stackLimit: Int,
    var descriptorClassList: Array<Class<*>>, skin: SlotSkin, comment: Array<String>
) : SlotWithSkinAndComment(inventory, slot, x, y, skin, comment) {

    override fun mayPlace(itemStack: ItemStack): Boolean {
        val descriptor = Eln.sixNodeItem.getDescriptor(itemStack) ?: return false
        return descriptorClassList.any { it.isAssignableFrom(descriptor.javaClass) }
    }

    override fun getMaxStackSize(): Int {
        return stackLimit
    }

    /** The cable length one segment takes here, from the device's inventory. */
    val requiredCableLength: Double
        get() = IUtilityCableInventory.requiredLengthOf(container)

    /**
     * A spool (more cable than one segment) is cut, never moved: an empty slot gets a fresh
     * segment, a slot holding segments of the same cable gets one more. Returns the spool with
     * one segment less, or the stack untouched when nothing was cut.
     */
    fun cutSpoolInto(spool: ItemStack, creativeFree: Boolean = false): Boolean {
        if (!mayPlace(spool) || !IUtilityCableInventory.isSpoolLongerThan(spool, requiredCableLength)) return false
        val existing = item
        if (existing.isEmpty) {
            return IUtilityCableInventory.trimCable(spool, container, containerSlot, creativeFree)
        }
        if (existing.count >= getMaxStackSize(existing)) return false
        val spoolDescriptor = Eln.sixNodeItem.getDescriptor(spool) as? UtilityCableDescriptor ?: return false
        val existingDescriptor = Eln.sixNodeItem.getDescriptor(existing)
        if (existingDescriptor !== spoolDescriptor) return false
        if (IUtilityCableInventory.isSpoolLongerThan(existing, requiredCableLength)) return false   // not a stack of segments
        if (!IUtilityCableInventory.consumeFromSpool(spool, requiredCableLength, creativeFree)) return false
        existing.grow(1)
        setChanged()
        return true
    }

    override fun safeInsert(stack: ItemStack, increment: Int): ItemStack {
        if (cutSpoolInto(stack)) return stack
        return super.safeInsert(stack, increment)
    }
}
