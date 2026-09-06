package mods.eln.node.transparent;

import mods.eln.Eln;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.Node;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ThermalLoad;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class TransparentNode extends Node {

    public TransparentNodeElement element;
    public int elementId;
    public ServerPlayer removedByPlayer;

    @Override
    public boolean nodeAutoSave() {

        return false;
    }

    @Override
    public void onNeighborBlockChange() {
        super.onNeighborBlockChange();
        element.onNeighborBlockChange();
    }

    public void readFromNBT(CompoundTag nbt) {
        super.readFromNBT(nbt.getCompoundTag("node"));

        elementId = nbt.getShort("eid");
        try {
            TransparentNodeDescriptor descriptor = Eln.transparentNodeItem.getDescriptor(elementId);
            element = (TransparentNodeElement) descriptor.ElementClass.getConstructor(TransparentNode.class, TransparentNodeDescriptor.class).newInstance(this, descriptor);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        element.readFromNBT(nbt.getCompoundTag("element"));

    }

    public CompoundTag writeToNBT(CompoundTag nbt) {
        super.writeToNBT(Utils.newNbtTagCompund(nbt, "node"));

        nbt.setShort("eid", (short) elementId);

        return element.writeToNBT(Utils.newNbtTagCompund(nbt, "element"));

    }

    @Override
    public void onBreakBlock() {

        element.onBreakElement();
        super.onBreakBlock();
    }

    @Override
    public ElectricalLoad getElectricalLoad(Direction side, LRDU lrdu) {
        return element.getElectricalLoad(side, lrdu);
    }

    @Override
    public ThermalLoad getThermalLoad(Direction side, LRDU lrdu) {
        return element.getThermalLoad(side, lrdu);
    }

    @Override
    public int getSideConnectionMask(Direction side, LRDU lrdu) {
        return element.getConnectionMask(side, lrdu);
    }

    @Override
    public String multiMeterString(Direction side) {
        return element.multiMeterString(side);
    }

    @Override
    public String thermoMeterString(Direction side) {
        return element.thermoMeterString(side);
    }

    public IFluidHandler getFluidHandler() {
        return element.getFluidHandler();
    }

    @Override
    public void publishSerialize(DataOutputStream stream) {

        super.publishSerialize(stream);

        try {
            stream.writeShort(this.elementId);
            element.networkSerialize(stream);
        } catch (IOException e) {

            e.printStackTrace();
        }

    }

    public enum FrontType {
        BlockSide, PlayerView, PlayerViewHorizontal, BlockSideInv
    }

    ;

    @Override
    public void initializeFromThat(Direction side, LivingEntity entityLiving, ItemStack itemStack) {
        try {
            TransparentNodeDescriptor descriptor = Eln.transparentNodeItem.getDescriptor(itemStack);

            int metadata = itemStack.getItemDamage();
            elementId = metadata;
            element = (TransparentNodeElement) descriptor.ElementClass.getConstructor(TransparentNode.class, TransparentNodeDescriptor.class).newInstance(this, descriptor);
            element.initializeFromThat(side, entityLiving, itemStack.getTagCompound());
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void initializeFromNBT() {
        element.initialize();
    }

    public boolean onBlockActivated(Player entityPlayer, Direction side, float vx, float vy, float vz) {
        if (element.onBlockActivated(entityPlayer, side, vx, vy, vz)) return true;
        return super.onBlockActivated(entityPlayer, side, vx, vy, vz);
    }

    @Override
    public boolean hasGui(Direction side) {
        if (element == null) return false;
        return element.hasGui();
    }

    public Container getInventory(Direction side) {
        if (element == null) return null;
        return element.getInventory();
    }

    public AbstractContainerMenu newContainer(Direction side, Player player) {
        if (element == null) return null;
        return element.newContainer(side, player);
    }

    @Override
    public int getBlockMetadata() {
        return element.transparentNodeDescriptor.tileEntityMetaTag.meta;
    }

    @Override
    public void networkUnserialize(DataInputStream stream, ServerPlayer player) {
        super.networkUnserialize(stream, player);

        Direction side;
        try {
            if (elementId == stream.readShort()) {
                element.networkUnserialize(stream, player);
            } else {
                Utils.println("Transparent node unserialize miss");
            }
        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    @Override
    public void connectJob() {
        super.connectJob();
        element.connectJob();
    }

    @Override
    public void disconnectJob() {
        super.disconnectJob();
        element.disconnectJob();
    }

    @Override
    public void checkCanStay(boolean onCreate) {

        super.checkCanStay(onCreate);
        element.checkCanStay(onCreate);
    }

    public void dropElement(ServerPlayer entityPlayer) {
        if (element != null)
            if (Utils.mustDropItem(entityPlayer))
                dropItem(element.getDropItemStack());
    }

    @Override
    public String getNodeUuid() {
        return "t";
    }

    @Override
    public void unload() {
        super.unload();
        if (element != null)
            element.unload();

    }

}
