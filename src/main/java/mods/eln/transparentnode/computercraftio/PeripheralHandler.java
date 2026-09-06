package mods.eln.transparentnode.computercraftio;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import mods.eln.misc.Coordinate;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeManager;
import mods.eln.simplenode.computerprobe.ComputerProbeNode;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

public class PeripheralHandler implements IPeripheralProvider {

    @Override
    public IPeripheral getPeripheral(Level world, BlockPos pos, Direction side) {
        NodeBase nb = NodeManager.instance.getNodeFromCoordonate(new Coordinate(pos.getX(), pos.getY(), pos.getZ(), world));
    /*	if (nb instanceof TransparentNode) {
			TransparentNode tn = (TransparentNode) nb;
			if (tn.element != null && tn.element instanceof ComputerCraftIoElement) {
				return (IPeripheral) tn.element;
			}
		}*/

        if (nb instanceof ComputerProbeNode) {
            IPeripheral p = (IPeripheral) nb;
            return p;
        }

        return null;
    }

    public static void register() {
        ComputerCraftAPI.registerPeripheralProvider(new PeripheralHandler());
    }
}
