package mods.eln.sixnode.electricalsensor;

import mods.eln.gui.ISlotSkin.SlotSkin;
import mods.eln.misc.BasicContainer;
import mods.eln.node.six.SixNodeItemSlot;
import mods.eln.sixnode.currentcable.CurrentCableDescriptor;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import static mods.eln.i18n.I18N.tr;

public class ElectricalSensorContainer extends BasicContainer {

    public static final int cableSlotId = 0;

    public ElectricalSensorContainer(Player player, Container inventory, ElectricalSensorDescriptor d) {
        super(player, inventory, new Slot[]{
            new SixNodeItemSlot(inventory, cableSlotId, 152, d.voltageOnly ? 14 : 62, 1,
                new Class[]{ElectricalCableDescriptor.class, CurrentCableDescriptor.class}, SlotSkin.medium,
                new String[]{tr("Electrical cable slot")})
        });
    }
}
