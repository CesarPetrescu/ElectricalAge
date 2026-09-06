package mods.eln.node.six;

import mods.eln.misc.INBTTReady;
import mods.eln.misc.Utils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class SixNodeElementInventory implements Container, INBTTReady {
    SixNodeElementRender sixnodeRender = null;
    SixNodeElement sixNodeElement = null;

    int stackLimit;

    public SixNodeElementInventory(int size, int stackLimit, SixNodeElementRender sixnodeRender) {
        inv = new ItemStack[size];
        Arrays.fill(inv, ItemStack.EMPTY);
        this.stackLimit = stackLimit;
        this.sixnodeRender = sixnodeRender;
    }

    public SixNodeElementInventory(int size, int stackLimit, SixNodeElement sixNodeElement) {
        inv = new ItemStack[size];
        Arrays.fill(inv, ItemStack.EMPTY);
        this.stackLimit = stackLimit;
        this.sixNodeElement = sixNodeElement;
    }


    private ItemStack[] inv;

    private ItemStack[] getInv() {
        return inv;
    }

    @Override
    public int getSizeInventory() {
        return getInv().length;
    }


    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot >= getInv().length || getInv()[slot] == null) return ItemStack.EMPTY;
        return getInv()[slot];
    }


    @NotNull
    @Override
    public ItemStack decrStackSize(int slot, int amt) {
        ItemStack stack = getStackInSlot(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() <= amt) {
            getInv()[slot] = ItemStack.EMPTY;
            return stack;
        }

        ItemStack result = stack.splitStack(amt);
        if (stack.isEmpty()) {
            getInv()[slot] = ItemStack.EMPTY;
        }
        return result;
    }

    @NotNull
    @Override
    public ItemStack removeStackFromSlot(int slot) {
        ItemStack stack = getStackInSlot(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        getInv()[slot] = ItemStack.EMPTY;
        return stack;
    }


    @Override
    public void setInventorySlotContents(int slot, @NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            getInv()[slot] = ItemStack.EMPTY;
            return;
        }

        getInv()[slot] = stack;
        int stackLimit = getInventoryStackLimit();
        if (stack.getCount() > stackLimit) {
            stack.setCount(stackLimit);
        }
    }


    @NotNull
    @Override
    public String getName() {
        return "tco.SixNodeInventory";
    }


    @Override
    public int getInventoryStackLimit() {
        return stackLimit;
    }

    @Override
    public boolean isUsableByPlayer(@NotNull Player player) {
        return true;
    }

    @Override
    public void openInventory(@NotNull Player player) {

    }

    @Override
    public void closeInventory(@NotNull Player player) {

    }


    @Override
    public void markDirty() {
        if (sixNodeElement != null && !sixNodeElement.sixNode.isDestructing()) {
            sixNodeElement.inventoryChanged();
        }
    }

    @Override
    public void readFromNBT(CompoundTag nbt, String str) {

        Utils.readFromNBT(nbt, str, this);
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt, String str) {

        return Utils.writeToNBT(nbt, str, this);
    }


    @Override
    public boolean isItemValidForSlot(int i, @NotNull ItemStack itemstack) {
        return false;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {

    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        Arrays.fill(inv, ItemStack.EMPTY);
    }

    @Override
    public boolean hasCustomName() {

        return false;
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return new TextComponentString("SixNodeInventory");
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : inv) {
            if (itemStack != null && !itemStack.isEmpty() && itemStack.getCount() > 0) return false;
        }
        return true;
    }
}
