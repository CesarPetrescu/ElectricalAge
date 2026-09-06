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
    public int getContainerSize() {
        return 0;
    }

    @Override
    public ItemStack getItem(int var1) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int var1, int var2) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int var1) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int var1, ItemStack var2) {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    
    public String getName() {
        return "FakeSideInventory";
    }

    
    public boolean hasCustomName() {
        return false;
    }

    
    public Component getDisplayName() {
        return Component.literal(getName());
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void startOpen(Player var1) {

    }

    @Override
    public void stopOpen(Player var1) {

    }

    @Override
    public boolean canPlaceItem(int var1, ItemStack var2) {
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
