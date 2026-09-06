package mods.eln.node.transparent;

import mods.eln.Eln;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.FakeSideInventory;
import mods.eln.misc.LRDU;
import mods.eln.node.Node;
import mods.eln.node.NodeBlockEntity;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;


public class TransparentNodeEntity extends NodeBlockEntity implements ISidedInventory { // boolean[] syncronizedSideEnable = new boolean[6];
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

    public Container newContainer(Direction side, EntityPlayer player) {
        TransparentNode n = (TransparentNode) getNode();
        if (n == null) return null;
        return n.newContainer(side, player);
    }

    public GuiScreen newGuiDraw(Direction side, EntityPlayer player) {
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

    public int getDamageValue(World world, BlockPos pos) {
        if (world.isRemote) {
            return elementRenderId;
        }
        return 0;
    }

    @Override
    public void tileEntityNeighborSpawn() {

        if (elementRender != null) elementRender.notifyNeighborSpawn();
    }

    public void addCollisionBoxesToList(AxisAlignedBB axisAlignedBB, List<AxisAlignedBB> list, Coordinate blockCoord) {
        TransparentNodeDescriptor desc = null;
        if (world.isRemote) {
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
            AxisAlignedBB bb = new AxisAlignedBB(pos);
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
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return getSidedInventory() != null;
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            ISidedInventory inventory = getSidedInventory();
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
    ISidedInventory getSidedInventory() {
        if (world.isRemote) {
            if (elementRender == null) return null;
            IInventory i = elementRender.getInventory();
            if (i instanceof ISidedInventory) {
                return (ISidedInventory) i;
            }
        } else {
            Node node = getNode();
            if (node instanceof TransparentNode) {
                TransparentNode tn = (TransparentNode) node;
                IInventory i = tn.getInventory(null);
                if (i instanceof ISidedInventory) {
                    return (ISidedInventory) i;
                }
            }
        }
        return null;
    }

    @Override
    public int getSizeInventory() {
        ISidedInventory inv = getSidedInventory();
        return inv == null ? 0 : inv.getSizeInventory();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int var1) {
        ISidedInventory inv = getSidedInventory();
        return inv == null ? ItemStack.EMPTY : inv.getStackInSlot(var1);
    }

    @NotNull
    @Override
    public ItemStack decrStackSize(int var1, int var2) {
        ISidedInventory inv = getSidedInventory();
        return inv == null ? ItemStack.EMPTY : inv.decrStackSize(var1, var2);
    }

    @NotNull
    @Override
    public ItemStack removeStackFromSlot(int var1) {
        ISidedInventory inv = getSidedInventory();
        return inv == null ? ItemStack.EMPTY : inv.removeStackFromSlot(var1);
    }

    @Override
    public void setInventorySlotContents(int var1, @NotNull ItemStack var2) {
        ISidedInventory inv = getSidedInventory();
        if (inv != null) inv.setInventorySlotContents(var1, var2);
    }

    @NotNull
    @Override
    public String getName() {
        ISidedInventory inv = getSidedInventory();
        return inv == null ? "None" : inv.getName();
    }

    @Override
    public boolean hasCustomName() {
        ISidedInventory inv = getSidedInventory();
        return inv != null && inv.hasCustomName();
    }

    @Override
    public int getInventoryStackLimit() {
        ISidedInventory inv = getSidedInventory();
        return inv == null ? 0 : inv.getInventoryStackLimit();
    }

    @Override
    public boolean isEmpty() {
        ISidedInventory inv = getSidedInventory();
        return inv == null || inv.isEmpty();
    }

    @Override
    public boolean isUsableByPlayer(@NotNull EntityPlayer player) {
        ISidedInventory inv = getSidedInventory();
        return inv != null && inv.isUsableByPlayer(player);
    }

    @Override
    public void openInventory(EntityPlayer player) {
        ISidedInventory inv = getSidedInventory();
        if (inv != null) inv.openInventory(player);
    }

    @Override
    public void closeInventory(EntityPlayer player) {
        ISidedInventory inv = getSidedInventory();
        if (inv != null) inv.closeInventory(player);
    }

    @Override
    public boolean isItemValidForSlot(int var1, ItemStack stack) {
        ISidedInventory inv = getSidedInventory();
        return inv != null && inv.isItemValidForSlot(var1, stack);
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
    public int[] getSlotsForFace(@NotNull EnumFacing facing) {
        ISidedInventory inv = getSidedInventory();
        return inv == null ? new int[0] : inv.getSlotsForFace(facing);
    }

    @Override
    public boolean canInsertItem(int var1, @NotNull ItemStack stack, @NotNull EnumFacing facing) {
        ISidedInventory inv = getSidedInventory();
        return inv != null && inv.canInsertItem(var1, stack, facing);
    }

    @Override
    public boolean canExtractItem(int var1, @NotNull ItemStack stack, @NotNull EnumFacing facing) {
        ISidedInventory inv = getSidedInventory();
        return inv != null && inv.canExtractItem(var1, stack, facing);
    }
}
