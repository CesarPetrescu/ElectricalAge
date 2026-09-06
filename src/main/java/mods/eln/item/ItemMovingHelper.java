package mods.eln.item;

import mods.eln.misc.McBridge;
import mods.eln.misc.Utils;
import mods.eln.sixnode.electricalcable.IUtilityCableInventory;
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;

public abstract class ItemMovingHelper {
    public abstract boolean acceptsStack(ItemStack stack);
    public abstract ItemStack newStackOfSize(int items);

    public void move(Inventory src, Container dst, int dstSlot, int desired) {
        boolean dstChanged = false;
        if(Utils.isCreative((ServerPlayer) src.player)) {
            if(desired == 0) {
                dst.setItem(dstSlot, ItemStack.EMPTY);
            } else {
                dst.setItem(dstSlot, newStackOfSize(desired));
            }
            dst.setChanged();
            return;
        }
        int now = 0;
        ItemStack stack = dst.getItem(dstSlot);
        if(!McBridge.isNothing(stack)) {
            now = stack.getCount();
        }
        Utils.println(String.format("IMH.m: now %d, desired %d", now, desired));
        if(now < desired) {
            int diff = desired - now;
            for(int idx = 0; idx < src.getContainerSize(); idx++) {
                ItemStack invStack = src.getItem(idx);
                if(McBridge.isNothing(invStack)) continue;
                if(!acceptsStack(invStack)) continue;
                if (Utils.getItemObject(invStack) instanceof UtilityCableDescriptor) {
                    if (IUtilityCableInventory.trimCable(invStack, dst, dstSlot)) {
                        if (invStack.getCount() == 0) src.setItem(idx, ItemStack.EMPTY);
                        syncItemInSlot(src, idx);
                        diff -= Math.min(invStack.getCount(), diff);
                        Utils.println(String.format("IMH.m: moved %d into node", (desired - now) - diff));
                        return; // trimCable automatically marks the destination inventory as dirty, if necessary
                    } else continue;
                }
                int move = Math.min(invStack.getCount(), diff);
                diff -= move;
                invStack.shrink(move);
                if(invStack.getCount() == 0) {
                    invStack = null;
                }
                src.setItem(idx, invStack);
                // Grissess: We need to send this immediately to sync with the client
                syncItemInSlot(src, idx);
                if(diff <= 0) break;
            }
            int moved = (desired - now) - diff;
            Utils.println(String.format("IMH.m: moved %d into node", moved));
            if(moved > 0) {
                dst.setItem(dstSlot, newStackOfSize(now + moved));
                dstChanged = true;
            }
        } else {
            int diff = now - desired;
            Utils.println(String.format("IMH.m: moving %d items", diff));
            if(diff > 0) {
                if (src.add(newStackOfSize(diff))) {
                    if(desired == 0) {
                        dst.setItem(dstSlot, ItemStack.EMPTY);
                    } else {
                        dst.setItem(dstSlot, newStackOfSize(desired));
                    }
                    dstChanged = true;
                    Utils.println("IMH.m: move succeeded");
                } else {
                    Utils.println("IMH.m: move failed!");
                }
            }
            // Grissess: Since we can't tell how the inventory might have been changed
            // due to addItemStackToInventory, we have to take the conservative
            // approach and assume every slot might have changed.
            syncEntireInventory(src.player);
        }

        if (dstChanged) {
            dst.setChanged();
        }
    }

    /**
     * 1.7.10 hand-built a slot packet; the player's container menu broadcasts its changed slots
     * to the client each tick (and now, on demand).
     */
    public static void syncItemInSlot(Inventory inv, int slot) {
        inv.setChanged();
        ((ServerPlayer) inv.player).containerMenu.broadcastChanges();
    }

    public static void syncEntireInventory(Player player) {
        player.getInventory().setChanged();
        ((ServerPlayer) player).containerMenu.broadcastChanges();
    }

}
