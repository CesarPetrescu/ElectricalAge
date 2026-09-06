package mods.eln.sixnode.thermalsensor;

import mods.eln.Eln;
import mods.eln.i18n.I18N;
import mods.eln.item.ConfigCopyToolDescriptor;
import mods.eln.item.IConfigurable;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.AutoAcceptInventoryProxy;
import mods.eln.node.NodeBase;
import mods.eln.node.six.SixNode;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElement;
import mods.eln.node.six.SixNodeElementInventory;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess;
import mods.eln.sim.nbt.NbtElectricalLoad;
import mods.eln.sim.nbt.NbtThermalLoad;
import mods.eln.sixnode.currentcable.CurrentCableDescriptor;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import mods.eln.sixnode.electricaldatalogger.DataLogs;
import mods.eln.sixnode.thermalcable.ThermalCableDescriptor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ThermalSensorElement extends SixNodeElement implements IConfigurable {

    public ThermalSensorDescriptor descriptor;
    public NbtThermalLoad thermalLoad = new NbtThermalLoad("thermalLoad");
    public NbtElectricalLoad outputGate = new NbtElectricalLoad("outputGate");

    public NbtElectricalGateOutputProcess outputGateProcess = new NbtElectricalGateOutputProcess("outputGateProcess", outputGate);
    public ThermalSensorProcess slowProcess = new ThermalSensorProcess(this);

    AutoAcceptInventoryProxy inventory;

    static final byte powerType = 0, temperatureType = 1;
    int typeOfSensor = temperatureType;
    float lowValue = 0, highValue = (float) Eln.SVU;

    public static final byte setTypeOfSensorId = 1;
    public static final byte setValueId = 2;

    public ThermalSensorElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
        super(sixNode, side, descriptor);
        thermalLoadList.add(thermalLoad);
        electricalLoadList.add(outputGate);
        electricalComponentList.add(outputGateProcess);
        slowProcessList.add(slowProcess);

        this.descriptor = (ThermalSensorDescriptor) descriptor;

        if (this.descriptor.temperatureOnly) {
            // Java reports the Kotlin vararg of descriptor classes as an unchecked array creation here.
            @SuppressWarnings("unchecked")
            AutoAcceptInventoryProxy configuredInventory = (new AutoAcceptInventoryProxy(new SixNodeElementInventory(1, 64, this)))
                .acceptIfEmpty(0, ThermalCableDescriptor.class, ElectricalCableDescriptor.class, CurrentCableDescriptor.class);
            inventory = configuredInventory;
        } else {
            // Java reports the Kotlin vararg of descriptor classes as an unchecked array creation here.
            @SuppressWarnings("unchecked")
            AutoAcceptInventoryProxy configuredInventory = (new AutoAcceptInventoryProxy(new SixNodeElementInventory(1, 64, this)))
                .acceptIfEmpty(0, ThermalCableDescriptor.class);
            inventory = configuredInventory;
        }
    }

    public Container getInventory() {
        if (inventory != null)
            return inventory.getInventory();
        else
            return null;
    }

    public static boolean canBePlacedOnSide(Direction side, int type) {
        return true;
    }

    @Override
    public void readFromNBT(@NotNull CompoundTag nbt) {
        super.readFromNBT(nbt);
        byte value = nbt.getByte("front");
        front = LRDU.fromInt((value >> 0) & 0x3);
        typeOfSensor = nbt.getByte("typeOfSensor");
        lowValue = nbt.getFloat("lowValue");
        highValue = nbt.getFloat("highValue");
    }

    @Override
    public void writeToNBT(CompoundTag nbt) {
        super.writeToNBT(nbt);
        nbt.putByte("front", (byte) ((front.toInt() << 0)));
        nbt.putByte("typeOfSensor", (byte) typeOfSensor);
        nbt.putFloat("lowValue", lowValue);
        nbt.putFloat("highValue", highValue);
    }

    @Override
    public ElectricalLoad getElectricalLoad(LRDU lrdu, int mask) {
        if (front == lrdu) return outputGate;

        return null;
    }

    @Nullable
    @Override
    public ThermalLoad getThermalLoad(@NotNull LRDU lrdu, int mask) {
        if (!descriptor.temperatureOnly) {
            if (!getInventory().getItem(ThermalSensorContainer.cableSlotId).isEmpty()) {
                if (front.left() == lrdu) return thermalLoad;
                if (front.right() == lrdu) return thermalLoad;
            }
        } else {
            if (front.inverse() == lrdu) return thermalLoad;
        }
        return null;
    }

    @Override
    public int getConnectionMask(LRDU lrdu) {
        if (!descriptor.temperatureOnly) {
            if (!getInventory().getItem(ThermalSensorContainer.cableSlotId).isEmpty()) {
                if (front.left() == lrdu) return NodeBase.maskThermal;
                if (front.right() == lrdu) return NodeBase.maskThermal;
            }
            if (front == lrdu) return NodeBase.maskElectricalOutputGate;
        } else {
            if (isItemThermalCable()) {
                if (front.inverse() == lrdu) return NodeBase.maskThermal;
            } else if (isItemElectricalCable()) {
                if (front.inverse() == lrdu) return NodeBase.maskElectricalAll;
            }
            if (front == lrdu) return NodeBase.maskElectricalOutputGate;
        }
        return 0;
    }

    @Override
    public String multiMeterString() {
        return "";
    }

    @NotNull
    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Output voltage"), Utils.plotVolt("", outputGate.getVoltage()));
        if (Eln.config.getBooleanOrElse("ui.waila.easyMode", false)) {
            switch (typeOfSensor) {
                case temperatureType:
                    info.put(I18N.tr("Measured temperature"), plotAmbientCelsius("", thermalLoad.getTemperature()));
                    break;

                case powerType:
                    info.put(I18N.tr("Measured thermal power"), Utils.plotPower("", thermalLoad.getPower()));
                    break;
            }
        }
        return info;
    }

    @NotNull
    @Override
    public String thermoMeterString() {
        return plotAmbientCelsius("T :", thermalLoad.temperatureCelsius);
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            stream.writeByte((front.toInt() << 4) + typeOfSensor);
            stream.writeFloat(lowValue);
            stream.writeFloat(highValue);
            Utils.serialiseItemStack(stream, getInventory().getItem(ThermalSensorContainer.cableSlotId));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize() {
        Eln.instance.signalCableDescriptor.applyTo(outputGate);
        computeElectricalLoad();
    }

    @Override
    public void inventoryChanged() {
        sixNode.disconnect();
        computeElectricalLoad();
        sixNode.connect();
    }

    public void computeElectricalLoad() {
        ItemStack cable = getInventory().getItem(ThermalSensorContainer.cableSlotId);

        SixNodeDescriptor descriptor = Eln.sixNodeItem.getDescriptor(cable);
        if (descriptor == null) return;
        if (descriptor instanceof ThermalCableDescriptor) {
            ThermalCableDescriptor cableDescriptor = (ThermalCableDescriptor) descriptor;
            cableDescriptor.setThermalLoad(thermalLoad);
            thermalLoad.setAsFast();
        } else if (descriptor instanceof ElectricalCableDescriptor) {
            ElectricalCableDescriptor cableDescriptor = (ElectricalCableDescriptor) descriptor;
            cableDescriptor.applyTo(thermalLoad);
            thermalLoad.Rp = 1000000000.0;
            thermalLoad.setAsSlow();
        } else if (descriptor instanceof CurrentCableDescriptor) {
            CurrentCableDescriptor cableDescriptor = (CurrentCableDescriptor) descriptor;
            cableDescriptor.applyTo(thermalLoad);
            thermalLoad.Rp = 1000000000.0;
            thermalLoad.setAsSlow();
        } else {
            thermalLoad.setHighImpedance();
        }
    }

    boolean isItemThermalCable() {
        SixNodeDescriptor descriptor = Eln.sixNodeItem.getDescriptor(getInventory().getItem(ThermalSensorContainer.cableSlotId));
        return descriptor instanceof ThermalCableDescriptor;
    }

    boolean isItemElectricalCable() {
        SixNodeDescriptor descriptor = Eln.sixNodeItem.getDescriptor(getInventory().getItem(ThermalSensorContainer.cableSlotId));
        return descriptor instanceof ElectricalCableDescriptor || descriptor instanceof CurrentCableDescriptor;
    }

    @Override
    public boolean onBlockActivated(Player entityPlayer, Direction side, float vx, float vy, float vz) {
        if (onBlockActivatedRotate(entityPlayer)) return true;
        ItemStack currentItemStack = entityPlayer.getMainHandItem();

        if (Eln.multiMeterElement.checkSameItemStack(currentItemStack)) {
            return false;
        }
        if (Eln.thermometerElement.checkSameItemStack(currentItemStack)) {
            return false;
        }
        if (Eln.allMeterElement.checkSameItemStack(currentItemStack)) {
            return false;
        }
        return inventory.take(currentItemStack, this, false, true);
    }

    @Override
    public void networkUnserialize(DataInputStream stream) {
        super.networkUnserialize(stream);
        try {
            switch (stream.readByte()) {
                case setTypeOfSensorId:
                    typeOfSensor = stream.readByte();
                    needPublish();
                    break;
                case setValueId:
                    lowValue = stream.readFloat();
                    highValue = stream.readFloat();
                    if (lowValue == highValue) highValue += 0.0001;
                    needPublish();
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu newContainer(@NotNull Direction side, @NotNull Player player) {
        return new ThermalSensorContainer(player, inventory.getInventory(), descriptor.temperatureOnly);
    }

    @Override
    public void readConfigTool(CompoundTag compound, Player invoker) {
        if(compound.contains("min"))
            lowValue = compound.getFloat("min");
        if(compound.contains("max"))
            highValue = compound.getFloat("max");
        if(compound.contains("unit")) {
            switch(compound.getByte("unit")) {
                case DataLogs.powerType:
                    typeOfSensor = powerType;
                    break;
                case DataLogs.celsiusType:
                    typeOfSensor = temperatureType;
                    break;
            }
        }
        ConfigCopyToolDescriptor.readCableType(compound, getInventory(), 0, invoker);
        reconnect();
    }

    @Override
    public void writeConfigTool(CompoundTag compound, Player invoker) {
        compound.putFloat("min", lowValue);
        compound.putFloat("max", highValue);
        switch(typeOfSensor) {
            case powerType:
                compound.putByte("unit", DataLogs.powerType);
                break;
            case temperatureType:
                compound.putByte("unit", DataLogs.celsiusType);
                break;
        }
        ConfigCopyToolDescriptor.writeCableType(compound, getInventory().getItem(0));
    }
}
