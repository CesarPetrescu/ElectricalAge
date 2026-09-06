package mods.eln.misc;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public class FakeSideInventory implements WorldlyContainer {

    static private final FakeSideInventory instance = new FakeSideInventory();

    public static FakeSideInventory getInstance() {
        return instance;
    }

    @Override
    public int getSizeInventory() {
        return 0;
    }

    @Override
    public ItemStack getStackInSlot(int var1) {
        return null;
    }

    @Override
    public ItemStack decrStackSize(int var1, int var2) {
        return null;
    }

    @Override
    public ItemStack removeStackFromSlot(int var1) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int var1, ItemStack var2) {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String getName() {
        return "FakeSideInventory";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return null;
    }

    @Override
    public int getInventoryStackLimit() {
        return 0;
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean isUsableByPlayer(Player player) {
        return false;
    }

    @Override
    public void openInventory(Player var1) {

    }

    @Override
    public void closeInventory(Player var1) {

    }

    @Override
    public boolean isItemValidForSlot(int var1, ItemStack var2) {
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

    }

    @Override
    public int[] getSlotsForFace(Direction var1) {
        return new int[]{};
    }

    @Override
    public boolean canInsertItem(int var1, ItemStack var2, Direction var3) {
        return false;
    }

    @Override
    public boolean canExtractItem(int var1, ItemStack var2, Direction var3) {
        return false;
    }
}
