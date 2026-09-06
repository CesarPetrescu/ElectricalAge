package mods.eln.item

import mods.eln.Eln
import mods.eln.GuiHandler
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiContainerEln
import mods.eln.gui.GuiHelperContainer
import mods.eln.gui.GuiTextFieldEln
import mods.eln.gui.ISlotSkin.SlotSkin
import mods.eln.gui.IGuiObject
import mods.eln.gui.SlotWithSkin
import mods.eln.i18n.I18N.tr
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.misc.Utils
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import kotlin.math.min
import mods.eln.misc.isNothing

private const val WIRE_SNIPS_CUT_ACTION_BASE = 1_000_000_000
private const val WIRE_SNIPS_CUT_ACTION_SLOT_SCALE = 1_000_000
private const val WIRE_SNIPS_INPUT_SLOT_INDEX = 0
private const val WIRE_SNIPS_PLAYER_INVENTORY_START = 1
private const val WIRE_SNIPS_PLAYER_INVENTORY_END = WIRE_SNIPS_PLAYER_INVENTORY_START + 36

class WireSnipsDescriptor(name: String) : GenericItemUsingDamageDescriptor(name, "wiresnips") {
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        list.add(tr("Right click to cut utility wire in your inventory"))
    }

    override fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
        if (!w.isClientSide) {
            GuiHandler.open(p, GuiHandler.wireSnipsOpen, w, 0, 0, 0)
        }
        return s
    }
}

class WireSnipsContainer(private val player: Player) : AbstractContainerMenu(GuiHandler.MENU.get(), GuiHandler.pendingContainerId) {
    private val inputInventory = SimpleContainer(1)

    init {
        addSlot(object : SlotWithSkin(inputInventory, WIRE_SNIPS_INPUT_SLOT_INDEX, 8, 32, SlotSkin.medium) {
            override fun getMaxStackSize(): Int = 1
        })
        bindPlayerInventory()
    }

    private fun bindPlayerInventory() {
        for (row in 0..2) {
            for (column in 0..8) {
                addSlot(SlotWithSkin(player.inventory, column + row * 9 + 9, 8 + column * 18, 118 + row * 18, SlotSkin.medium))
            }
        }
        for (column in 0..8) {
            addSlot(SlotWithSkin(player.inventory, column, 8 + column * 18, 176, SlotSkin.medium))
        }
    }

    fun selectedWireStack(): ItemStack? = inputInventory.getItem(WIRE_SNIPS_INPUT_SLOT_INDEX)

    fun selectedWireDescriptor(): UtilityCableDescriptor? {
        val stack = selectedWireStack() ?: return null
        return stack.utilityCableDescriptor()
    }

    fun encodeCutAction(lengthMeters: Double): Int {
        val clampedMeters = lengthMeters.toInt().coerceAtLeast(1)
        return WIRE_SNIPS_CUT_ACTION_BASE + WIRE_SNIPS_INPUT_SLOT_INDEX * WIRE_SNIPS_CUT_ACTION_SLOT_SCALE + clampedMeters
    }

    /** 1.7.10's enchantItem: the GUI's "cut" button arrives as a container button click. */
    override fun clickMenuButton(player: Player, action: Int): Boolean {
        if (action < WIRE_SNIPS_CUT_ACTION_BASE) return false
        val encoded = action - WIRE_SNIPS_CUT_ACTION_BASE
        val slot = encoded / WIRE_SNIPS_CUT_ACTION_SLOT_SCALE
        val targetLength = (encoded % WIRE_SNIPS_CUT_ACTION_SLOT_SCALE).toDouble()
        if (slot != WIRE_SNIPS_INPUT_SLOT_INDEX || targetLength <= 0.0) return false

        val stack = inputInventory.getItem(WIRE_SNIPS_INPUT_SLOT_INDEX).takeUnless { it.isEmpty } ?: return false
        val descriptor = UtilityCableDescriptor.allDescriptors().firstOrNull { it.checkSameItemStack(stack) } ?: return false
        val available = descriptor.getRemainingLengthMeters(stack)
        if (targetLength >= available) return false

        val cutStack = descriptor.newItemStack(1)
        descriptor.setRemainingLengthMeters(cutStack, targetLength)
        descriptor.setRemainingLengthMeters(stack, available - targetLength)
        inputInventory.setChanged()
        player.inventory.setChanged()

        if (!player.inventory.add(cutStack)) {
            player.drop(cutStack, false)
        }
        if (descriptor.getRemainingLengthMeters(stack) <= 0.0) {
            inputInventory.setItem(WIRE_SNIPS_INPUT_SLOT_INDEX, ItemStack.EMPTY)
        }
        broadcastChanges()
        return true
    }

