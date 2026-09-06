package mods.eln.node.transparent;

import mods.eln.Eln;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.FakeSideInventory;
import mods.eln.misc.LRDU;
import mods.eln.node.Node;
import mods.eln.node.NodeBlockEntity;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;


public class TransparentNodeEntity extends NodeBlockEntity implements WorldlyContainer { // boolean[] syncronizedSideEnable = new boolean[6];
    TransparentNodeElementRender elementRender = null;
    private short elementRenderId;


    @Override
    public CableRenderDescriptor getCableRender(Direction side, LRDU lrdu) {
        if (elementRender == null) return null;
        return elementRender.getCableRender(side, lrdu);
    }

    @Override
    public void serverPublishUnserialize(DataInputStream stream) {
        super.serverPublishUnserialize(stream);

        try {
            Short id = stream.readShort();
            if (id == 0) {
                elementRenderId = (byte) 0;
                elementRender = null;
            } else {
                if (id != elementRenderId) {
                    elementRenderId = id;
                    TransparentNodeDescriptor descriptor = Eln.transparentNodeItem.getDescriptor(id);
                    elementRender = (TransparentNodeElementRender) descriptor.RenderClass.getConstructor(TransparentNodeEntity.class, TransparentNodeDescriptor.class).newInstance(this, descriptor);
                }
                elementRender.networkUnserialize(stream);
            }

        } catch (IOException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }

    }

    public AbstractContainerMenu newContainer(Direction side, Player player) {
        TransparentNode n = (TransparentNode) getNode();
        if (n == null) return null;
        return n.newContainer(side, player);
    }

    public Screen newGuiDraw(Direction side, Player player) {
        return elementRender.newGuiDraw(side, player);
    }

