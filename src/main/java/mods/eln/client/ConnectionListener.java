package mods.eln.client;

import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import mods.eln.Eln;
import mods.eln.misc.Utils;
import mods.eln.ore.OreScannerManager;
import mods.eln.misc.UtilsClient;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ConnectionListener {

    public ConnectionListener() {
        NeoForge.EVENT_BUS.register(this);
    }

    static boolean newConnection = false;
    static int timer = 0;

    @SubscribeEvent
    public void onConnectedToServerEvent(ClientPlayerNetworkEvent.LoggingIn event) {
        Utils.println("Connected to server " + Utils.INSTANCE.getSide());
        OreScannerManager.regenOreScannerFactors();

        timer = 20;
        newConnection = true;
    }

    @SubscribeEvent
    public void onDisconnectedFromServerEvent(ClientPlayerNetworkEvent.LoggingOut event) {
        Utils.println("Disconnected from server " + Utils.INSTANCE.getSide());
        Minecraft.getInstance().execute(UtilsClient::glDeleteListsAllSafe);
    }

    @SubscribeEvent
    public void tick(ClientTickEvent.Post event) {
        if (newConnection) {
            if (timer-- != 0) return;

            newConnection = false;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
            DataOutputStream stream = new DataOutputStream(bos);

            try {
                stream.writeByte(Eln.packetClientToServerConnection);
            } catch (IOException e) {

                e.printStackTrace();
            }

            UtilsClient.sendPacketToServer(bos);
        }
    }
}
