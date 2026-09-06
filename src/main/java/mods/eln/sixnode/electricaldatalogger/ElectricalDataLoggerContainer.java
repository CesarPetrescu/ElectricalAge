package mods.eln.sixnode.electricaldatalogger;

import mods.eln.generic.GenericItemUsingDamageSlot;
import mods.eln.gui.ISlotSkin.SlotSkin;
import mods.eln.gui.ItemStackFilter;
import mods.eln.gui.SlotFilter;
import mods.eln.misc.BasicContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

public class ElectricalDataLoggerContainer extends BasicContainer {

    public static final int paperSlotId = 0;
    public static final int printSlotId = 1;

    public ElectricalDataLoggerContainer(Player player, Container inventory) {
        super(player, inventory, new Slot[]{
            new SlotFilter(inventory, paperSlotId, 176 / 2 - 44, 184, 64, new ItemStackFilter[]{new ItemStackFilter(Items.PAPER)}, SlotSkin.medium, new String[]{"Paper Slot"}),
            new GenericItemUsingDamageSlot(inventory, printSlotId, 176 / 2 + 45 - 17, 184, 1, DataLogsPrintDescriptor.class, SlotSkin.medium, new String[]{})
        });
    }
}
