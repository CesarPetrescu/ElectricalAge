package mods.eln.sixnode.electricalmath;

import mods.eln.gui.ISlotSkin.SlotSkin;
import mods.eln.gui.ItemStackFilter;
import mods.eln.gui.SlotFilter;
import mods.eln.misc.BasicContainer;
import mods.eln.node.NodeBase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import static mods.eln.i18n.I18N.tr;

public class ElectricalMathContainer extends BasicContainer {

    NodeBase node = null;
    public static final int restoneSlotId = 0;

    public ElectricalMathContainer(NodeBase node, Player player, Container inventory) {
        super(player, inventory, new Slot[]{
            new SlotFilter(inventory, restoneSlotId, 125 + 27 + 44 / 2, 25, 64,
                new ItemStackFilter[]{new ItemStackFilter(Items.REDSTONE)}, SlotSkin.medium, new String[]{tr("Redstone slot")})
        });
        this.node = node;
    }
}
