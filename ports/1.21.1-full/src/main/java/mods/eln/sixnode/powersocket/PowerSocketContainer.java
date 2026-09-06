package mods.eln.sixnode.powersocket;

import mods.eln.misc.BasicContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

public class PowerSocketContainer extends BasicContainer {

    //TODO TBD?>
    public static final int cableSlotId = 0;

    public PowerSocketContainer(Player player, Container inventory) {
        super(player, inventory, new Slot[]{});
    }
}
