package mods.eln.sixnode.resistor;

import mods.eln.gui.ISlotSkin;
import mods.eln.gui.ItemStackFilter;
import mods.eln.gui.SlotFilter;
import mods.eln.misc.BasicContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import static mods.eln.i18n.I18N.tr;

/**
 * Created by svein on 05/08/15.
 */
public class ResistorContainer extends BasicContainer {
    public static final int coreId = 0;

    public ResistorContainer(Player player, Container inventory) {
        super(player, inventory, new Slot[]{
            new SlotFilter(inventory, coreId, 132, 8, 64, ItemStackFilter.OreDict("dustCoal"),
                ISlotSkin.SlotSkin.medium,
                new String[]{tr("Coal dust slot"), tr("(Sets resistance)")})
        });
    }
}
