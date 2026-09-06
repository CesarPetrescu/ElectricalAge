package mods.eln.node;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import mods.eln.misc.Direction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.io.DataInputStream;

public interface INodeEntity {
    String getNodeUuid();

    void serverPublishUnserialize(DataInputStream stream);

    void serverPacketUnserialize(DataInputStream stream);

    @SideOnly(Side.CLIENT)
    Screen newGuiDraw(Direction side, Player player);

    AbstractContainerMenu newContainer(Direction side, Player player);
}
