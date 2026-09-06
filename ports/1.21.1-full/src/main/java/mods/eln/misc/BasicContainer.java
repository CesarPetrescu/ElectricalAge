package mods.eln.misc;

import mods.eln.gui.ISlotSkin.SlotSkin;
import mods.eln.gui.SlotWithSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class BasicContainer extends Container {

    protected IInventory inventory;

    public BasicContainer(EntityPlayer player, IInventory inventory, Slot[] slot) {
        this.inventory = inventory;

        for (int i = 0; i < slot.length; i++) {
            addSlotToContainer(slot[i]);
        }

        bindPlayerInventory(player.inventory);
    }

    @Override
    public boolean canInteractWith(@NotNull EntityPlayer player) {
        return inventory.isUsableByPlayer(player);
    }

    protected void bindPlayerInventory(InventoryPlayer inventoryPlayer) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlotToContainer(new SlotWithSkin(inventoryPlayer, j + i * 9 + 9, j * 18, i * 18, SlotSkin.medium));
                // 8 + j * 18, 84 + i * 18));
            }
        }

        for (int i = 0; i < 9; i++) {
            addSlotToContainer(new SlotWithSkin(inventoryPlayer, i, i * 18, 58, SlotSkin.medium));
            // addSlotToContainer(new Slot(inventoryPlayer, i, 8 + i * 18,
            // 142));
        }
    }

    @Override
    protected Slot addSlotToContainer(Slot slot) {
        // slot.xPos = helper.
        return super.addSlotToContainer(slot);
    }

    public ItemStack transferStackInSlot(EntityPlayer player, int slotId) {
        ItemStack movedStack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(slotId);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            movedStack = stack.copy();
            int invSize = inventory.getSizeInventory();
            if (slotId < invSize) {
                this.mergeItemStack(stack, invSize, inventorySlots.size(), true);
            } else {
                if (!this.mergeItemStack(stack, 0, invSize, true)) {
                    if (slotId < invSize + 27) {
                        this.mergeItemStack(stack, invSize + 27, inventorySlots.size(), false);
                    } else {
                        this.mergeItemStack(stack, invSize, invSize + 27, false);
                    }
                }
            }

            if (stack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }

        return movedStack;
    }

    protected boolean mergeItemStack(ItemStack par1ItemStack, int par2, int par3, boolean par4) {
        return super.mergeItemStack(par1ItemStack, par2, par3, par4);
    }

    @Override
    public ItemStack slotClick(int arg0, int arg1, ClickType type, EntityPlayer arg3) {
        if (arg0 >= this.inventorySlots.size()) {
            System.out.println("Damned !!! What happen ?");
            Utils.sendMessage(arg3, "Damn! Sorry, this is a debug");
            Utils.sendMessage(arg3, "message from Electrical age.");
            Utils.sendMessage(arg3, "Could you send me a message about that?");
            Utils.sendMessage(arg3, "Thanks :D");
            return ItemStack.EMPTY;
        }
        return super.slotClick(arg0, arg1, type, arg3);
    }
}
