package mods.eln.sixnode.wirelesssignal;

import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import mods.eln.sixnode.wirelesssignal.WirelessUtils.WirelessSignalSpot;
import mods.eln.sixnode.wirelesssignal.aggregator.BiggerAggregator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

public class WirelessSignalAnalyserItemDescriptor extends GenericItemUsingDamageDescriptor {

    public WirelessSignalAnalyserItemDescriptor(String name) {
        super(name);
    }

    @Override
    public InteractionResult onItemUse(ItemStack stack, Player player, Level world, BlockPos pos, InteractionHand hand, net.minecraft.core.Direction side, float vx, float vy, float vz) {
        if (world.isClientSide) return InteractionResult.PASS;
        Utils.sendMessage(player, "-------------------");
        Direction dir = Direction.fromFacing(side);
        Coordinate c = new Coordinate(pos, world);
        c.move(dir);

        WirelessSignalSpot spot = WirelessUtils.buildSpot(c, null, 0);
        HashMap<String, HashSet<IWirelessSignalTx>> txSet = new HashMap<String, HashSet<IWirelessSignalTx>>();
        HashMap<IWirelessSignalTx, Double> txStrength = new HashMap<IWirelessSignalTx, Double>();
        WirelessUtils.getTx(spot, txSet, txStrength);

        BiggerAggregator aggregator = new BiggerAggregator();

        for (Entry<String, HashSet<IWirelessSignalTx>> entrySet : txSet.entrySet()) {
            HashSet<IWirelessSignalTx> set = entrySet.getValue();
            double strength = 100000;
            for (IWirelessSignalTx oneTx : set) {
                double temp = txStrength.get(oneTx);
                if (temp < strength) strength = temp;
            }
            Utils.sendMessage(player, entrySet.getKey() + " Strength=" + String.format("%2.1f", strength) + " Value=" + String.format("%3.0f", aggregator.aggregate(set) * 100) + "%");
        }

        if (txSet.isEmpty()) {
            Utils.sendMessage(player, "No wireless signal in area!");
        }
        /*ArrayList<WirelessSignalInfo> list = WirelessSignalRxProcess.getTxList(c);
		int idx = 0;
		for (WirelessSignalInfo e : list) {
			Utils.sendMessage(player, e.tx.getChannel() + " Strength=" + String.format("%2.1f", e.power) + " Value=" + String.format("%2.1fV", e.tx.getValue() * Cable.SVU));
			idx++;
		}
		if (list.size() == 0) {
			Utils.sendMessage(player, "No wireless signal in area!");
		}*/
        return InteractionResult.PASS;
    }
}
