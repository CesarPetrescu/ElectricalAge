package mods.eln.sixnode.powerinductorsix;

import mods.eln.cable.CableRenderType;
import mods.eln.misc.Direction;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElementInventory;
import mods.eln.node.six.SixNodeElementRender;
import mods.eln.node.six.SixNodeEntity;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;

public class PowerInductorSixRender extends SixNodeElementRender {

    public PowerInductorSixDescriptor descriptor;
    private CableRenderType renderPreProcess;

    SixNodeElementInventory inventory = new SixNodeElementInventory(2, 64, this);

    public PowerInductorSixRender(SixNodeEntity tileEntity, Direction side, SixNodeDescriptor descriptor) {
        super(tileEntity, side, descriptor);
        this.descriptor = (PowerInductorSixDescriptor) descriptor;
    }

    @Override
    public void draw() {
        front.left().glRotateOnX();
        descriptor.draw();
    }

    @Override
    public Screen newGuiDraw(Direction side, Player player) {
        return new PowerInductorSixGui(player, inventory, this);
    }
}
