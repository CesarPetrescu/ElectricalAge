package mods.eln.sixnode.groundcable;

import mods.eln.Eln;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.init.Cable;
import mods.eln.misc.Direction;
import mods.eln.misc.Utils;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElementInventory;
import mods.eln.node.six.SixNodeElementRender;
import mods.eln.node.six.SixNodeEntity;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.DataInputStream;
import java.io.IOException;

public class GroundCableRender extends SixNodeElementRender {

    GroundCableDescriptor descriptor;

    //double voltage = 0,current = 0;
    int color = 0;

    CableRenderDescriptor cableRender;

    SixNodeElementInventory inventory = new SixNodeElementInventory(1, 64, this);

    public GroundCableRender(SixNodeEntity tileEntity, Direction side, SixNodeDescriptor descriptor) {
        super(tileEntity, side, descriptor);
        this.descriptor = (GroundCableDescriptor) descriptor;
    }

    @Override
    public void draw() {
        super.draw();

        if (side.isY()) {
            front.glRotateOnX();
        }

        descriptor.draw();
    }

    @Override
    public void publishUnserialize(DataInputStream stream) {
        super.publishUnserialize(stream);
        try {
            Byte b;
            b = stream.readByte();

            color = (b >> 4) & 0xF;

            ItemStack cableStack = Utils.unserialiseItemStack(stream);
            ElectricalCableDescriptor desc = (ElectricalCableDescriptor) ElectricalCableDescriptor.getDescriptor(cableStack, ElectricalCableDescriptor.class);

            if (desc == null)
                cableRender = Cable.Companion.getLowVoltage().descriptor.render;
            else
                cableRender = desc.render;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public CableRenderDescriptor getCableRender(mods.eln.misc.LRDU lrdu) {
        return cableRender;
    }

    @Override
    public Screen newGuiDraw(Direction side, Player player) {
        return new GroundCableGui(player, inventory, this);
    }
}
