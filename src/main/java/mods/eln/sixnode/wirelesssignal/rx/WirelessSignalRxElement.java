package mods.eln.sixnode.wirelesssignal.rx;

import mods.eln.i18n.I18N;
import mods.eln.item.IConfigurable;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.six.SixNode;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElement;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.nbt.NbtElectricalGateOutput;
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess;
import mods.eln.sixnode.wirelesssignal.aggregator.BiggerAggregator;
import mods.eln.sixnode.wirelesssignal.aggregator.IWirelessSignalAggregator;
import mods.eln.sixnode.wirelesssignal.aggregator.SmallerAggregator;
import mods.eln.sixnode.wirelesssignal.aggregator.ToogleAggregator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WirelessSignalRxElement extends SixNodeElement implements IConfigurable {

    NbtElectricalGateOutput outputGate = new NbtElectricalGateOutput("outputGate");
    NbtElectricalGateOutputProcess outputGateProcess = new NbtElectricalGateOutputProcess("outputGateProcess", outputGate);

    public String channel = "Default channel";

    WirelessSignalRxProcess slowProcess = new WirelessSignalRxProcess(this);

    WirelessSignalRxDescriptor descriptor;

    ToogleAggregator toogleAggregator;

    boolean connection = false;

    public static final byte setChannelId = 1;
    public static final byte setSelectedAggregator = 2;
    IWirelessSignalAggregator[] aggregators;

    int selectedAggregator = 0;

    public WirelessSignalRxElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
        super(sixNode, side, descriptor);

        this.descriptor = (WirelessSignalRxDescriptor) descriptor;

        electricalLoadList.add(outputGate);
        electricalComponentList.add(outputGateProcess);
        electricalProcessList.add(slowProcess);

        aggregators = new IWirelessSignalAggregator[3];
        aggregators[0] = new BiggerAggregator();
        aggregators[1] = new SmallerAggregator();
        aggregators[2] = toogleAggregator = new ToogleAggregator();
    }

    @Override
    public ElectricalLoad getElectricalLoad(LRDU lrdu, int mask) {
        if (front == lrdu) return outputGate;
        return null;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public ThermalLoad getThermalLoad(@NotNull LRDU lrdu, int mask) {
        return null;
    }

    @Override
    public int getConnectionMask(LRDU lrdu) {
        if (front == lrdu) return NodeBase.maskElectricalOutputGate;
        return 0;
    }

    @Override
    public String multiMeterString() {
        return outputGate.plot("Output gate");
    }

    @NotNull
    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Channel"), (connection ? "\u00A7a" : "\u00A7c") + channel);
        info.put(I18N.tr("Output voltage"), Utils.plotVolt("", outputGate.getVoltage()));
        return info;
    }

    @NotNull
    @Override
    public String thermoMeterString() {
        return "";
    }

    @Override
    public void initialize() {
    }

    void setConnection(boolean connection) {
        if (connection != this.connection) {
            this.connection = connection;
            needPublish();
        }
    }

    @Override
    public void writeToNBT(CompoundTag nbt) {
        super.writeToNBT(nbt);
        nbt.putString("channel", channel);
        nbt.putBoolean("connection", connection);
        nbt.putInt("selectedAggregator", selectedAggregator);
        toogleAggregator.writeToNBT(nbt, "toogleAggregator");
    }

    @Override
    public void readFromNBT(@NotNull CompoundTag nbt) {
        super.readFromNBT(nbt);
        channel = nbt.getString("channel");
        connection = nbt.getBoolean("connection");
        selectedAggregator = nbt.getInt("selectedAggregator");
        toogleAggregator.readFromNBT(nbt, "toogleAggregator");
    }

    @Override
    public Coordinate getCoordinate() {
        return sixNode.coordinate;
    }

    @Override
    public void networkUnserialize(DataInputStream stream) {
        super.networkUnserialize(stream);

        try {
            switch (stream.readByte()) {
                case setChannelId:
                    channel = stream.readUTF();
                    slowProcess.sleepTimer = 0;
                    needPublish();
                    break;

                case setSelectedAggregator:
                    selectedAggregator = stream.readByte();
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

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            stream.writeUTF(channel);
            stream.writeBoolean(connection);
            stream.writeByte(selectedAggregator);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public IWirelessSignalAggregator getAggregator() {
        if (selectedAggregator >= 0 && selectedAggregator < aggregators.length)
            return aggregators[selectedAggregator];
        return null;
    }

    @Override
    public void readConfigTool(CompoundTag compound, Player invoker) {
        if(compound.contains("wirelessChannels")) {
            String newChannel = compound.getList("wirelessChannels", 8).getString(0);
            if(newChannel != null && newChannel != "") {
                channel = newChannel;
                needPublish();
            }
        }
    }

    @Override
    public void writeConfigTool(CompoundTag compound, Player invoker) {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(channel));
        compound.put("wirelessChannels", list);
    }

    //	HashMap<String, ArrayList<IWirelessSignalTx>> wirelessTxInRange = new HashMap<String, ArrayList<IWirelessSignalTx>>();
//	ArrayList<IWirelessSignalSpot> wirelessSpotInRange = new ArrayList<IWirelessSignalSpot>();
}
