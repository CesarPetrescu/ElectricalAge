package mods.eln.sixnode.groundcable;

import mods.eln.gui.ISlotSkin.SlotSkin;
import mods.eln.misc.BasicContainer;
import mods.eln.node.six.SixNodeItemSlot;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import static mods.eln.i18n.I18N.tr;

public class GroundCableContainer extends BasicContainer {

    public static final int cableSlotId = 0;

    public GroundCableContainer(Player player, Container inventory) {
        super(player, inventory, new Slot[]{
            new SixNodeItemSlot(inventory, cableSlotId, 176 / 2 - 8, 8, 1,
                new Class[]{ElectricalCableDescriptor.class}, SlotSkin.medium,
                new String[]{tr("Electrical cable slot")})
        });
    }
}
