package mods.eln.sixnode.electricalentitysensor;

import mods.eln.gui.GuiContainerEln;
import mods.eln.gui.GuiHelperContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;

public class ElectricalEntitySensorGui extends GuiContainerEln {

    ElectricalEntitySensorRender render;

    public ElectricalEntitySensorGui(Player player, Container inventory, ElectricalEntitySensorRender render) {
        super(new ElectricalEntitySensorContainer(player, inventory));
        this.render = render;
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    protected GuiHelperContainer newHelper() {
        return new GuiHelperContainer(this, 176, 166 - 52, 8, 84 - 52);
    }
}