    override fun stillValid(player: Player): Boolean = true

    override fun quickMoveStack(player: Player, slotId: Int): ItemStack {
        val slot = slots.getOrNull(slotId) ?: return ItemStack.EMPTY
        if (!slot.hasItem()) return ItemStack.EMPTY

        val stack = slot.item
        if (slotId == WIRE_SNIPS_INPUT_SLOT_INDEX) {
            if (!moveItemStackTo(stack, WIRE_SNIPS_PLAYER_INVENTORY_START, WIRE_SNIPS_PLAYER_INVENTORY_END, true)) return ItemStack.EMPTY
        } else {
            val descriptor = stack.utilityCableDescriptor()
            if (descriptor == null || descriptor.getRemainingLengthMeters(stack) <= 0.0) {
                return ItemStack.EMPTY
            }
            val inputSlot = slots[WIRE_SNIPS_INPUT_SLOT_INDEX]
            if (inputSlot.hasItem()) {
                return ItemStack.EMPTY
            }
            val moved = min(stack.count, inputSlot.maxStackSize)
            val movedStack = stack.copy()
            movedStack.count = moved
            inputSlot.set(movedStack)
            stack.count -= moved
        }

        if (stack.count <= 0) {
            slot.set(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }
        // One pass, as in 1.7.10 (a returned stack would make vanilla loop).
        return ItemStack.EMPTY
    }

    override fun removed(player: Player) {
        super.removed(player)
        val stack = inputInventory.getItem(WIRE_SNIPS_INPUT_SLOT_INDEX).takeUnless { it.isEmpty } ?: return
        inputInventory.setItem(WIRE_SNIPS_INPUT_SLOT_INDEX, ItemStack.EMPTY)
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
    }

    private fun ItemStack.utilityCableDescriptor(): UtilityCableDescriptor? {
        return Eln.sixNodeItem.getDescriptor(this) as? UtilityCableDescriptor
            ?: GenericItemBlockUsingDamageDescriptor.getDescriptor(this, UtilityCableDescriptor::class.java) as? UtilityCableDescriptor
    }
}

class WireSnipsGui(player: Player) : GuiContainerEln(WireSnipsContainer(player)) {
    private val snipsContainer: WireSnipsContainer
        get() = menu as WireSnipsContainer

    private lateinit var cutButton: GuiButtonEln
    private lateinit var lengthField: GuiTextFieldEln

    override fun newHelper(): GuiHelperContainer {
        return GuiHelperContainer(this, 176, 216, 8, 134)
    }

    override fun initGui() {
        super.initGui()
        lengthField = newGuiTextField(8, 72, 58).apply {
            text = "32"
            setComment(0, tr("Cut length in whole meters"))
        }
        cutButton = newGuiButton(72, 70, 96, tr("Cut Wire"))
    }

    override fun guiObjectEvent(obj: IGuiObject) {
        when (obj) {
            cutButton -> {
                val length = lengthField.text.toDoubleOrNull() ?: return
                Minecraft.getInstance().gameMode?.handleInventoryButtonClick(snipsContainer.containerId, snipsContainer.encodeCutAction(length))
            }
        }
    }

    override fun preDraw(f: Float, mouseX: Int, mouseY: Int) {
        super.preDraw(f, mouseX, mouseY)
        val descriptor = snipsContainer.selectedWireDescriptor()
        cutButton.enabled = descriptor != null && (lengthField.text.toDoubleOrNull() ?: 0.0) > 0.0
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        val stack = snipsContainer.selectedWireStack()
        val descriptor = snipsContainer.selectedWireDescriptor()
        drawString(8, 6, tr("Wire Snips"))
        drawString(8, 20, tr("Input Wire"))
        drawString(8, 62, tr("Length (m)"))
        if (stack.isNothing()) {
            drawString(8, 104, tr("Insert a wire coil to cut"))
            return
        }
        if (descriptor == null) {
            drawString(8, 104, tr("Input must be a utility wire"))
            return
        }
        drawString(8, 104, stack.hoverName.string)
        drawString(8, 116, tr("Remaining: %1$ m", Utils.plotValue(descriptor.getRemainingLengthMeters(stack))))
    }
}
