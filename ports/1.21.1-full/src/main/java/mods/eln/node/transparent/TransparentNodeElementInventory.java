package mods.eln.node.transparent;

import mods.eln.misc.INBTTReady;
import mods.eln.misc.Utils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TransparentNodeElementInventory implements WorldlyContainer, INBTTReady {
    protected TransparentNodeElementRender transparentNodeRender = null;
    protected TransparentNodeElement transparentNodeElement = null;

    int stackLimit;

    public TransparentNodeElementInventory(int size, int stackLimit, TransparentNodeElementRender TransparentnodeRender) {
        inv = new ItemStack[size];
        Arrays.fill(inv, ItemStack.EMPTY);
        this.stackLimit = stackLimit;
        this.transparentNodeRender = TransparentnodeRender;
    }

    public TransparentNodeElementInventory(int size, int stackLimit, TransparentNodeElement TransparentNodeElement) {
        inv = new ItemStack[size];
        Arrays.fill(inv, ItemStack.EMPTY);
        this.stackLimit = stackLimit;
        this.transparentNodeElement = TransparentNodeElement;
    }

    private ItemStack[] inv;

    private ItemStack[] getInv() {
        return inv;
    }

    @Override
    public int getContainerSize() {

        return getInv().length;
    }

    @NotNull
    @Override
    public ItemStack getItem(int slot) {
        if (slot >= getInv().length) return ItemStack.EMPTY;
        ItemStack stack = getInv()[slot];
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public ItemStack removeItem(int slot, int amt) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() <= amt) {
            getInv()[slot] = ItemStack.EMPTY;
            return stack;
        }

        ItemStack result = stack.split(amt);
        if (stack.isEmpty()) {
            getInv()[slot] = ItemStack.EMPTY;
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        getInv()[slot] = ItemStack.EMPTY;
        return stack;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            getInv()[slot] = ItemStack.EMPTY;
            return;
        }

        getInv()[slot] = stack;
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
    }

    
    public String getName() {
        return "tco.TransparentNodeInventory";
    }

    @Override
    public int getMaxStackSize() {
        return stackLimit;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : getInv()) {
            if (stack != null && !stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void startOpen(Player player) {

    }

    @Override
    public void stopOpen(Player player) {

    }

    @Override
    public void setChanged() {
        if (transparentNodeElement != null && !transparentNodeElement.node.isDestructing()) {
            transparentNodeElement.inventoryChange(this);
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
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        for (int idx = 0; idx < 6; idx++) {
            int[] lol = getSlotsForFace(Direction.values()[idx]);
            for (int hohoho : lol) {
                if (hohoho == i && canPlaceItemThroughFace(i, itemstack, Direction.values()[idx])) {
                    return true;
                }
            }
        }
        return false;
    }

    
    public int getField(int id) {
        return 0;
    }

    
    public void setField(int id, int value) {

    }

    
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clearContent() {
        Arrays.fill(inv, ItemStack.EMPTY);
    }

    
    public boolean hasCustomName() {

        return false;
    }

    
    public Component getDisplayName() {
        return Component.literal("TransparentNodeInventory");
    }

    @Override
    public int[] getSlotsForFace(Direction var1) {
        return new int[]{};
    }

    @Override
    public boolean canPlaceItemThroughFace(int var1, ItemStack var2, Direction var3) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int var1, ItemStack var2, Direction var3) {
        return false;
    }

}