    public void preparePacketForServer(DataOutputStream stream) {
        try {
            super.preparePacketForServer(stream);
            stream.writeShort(elementRenderId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendPacketToServer(ByteArrayOutputStream bos) {
        super.sendPacketToServer(bos);
    }

    public boolean cameraDrawOptimisation() {
        if (elementRender == null) return super.cameraDrawOptimisation();
        return elementRender.cameraDrawOptimisation();
    }

    public int getDamageValue(Level world, BlockPos pos) {
        if (world.isClientSide) {
            return elementRenderId;
        }
        return 0;
    }

    @Override
    public void tileEntityNeighborSpawn() {

        if (elementRender != null) elementRender.notifyNeighborSpawn();
    }

    public void addCollisionBoxesToList(AABB axisAlignedBB, List<AABB> list, Coordinate blockCoord) {
        TransparentNodeDescriptor desc = null;
        if (world.isClientSide) {
            desc = elementRender == null ? null : elementRender.transparentNodedescriptor;
        } else {
            TransparentNode node = (TransparentNode) getNode();
            desc = node == null ? null : node.element.transparentNodeDescriptor;
        }
        BlockPos pos;
        if (blockCoord != null) {
            pos = blockCoord.pos;
        } else {
            pos = this.pos;
        }
        if (desc == null) {
            AABB bb = new AABB(pos);
            if (axisAlignedBB.intersects(bb)) list.add(bb);
        } else {
            desc.addCollisionBoxesToList(axisAlignedBB, list, pos);
        }
    }

    public void serverPacketUnserialize(DataInputStream stream) {
        super.serverPacketUnserialize(stream);
        if (elementRender != null)
            elementRender.serverPacketUnserialize(stream);
    }

    @Override
    public String getNodeUuid() {
        return "t";
    }

    @Override
    public void destructor() {
        if (elementRender != null)
            elementRender.destructor();
        super.destructor();
    }

    @Override
    public void clientRefresh(float deltaT) {
        if (elementRender != null) {
            elementRender.refresh(deltaT);
        }
    }

    @Override
    public int isProvidingWeakPower(Direction side) {
        return 0;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable net.minecraft.core.Direction facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return getSidedInventory() != null;
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable net.minecraft.core.Direction facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            WorldlyContainer inventory = getSidedInventory();
            if (inventory != null) {
                if (facing != null) {
                    return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(new SidedInvWrapper(inventory, facing));
                } else {
                    return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(new InvWrapper(inventory));
                }
            }
        }
        return super.getCapability(capability, facing);
    }

    @Nullable
    WorldlyContainer getSidedInventory() {
        if (world.isClientSide) {
            if (elementRender == null) return null;
            Container i = elementRender.getInventory();
            if (i instanceof WorldlyContainer) {
                return (WorldlyContainer) i;
            }
        } else {
            Node node = getNode();
            if (node instanceof TransparentNode) {
                TransparentNode tn = (TransparentNode) node;
                Container i = tn.getInventory(null);
                if (i instanceof WorldlyContainer) {
                    return (WorldlyContainer) i;
                }
            }
        }
        return null;
    }

    @Override
    public int getContainerSize() {
        WorldlyContainer inv = getSidedInventory();
        return inv == null ? 0 : inv.getContainerSize();
    }

    @NotNull
    @Override
    public ItemStack getItem(int var1) {
        WorldlyContainer inv = getSidedInventory();
        return inv == null ? ItemStack.EMPTY : inv.getItem(var1);
    }

    @NotNull
    @Override
    public ItemStack removeItem(int var1, int var2) {
        WorldlyContainer inv = getSidedInventory();
        return inv == null ? ItemStack.EMPTY : inv.removeItem(var1, var2);
    }

    @NotNull
    @Override
    public ItemStack removeItemNoUpdate(int var1) {
        WorldlyContainer inv = getSidedInventory();
        return inv == null ? ItemStack.EMPTY : inv.removeItemNoUpdate(var1);
    }

    @Override
    public void setItem(int var1, @NotNull ItemStack var2) {
        WorldlyContainer inv = getSidedInventory();
        if (inv != null) inv.setItem(var1, var2);
    }

    @NotNull
    @Override
    public String getName() {
        WorldlyContainer inv = getSidedInventory();
        return inv == null ? "None" : inv.getName();
    }

    @Override
    public boolean hasCustomName() {
        WorldlyContainer inv = getSidedInventory();
        return inv != null && inv.hasCustomName();
    }

    @Override
    public int getMaxStackSize() {
        WorldlyContainer inv = getSidedInventory();
        return inv == null ? 0 : inv.getMaxStackSize();
    }

    @Override
    public boolean isEmpty() {
        WorldlyContainer inv = getSidedInventory();
        return inv == null || inv.isEmpty();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        WorldlyContainer inv = getSidedInventory();
        return inv != null && inv.stillValid(player);
    }

    @Override
    public void startOpen(Player player) {
        WorldlyContainer inv = getSidedInventory();
        if (inv != null) inv.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        WorldlyContainer inv = getSidedInventory();
        if (inv != null) inv.stopOpen(player);
    }

    @Override
    public boolean canPlaceItem(int var1, ItemStack stack) {
        WorldlyContainer inv = getSidedInventory();
        return inv != null && inv.canPlaceItem(var1, stack);
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
    public int[] getSlotsForFace(@NotNull net.minecraft.core.Direction facing) {
        WorldlyContainer inv = getSidedInventory();
        return inv == null ? new int[0] : inv.getSlotsForFace(facing);
    }

    @Override
    public boolean canPlaceItemThroughFace(int var1, @NotNull ItemStack stack, @NotNull net.minecraft.core.Direction facing) {
        WorldlyContainer inv = getSidedInventory();
        return inv != null && inv.canPlaceItemThroughFace(var1, stack, facing);
    }

    @Override
    public boolean canTakeItemThroughFace(int var1, @NotNull ItemStack stack, @NotNull net.minecraft.core.Direction facing) {
        WorldlyContainer inv = getSidedInventory();
        return inv != null && inv.canTakeItemThroughFace(var1, stack, facing);
    }
}
