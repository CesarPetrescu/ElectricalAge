package mods.eln.sixnode.electricalbreaker;

import mods.eln.misc.BasicContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;

public class ElectricalBreakerContainer extends BasicContainer {

    public ElectricalBreakerContainer(Player player, Container inventory) {
        super(player, inventory, new net.minecraft.world.inventory.Slot[]{});
    }
}
