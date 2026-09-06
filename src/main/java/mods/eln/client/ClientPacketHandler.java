package mods.eln.client;

import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.bus.api.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import io.netty.channel.ChannelHandler.Sharable;
import mods.eln.Eln;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.Connection;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

@Sharable
public class ClientPacketHandler {

    public ClientPacketHandler() {
        //NeoForge.EVENT_BUS.register(this);
        Eln.eventChannel.register(this);
    }

    @SubscribeEvent
    public void onClientPacket(ClientCustomPacketEvent event) {
        //Utils.println("onClientPacket");
        FMLProxyPacket packet = event.getPacket();
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(packet.payload().array()));
        Connection manager = event.getManager();
        Player player = Minecraft.getInstance().player; // EntityPlayerSP

        Eln.packetHandler.packetRx(stream, manager, player);
    }
}
